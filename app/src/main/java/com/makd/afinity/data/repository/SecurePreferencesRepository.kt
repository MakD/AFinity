package com.makd.afinity.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class ServerUserToken(
    val serverId: String,
    val userId: UUID,
    val accessToken: String,
    val username: String,
    val serverUrl: String,
)

interface SecurePreferencesRepository {

    suspend fun saveAuthenticationData(
        accessToken: String,
        userId: UUID,
        serverId: String,
        serverUrl: String,
        username: String,
    )

    suspend fun getAccessToken(): String?

    suspend fun getSavedUserId(): String?

    suspend fun getSavedServerId(): String?

    suspend fun getSavedServerUrl(): String?

    suspend fun getSavedUsername(): String?

    suspend fun clearAuthenticationData()

    suspend fun hasValidAuthData(): Boolean

    fun getAuthenticationStateFlow(): Flow<Boolean>

    suspend fun saveServerUserToken(
        serverId: String,
        userId: UUID,
        accessToken: String,
        username: String,
        serverUrl: String,
    )

    suspend fun getServerUserToken(serverId: String, userId: UUID): String?

    suspend fun getLastUserIdForServer(serverId: String): UUID?

    suspend fun getAllServerUserTokens(): List<ServerUserToken>

    suspend fun clearServerUserToken(serverId: String, userId: UUID)

    suspend fun clearAllServerTokens(serverId: String)

    suspend fun saveDeviceId(deviceId: String)

    suspend fun getDeviceId(): String?

    suspend fun saveApiKey(key: String)

    suspend fun getApiKey(): String?

    suspend fun clearAllSecureData()

    suspend fun saveJellyseerrAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
        url: String,
        cookie: String,
        username: String,
    )

    suspend fun switchJellyseerrContext(jellyfinServerId: String, jellyfinUserId: UUID): Boolean

    fun clearActiveJellyseerrCache()

    suspend fun getJellyseerrAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): Triple<String?, String?, String?>

    suspend fun clearJellyseerrAuthForUser(jellyfinServerId: String, jellyfinUserId: UUID)

    suspend fun getJellyseerrServerUrl(): String?

    suspend fun getJellyseerrCookie(): String?

    suspend fun getJellyseerrUsername(): String?

    suspend fun hasValidJellyseerrAuth(): Boolean

    suspend fun saveJellyseerrServerUrl(url: String)

    fun getCachedJellyseerrServerUrl(): String?

    fun getCachedJellyseerrCookie(): String?

    suspend fun saveJellyseerrCookie(cookie: String)

    suspend fun saveJellyseerrUsername(username: String)

    suspend fun clearJellyseerrAuthData()

    suspend fun saveAudiobookshelfAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
        serverUrl: String,
        accessToken: String,
        absUserId: String,
        username: String,
    )

    suspend fun switchAudiobookshelfContext(jellyfinServerId: String, jellyfinUserId: UUID): Boolean

    fun clearActiveAudiobookshelfCache()

    suspend fun getAudiobookshelfAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): AudiobookshelfAuthData?

    suspend fun clearAudiobookshelfAuthForUser(jellyfinServerId: String, jellyfinUserId: UUID)

    fun getCachedAudiobookshelfServerUrl(): String?

    fun getCachedAudiobookshelfToken(): String?

    suspend fun hasValidAudiobookshelfAuth(): Boolean
}

data class AudiobookshelfAuthData(
    val serverUrl: String,
    val accessToken: String,
    val absUserId: String,
    val username: String,
)
