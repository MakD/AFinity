package com.makd.afinity.data.repository

import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import kotlinx.coroutines.flow.Flow
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

}
