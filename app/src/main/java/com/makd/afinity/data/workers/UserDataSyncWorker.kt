package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.api.operations.ItemsApi
import timber.log.Timber
import java.util.UUID

@HiltWorker
class UserDataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: ApiClient,
    private val databaseRepository: DatabaseRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting user data sync")

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user, cannot sync")
                return@withContext Result.failure(workDataOf("error" to "User not authenticated"))
            } ?: return@withContext Result.failure(workDataOf("error" to "User ID is null"))

            val unsyncedData = databaseRepository.getAllUserDataToSync(userId)

            if (unsyncedData.isEmpty()) {
                Timber.d("No unsynced user data to sync")
                return@withContext Result.success()
            }

            Timber.i("Found ${unsyncedData.size} user data items to sync")

            val itemsApi = ItemsApi(apiClient)
            var successCount = 0
            var failureCount = 0

            unsyncedData.forEach { userData ->
                try {
                    Timber.d("Syncing user data for item ${userData.itemId}: position=${userData.playbackPositionTicks}, played=${userData.played}")

                    itemsApi.updateItemUserData(
                        itemId = userData.itemId,
                        userId = userId,
                        data = UpdateUserItemDataDto(
                            playbackPositionTicks = userData.playbackPositionTicks,
                            played = userData.played,
                            isFavorite = userData.favorite,
                        )
                    )

                    databaseRepository.markUserDataSynced(userId, userData.itemId)
                    successCount++
                    Timber.i("✓ Successfully synced user data for item ${userData.itemId}")

                } catch (e: Exception) {
                    failureCount++
                    Timber.w(e, "Failed to sync user data for item ${userData.itemId}")
                }
            }

            Timber.i("User data sync completed: $successCount succeeded, $failureCount failed out of ${unsyncedData.size} total")

            return@withContext if (successCount > 0 || failureCount == 0) {
                Result.success(workDataOf(
                    "synced_count" to successCount,
                    "failed_count" to failureCount
                ))
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "User data sync failed")
            return@withContext Result.retry()
        }
    }
}