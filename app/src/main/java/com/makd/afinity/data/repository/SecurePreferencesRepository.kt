package com.makd.afinity.data.repository

import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class ServerUserToken(
    val serverId: String,
    val userId: UUID,
    val accessToken: String,
    val username: String,
    val serverUrl: String
)

interface SecurePreferencesRepository {

    suspend fun saveAuthenticationData(
        accessToken: String, userId: UUID, serverId: String, serverUrl: String, username: String
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
        serverId: String, userId: UUID, accessToken: String, username: String, serverUrl: String
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
        username: String
    )

    suspend fun switchJellyseerrContext(jellyfinServerId: String, jellyfinUserId: UUID): Boolean
    fun clearActiveJellyseerrCache()
    suspend fun getJellyseerrAuthForUser(
        jellyfinServerId: String, jellyfinUserId: UUID
    ): Triple<String?, String?, String?>

    suspend fun clearJellyseerrAuthForUser(jellyfinServerId: String, jellyfinUserId: UUID)
    suspend fun getJellyseerrServerUrl(): String?
    suspend fun getJellyseerrCookie(): String?
    suspend fun getJellyseerrUsername(): String?
    suspend fun hasValidJellyseerrAuth(): Boolean

    suspend fun saveJellyseerrServerUrl(url: String)

    suspend fun saveAuthCookies(serverUrl: String, cookies: String)
    suspend fun getAuthCookiesForHost(host: String): String?
    fun getCachedAuthCookiesForHost(host: String): String?
    fun getCachedJellyseerrServerUrl(): String?
    fun getCachedJellyseerrCookie(): String?

    suspend fun saveJellyseerrCookie(cookie: String)
    suspend fun saveJellyseerrUsername(username: String)
    suspend fun clearJellyseerrAuthData()
}