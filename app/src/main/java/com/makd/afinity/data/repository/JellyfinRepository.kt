package com.makd.afinity.data.repository

import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.ui.settings.servers.JellyfinStats
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import java.util.UUID

interface JellyfinRepository {

    fun getBaseUrl(): String

    suspend fun setBaseUrl(baseUrl: String)

    suspend fun discoverServersFlow(): Flow<List<Server>>

    suspend fun validateServer(serverUrl: String): JellyfinServerRepository.ServerConnectionResult

    suspend fun refreshServerInfo()

    suspend fun logout()

    suspend fun getCurrentUser(): User?

    suspend fun getPublicUsers(serverUrl: String): List<User>

    suspend fun getUserProfileImageUrl(): String?

    fun getLibraryStatsFlow(serverId: String): Flow<JellyfinStats>

    suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long = 0,
        sessionId: String? = null,
    )

    suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean = false,
        sessionId: String? = null,
    )

    suspend fun reportPlaybackStopped(itemId: UUID, positionTicks: Long, sessionId: String? = null)

    suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        videoStreamIndex: Int? = null,
    ): String

    suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int = 0,
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null,
    ): String

    suspend fun getActiveSessions(): Result<List<SessionInfoDto>>

    suspend fun restartServer(): Result<Unit>

    suspend fun shutdownServer(): Result<Unit>

    suspend fun refreshAllLibraries(): Result<Unit>

    suspend fun getScheduledTasks(): Result<List<TaskInfo>>

    suspend fun startScheduledTask(taskId: String): Result<Unit>

    suspend fun stopScheduledTask(taskId: String): Result<Unit>
}
