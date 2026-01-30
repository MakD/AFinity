package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.DatabaseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber

@HiltWorker
class UserDataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting user data sync")

            val servers = databaseRepository.getAllServers()

            if (servers.isEmpty()) {
                Timber.d("No servers found, skipping sync")
                return@withContext Result.success()
            }

            var totalSuccess = 0
            var totalFailure = 0

            servers.forEach { server ->
                try {
                    val apiClient = sessionManager.getOrRestoreApiClient(server.id)

                    if (apiClient == null) {
                        Timber.d("Skipping sync for server ${server.name}: No valid session found")
                        return@forEach
                    }

                    val userId = try {
                        apiClient.userApi.getCurrentUser().content?.id
                    } catch (e: Exception) {
                        Timber.w("Could not validate user token for server ${server.name}: ${e.message}")
                        null
                    }

                    if (userId == null) {
                        return@forEach
                    }

                    val unsyncedData = databaseRepository.getAllUserDataToSync(userId)

                    if (unsyncedData.isNotEmpty()) {
                        Timber.i("Found ${unsyncedData.size} items to sync for user $userId on server ${server.name}")

                        val itemsApi = ItemsApi(apiClient)

                        unsyncedData.forEach { userData ->
                            try {
                                Timber.d("Syncing item ${userData.itemId} -> Server: ${server.name}")

                                itemsApi.updateItemUserData(
                                    itemId = userData.itemId,
                                    userId = userId,
                                    data = UpdateUserItemDataDto(
                                        playbackPositionTicks = userData.playbackPositionTicks,
                                        played = userData.played,
                                        isFavorite = userData.favorite
                                    )
                                )

                                databaseRepository.markUserDataSynced(userId, userData.itemId)
                                totalSuccess++
                            } catch (e: Exception) {
                                totalFailure++
                                Timber.w(e, "Failed to sync item ${userData.itemId} on server ${server.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing sync for server ${server.name}")
                }
            }

            Timber.i("Global user data sync completed. Success: $totalSuccess, Failures: $totalFailure")

            return@withContext if (totalSuccess > 0 || totalFailure == 0) {
                Result.success(
                    workDataOf(
                        "synced_count" to totalSuccess,
                        "failed_count" to totalFailure
                    )
                )
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "User data sync failed with critical error")
            return@withContext Result.retry()
        }
    }
}