package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@HiltWorker
class UserDataSyncWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Starting user data sync (attempt ${runAttemptCount + 1})")

                val accounts = securePreferencesRepository.getAllServerUserTokens()

                if (accounts.isEmpty()) {
                    Timber.d("No accounts found, skipping sync")
                    return@withContext Result.success()
                }

                var totalSuccess = 0
                var totalFailure = 0
                var retryNeeded = false

                accounts
                    .distinctBy { it.serverId to it.userId }
                    .forEach { account ->
                        val pending =
                            try {
                                databaseRepository.getAllUserDataToSync(
                                    account.userId,
                                    account.serverId,
                                )
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Could not read pending user data for ${account.serverId}",
                                )
                                retryNeeded = true
                                return@forEach
                            }

                        if (pending.isEmpty()) return@forEach

                        Timber.i(
                            "Found ${pending.size} items to sync for user ${account.userId} on server ${account.serverId}"
                        )

                        val apiClient =
                            try {
                                sessionManager.getDetachedApiClient(
                                    account.serverId,
                                    account.userId,
                                )
                            } catch (e: Exception) {
                                Timber.w(e, "Could not build client for server ${account.serverId}")
                                null
                            }

                        if (apiClient == null) {
                            Timber.w(
                                "No client for server ${account.serverId} with ${pending.size} pending items, will retry"
                            )
                            retryNeeded = true
                            return@forEach
                        }

                        val itemsApi = ItemsApi(apiClient)
                        val playStateApi = PlayStateApi(apiClient)

                        for (userData in pending) {
                            when (
                                uploadUserData(
                                    itemsApi = itemsApi,
                                    playStateApi = playStateApi,
                                    userId = account.userId,
                                    userData = userData,
                                )
                            ) {
                                UploadResult.SYNCED -> {
                                    databaseRepository.markUserDataSynced(
                                        account.userId,
                                        userData.itemId,
                                        account.serverId,
                                    )
                                    totalSuccess++
                                }
                                UploadResult.DISCARD -> {
                                    databaseRepository.markUserDataSynced(
                                        account.userId,
                                        userData.itemId,
                                        account.serverId,
                                    )
                                    totalFailure++
                                }
                                UploadResult.UNAUTHORIZED -> {
                                    totalFailure++
                                    Timber.w(
                                        "Token rejected for server ${account.serverId}, aborting its sync"
                                    )
                                    break
                                }
                                UploadResult.RETRY -> {
                                    totalFailure++
                                    retryNeeded = true
                                    break
                                }
                            }
                        }
                    }

                Timber.i(
                    "Global user data sync completed. Success: $totalSuccess, Failures: $totalFailure, retryNeeded: $retryNeeded"
                )

                return@withContext when {
                    !retryNeeded ->
                        Result.success(
                            workDataOf(
                                "synced_count" to totalSuccess,
                                "failed_count" to totalFailure,
                            )
                        )
                    runAttemptCount >= MAX_RUN_ATTEMPTS -> {
                        Timber.w(
                            "Giving up user data sync after $runAttemptCount attempts, rows stay queued"
                        )
                        Result.success(
                            workDataOf(
                                "synced_count" to totalSuccess,
                                "failed_count" to totalFailure,
                            )
                        )
                    }
                    else -> Result.retry()
                }
            } catch (e: Exception) {
                Timber.e(e, "User data sync failed with critical error")
                return@withContext if (runAttemptCount >= MAX_RUN_ATTEMPTS) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }

    private suspend fun uploadUserData(
        itemsApi: ItemsApi,
        playStateApi: PlayStateApi,
        userId: UUID,
        userData: AfinityUserDataDto,
    ): UploadResult {
        return try {
            val datePlayed = userData.lastPlayedAt?.toDateTime()

            if (userData.played) {
                playStateApi.markPlayedItem(
                    itemId = userData.itemId,
                    userId = userId,
                    datePlayed = datePlayed,
                )
            } else {
                itemsApi.updateItemUserData(
                    itemId = userData.itemId,
                    userId = userId,
                    data =
                        UpdateUserItemDataDto(
                            playbackPositionTicks = userData.playbackPositionTicks,
                            lastPlayedDate = datePlayed,
                        ),
                )
            }
            Timber.d("Synced item ${userData.itemId} (played=${userData.played})")
            UploadResult.SYNCED
        } catch (e: InvalidStatusException) {
            when (e.status) {
                401,
                403 -> UploadResult.UNAUTHORIZED
                400,
                404 -> {
                    Timber.w(
                        "Server rejected item ${userData.itemId} with ${e.status}, dropping pending row"
                    )
                    UploadResult.DISCARD
                }
                else -> {
                    Timber.w(e, "Failed to sync item ${userData.itemId}")
                    UploadResult.RETRY
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync item ${userData.itemId}")
            UploadResult.RETRY
        }
    }

    private fun Long.toDateTime(): DateTime =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private enum class UploadResult {
        SYNCED,
        DISCARD,
        UNAUTHORIZED,
        RETRY,
    }

    private companion object {
        const val MAX_RUN_ATTEMPTS = 6
    }
}
