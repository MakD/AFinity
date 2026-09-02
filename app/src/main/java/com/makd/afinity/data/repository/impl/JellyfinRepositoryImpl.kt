package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.database.dao.JellyfinStatsDao
import com.makd.afinity.data.database.dao.ServerStorageDao
import com.makd.afinity.data.database.entities.JellyfinStatsCacheEntity
import com.makd.afinity.data.database.entities.ServerStorageCacheEntity
import com.makd.afinity.data.database.entities.toJellyfinStats
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerStorage
import com.makd.afinity.data.models.server.StorageDevice
import com.makd.afinity.data.models.server.StorageFolder
import com.makd.afinity.data.models.server.StorageFolderKind
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
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.ScheduledTaskApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.model.api.FolderStorageDto
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemStorageDto
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
    private val serverStorageDao: ServerStorageDao,
    private val sessionManager: SessionManager,
) : JellyfinRepository {

    private val storageJson = Json { ignoreUnknownKeys = true }

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
        emit(cached?.toJellyfinStats() ?: JellyfinStats())

        try {
            val apiClient = sessionManager.getCurrentApiClient()
            if (apiClient != null) {
                val libraryApi = LibraryApi(apiClient)
                val counts =
                    withContext(Dispatchers.IO) {
                        libraryApi
                            .getItemCounts(userId = sessionManager.currentSession.value?.userId)
                            .content
                    }

                val freshStats =
                    JellyfinStatsCacheEntity(
                        serverId = serverId,
                        movieCount = counts.movieCount,
                        seriesCount = counts.seriesCount,
                        episodeCount = counts.episodeCount,
                        boxsetCount = counts.boxSetCount,
                        albumCount = counts.albumCount,
                        songCount = counts.songCount,
                        artistCount = counts.artistCount,
                        musicVideoCount = counts.musicVideoCount,
                        bookCount = counts.bookCount,
                        trailerCount = counts.trailerCount,
                        programCount = counts.programCount,
                        itemCount = counts.itemCount,
                    )

                jellyfinStatsDao.insertStats(freshStats)
                emit(freshStats.toJellyfinStats())
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

    override suspend fun sendSessionPlaystateCommand(
        sessionId: String,
        command: PlaystateCommand,
        seekPositionTicks: Long?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                SessionApi(apiClient)
                    .sendPlaystateCommand(
                        sessionId = sessionId,
                        command = command,
                        seekPositionTicks = seekPositionTicks,
                    )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send playstate command $command to session $sessionId")
                Result.failure(e)
            }
        }

    override suspend fun sendSessionGeneralCommand(
        sessionId: String,
        command: GeneralCommandType,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                SessionApi(apiClient).sendGeneralCommand(sessionId = sessionId, command = command)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send general command $command to session $sessionId")
                Result.failure(e)
            }
        }

    override suspend fun setSessionVolume(sessionId: String, volume: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            val controllingUserId =
                sessionManager.currentSession.value?.userId
                    ?: return@withContext Result.failure(Exception("No current user"))
            return@withContext try {
                SessionApi(apiClient)
                    .sendFullGeneralCommand(
                        sessionId = sessionId,
                        data =
                            GeneralCommand(
                                name = GeneralCommandType.SET_VOLUME,
                                controllingUserId = controllingUserId,
                                arguments =
                                    mapOf("Volume" to volume.coerceIn(0, 100).toString()),
                            ),
                    )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set volume on session $sessionId")
                Result.failure(e)
            }
        }

    override suspend fun sendSessionMessage(
        sessionId: String,
        header: String,
        text: String,
        timeoutMs: Long?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(Exception("No active session"))
            return@withContext try {
                SessionApi(apiClient)
                    .sendMessageCommand(
                        sessionId = sessionId,
                        data =
                            MessageCommand(
                                header = header,
                                text = text,
                                timeoutMs = timeoutMs,
                            ),
                    )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message to session $sessionId")
                Result.failure(e)
            }
        }

    override fun getServerStorageFlow(serverId: String): Flow<ServerStorage> = flow {
        serverStorageDao
            .getStorage(serverId)
            ?.let { cached ->
                runCatching { storageJson.decodeFromString<ServerStorage>(cached.payload) }
                    .onFailure { Timber.w(it, "Discarding unreadable cached server storage") }
                    .getOrNull()
            }
            ?.takeUnless { it.isEmpty }
            ?.let { emit(it) }

        val apiClient = sessionManager.getCurrentApiClient() ?: return@flow
        try {
            val fresh =
                withContext(Dispatchers.IO) {
                    SystemApi(apiClient).getSystemStorage().content.toServerStorage()
                }
            if (fresh.isEmpty) return@flow
            serverStorageDao.insertStorage(
                ServerStorageCacheEntity(
                    serverId = serverId,
                    payload = storageJson.encodeToString(fresh),
                )
            )
            emit(fresh)
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh server storage")
        }
    }

    private fun SystemStorageDto.toServerStorage(): ServerStorage {
        val serverFolders =
            listOf(
                programDataFolder to StorageFolderKind.PROGRAM_DATA,
                internalMetadataFolder to StorageFolderKind.METADATA,
                transcodingTempFolder to StorageFolderKind.TRANSCODING_TEMP,
                cacheFolder to StorageFolderKind.CACHE,
                imageCacheFolder to StorageFolderKind.IMAGE_CACHE,
                logFolder to StorageFolderKind.LOGS,
                webFolder to StorageFolderKind.WEB,
            )

        val members =
            serverFolders.map { (dto, kind) -> DeviceMember(dto, StorageFolder(kind, dto.path)) } +
                libraries.flatMap { library ->
                    library.folders.map { dto ->
                        DeviceMember(dto, StorageFolder(null, dto.path), library.name)
                    }
                }

        val devices =
            members
                .groupBy { it.dto.deviceKey() }
                .map { (_, grouped) ->
                    val reference = grouped.first().dto
                    val folders = grouped.filter { it.library == null }.map { it.folder }
                    val libraryNames = grouped.mapNotNull { it.library }.distinct()
                    StorageDevice(
                        label = commonPathPrefix(grouped.map { it.folder.path }),
                        storageType = reference.storageType,
                        freeSpace = reference.freeSpace,
                        usedSpace = reference.usedSpace,
                        folders = folders,
                        libraries = libraryNames,
                    )
                }
                .sortedByDescending { it.usedFraction }

        return ServerStorage(devices = devices)
    }

    private class DeviceMember(
        val dto: FolderStorageDto,
        val folder: StorageFolder,
        val library: String? = null,
    )

    private fun FolderStorageDto.deviceKey(): String =
        deviceId?.takeUnless { it.isBlank() } ?: "$freeSpace:$usedSpace:$storageType"

    private fun commonPathPrefix(paths: List<String>): String {
        val separator = if (paths.any { it.contains('\\') }) "\\" else "/"
        val segments = paths.map { path -> path.split('/', '\\').filter { it.isNotEmpty() } }
        val first = segments.firstOrNull() ?: return separator

        var shared = 0
        while (
            shared < first.size &&
                segments.all { it.size > shared && it[shared].equals(first[shared], true) }
        ) {
            shared++
        }
        if (shared == 0) return separator

        val prefix = first.take(shared).joinToString(separator)
        return if (separator == "/") "/$prefix" else prefix
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
                    ScheduledTaskApi(apiClient).getTasks(isHidden = false).content ?: emptyList()
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
                ScheduledTaskApi(apiClient).startTask(taskId)
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
                ScheduledTaskApi(apiClient).stopTask(taskId)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop task $taskId")
                Result.failure(e)
            }
        }
}
