package com.makd.afinity.data.repository

import kotlinx.coroutines.flow.Flow

interface SecurePreferencesRepository {

    suspend fun saveAuthenticationData(
        accessToken: String,
        userId: java.util.UUID,
        serverId: String,
        serverUrl: String,
        username: String
    )

    suspend fun getAccessToken(): String?
    suspend fun getSavedUserId(): String?
    suspend fun getSavedServerId(): String?
    suspend fun getSavedServerUrl(): String?
    suspend fun getSavedUsername(): String?
    suspend fun clearAuthenticationData()
    suspend fun hasValidAuthData(): Boolean
    fun getAuthenticationStateFlow(): Flow<Boolean>

    suspend fun saveDeviceId(deviceId: String)
    suspend fun getDeviceId(): String?

    suspend fun saveApiKey(key: String)
    suspend fun getApiKey(): String?

    suspend fun clearAllSecureData()
}