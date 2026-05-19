package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.database.dao.JellyfinStatsDao
import com.makd.afinity.data.database.entities.JellyfinStatsCacheEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.ui.settings.servers.JellyfinStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.ScheduledTasksApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepositoryImpl
@Inject
constructor(
    private val serverRepository: ServerRepository,
    private val authRepository: AuthRepository,
    private val playbackRepository: PlaybackRepository,
    private val jellyfinStatsDao: JellyfinStatsDao,
    private val sessionManager: SessionManager,
) : JellyfinRepository {

    override fun getBaseUrl(): String {
        return serverRepository.getBaseUrl()
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        serverRepository.setBaseUrl(baseUrl)
    }

    override suspend fun discoverServersFlow(): Flow<List<Server>> {
        return try {
            serverRepository.discoverServersFlow()
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers flow")
            flowOf(emptyList())
        }
    }

    override suspend fun validateServer(
        serverUrl: String
    ): JellyfinServerRepository.ServerConnectionResult {
        return try {
            serverRepository.testServerConnection(serverUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate server: $serverUrl")
            JellyfinServerRepository.ServerConnectionResult.Error(
                "Failed to validate server: ${e.message ?: "Unknown error"}"
            )
        }
    }

    override suspend fun refreshServerInfo() {
        serverRepository.refreshServerInfo()
    }

    override suspend fun logout() {
        try {
            authRepository.logout()
        } catch (e: Exception) {
            Timber.e(e, "Failed to logout")
        }
    }

    override suspend fun getCurrentUser(): User? {
        return try {
            authRepository.getCurrentUser()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            null
        }
    }

    override suspend fun getPublicUsers(serverUrl: String): List<User> {
        return try {
            authRepository.getPublicUsers(serverUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get public users")
            emptyList()
        }
    }

    override suspend fun getUserProfileImageUrl(): String? {
        return try {
            val currentUser = authRepository.currentUser.value
            val serverUrl = getBaseUrl()

            currentUser?.primaryImageTag?.let { imageTag ->
                "$serverUrl/Users/${currentUser.id}/Images/Primary?tag=$imageTag"
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
        }
    }

    override fun getLibraryStatsFlow(serverId: String): Flow<JellyfinStats> = flow {
        val cached = jellyfinStatsDao.getStatsFlow(serverId).firstOrNull()
        if (cached != null) {
            emit(
                JellyfinStats(
                    movieCount = cached.movieCount,
                    seriesCount = cached.seriesCount,
                    episodeCount = cached.episodeCount,
                    boxsetCount = cached.boxsetCount,
                )
            )
        } else {
            emit(JellyfinStats())
        }

        try {
            val apiClient = sessionManager.getCurrentApiClient()
            if (apiClient != null) {
                val libraryApi = LibraryApi(apiClient)
                val counts = withContext(Dispatchers.IO) { libraryApi.getItemCounts().content }

                val freshStats =
                    JellyfinStatsCacheEntity(
                        serverId = serverId,
                        movieCount = counts.movieCount ?: 0,
                        seriesCount = counts.seriesCount ?: 0,
                        episodeCount = counts.episodeCount ?: 0,
                        boxsetCount = counts.boxSetCount ?: 0,
                    )

                jellyfinStatsDao.insertStats(freshStats)
                emit(
                    JellyfinStats(
                        movieCount = freshStats.movieCount,
                        seriesCount = freshStats.seriesCount,
                        episodeCount = freshStats.episodeCount,
                        boxsetCount = freshStats.boxsetCount,
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh remote Jellyfin stats")
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStart(
                itemId = itemId,
                sessionId = actualSessionId,
                mediaSourceId = itemId.toString(),
                playMethod = "DirectPlay",
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start: $itemId")
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackProgress(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                playMethod = "DirectPlay",
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback progress: $itemId")
        }
    }

    override suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStop(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                mediaSourceId = itemId.toString(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stopped: $itemId")
        }
    }

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?,
    ): String {
        return try {
            serverRepository.buildStreamUrl(
                itemId.toString(),
                mediaSourceId,
                maxBitrate,
                audioStreamIndex,
                subtitleStreamIndex,
                videoStreamIndex,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get stream URL for item: $itemId")
            ""
        }
    }

    override suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int,
        tag: String?,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?,
    ): String {
        return try {
            serverRepository.buildImageUrl(
                itemId = itemId.toString(),
                imageType = imageType,
                imageIndex = imageIndex,
                tag = tag,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                quality = quality,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get image URL for item: $itemId")
            ""
        }
    }

    override suspend fun getActiveSessions(): Result<List<SessionInfoDto>> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                val sessions = SessionApi(apiClient).getSessions().content ?: emptyList()
                Result.success(sessions)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get active sessions")
                Result.failure(e)
            }
        }

    override suspend fun restartServer(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                SystemApi(apiClient).restartApplication()
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to restart server")
                Result.failure(e)
            }
        }

    override suspend fun shutdownServer(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                SystemApi(apiClient).shutdownApplication()
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to shutdown server")
                Result.failure(e)
            }
        }

    override suspend fun refreshAllLibraries(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                LibraryApi(apiClient).refreshLibrary()
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh libraries")
                Result.failure(e)
            }
        }

    override suspend fun getScheduledTasks(): Result<List<TaskInfo>> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                val tasks =
                    ScheduledTasksApi(apiClient).getTasks(isHidden = false).content ?: emptyList()
                Result.success(tasks)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get scheduled tasks")
                Result.failure(e)
            }
        }

    override suspend fun startScheduledTask(taskId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                ScheduledTasksApi(apiClient).startTask(taskId)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start task $taskId")
                Result.failure(e)
            }
        }

    override suspend fun stopScheduledTask(taskId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                ScheduledTasksApi(apiClient).stopTask(taskId)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop task $taskId")
                Result.failure(e)
            }
        }
}
