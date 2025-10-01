package com.makd.afinity.data.repository.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurePreferencesRepository {

    private companion object {
        private const val SECURE_PREFS_FILE_NAME = "afinity_secure_prefs"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_API_KEY = "api_key"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create encrypted preferences, falling back to regular SharedPreferences")
            context.getSharedPreferences(SECURE_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private val _authenticationState = MutableStateFlow(false)

    init {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            _authenticationState.value = hasValidAuthData()
        }
    }

    override suspend fun saveAuthenticationData(
        accessToken: String,
        userId: java.util.UUID,
        serverId: String,
        serverUrl: String,
        username: String
    ) = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_USER_ID, userId.toString())
                putString(KEY_SERVER_ID, serverId)
                putString(KEY_SERVER_URL, serverUrl)
                putString(KEY_USERNAME, username)
                putBoolean(KEY_IS_AUTHENTICATED, true)
            }.apply()

            _authenticationState.value = true
            Timber.d("Saved authentication data securely for user: $username")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save authentication data")
            throw e
        }
    }

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve access token")
            null
        }
    }

    override suspend fun getSavedUserId(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_USER_ID, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve user ID")
            null
        }
    }

    override suspend fun getSavedServerId(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_SERVER_ID, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve server ID")
            null
        }
    }

    override suspend fun getSavedServerUrl(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_SERVER_URL, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve server URL")
            null
        }
    }

    override suspend fun getSavedUsername(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_USERNAME, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve username")
            null
        }
    }

    override suspend fun clearAuthenticationData() = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().apply {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_USER_ID)
                remove(KEY_SERVER_ID)
                remove(KEY_SERVER_URL)
                remove(KEY_USERNAME)
                putBoolean(KEY_IS_AUTHENTICATED, false)
            }.apply()

            _authenticationState.value = false
            Timber.d("Cleared authentication data")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear authentication data")
            throw e
        }
    }

    override suspend fun hasValidAuthData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val isAuthenticated = encryptedPrefs.getBoolean(KEY_IS_AUTHENTICATED, false)
            val hasToken = !encryptedPrefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
            val hasUserId = !encryptedPrefs.getString(KEY_USER_ID, null).isNullOrBlank()
            val hasServerUrl = !encryptedPrefs.getString(KEY_SERVER_URL, null).isNullOrBlank()

            isAuthenticated && hasToken && hasUserId && hasServerUrl
        } catch (e: Exception) {
            Timber.e(e, "Failed to check auth data validity")
            false
        }
    }

    override fun getAuthenticationStateFlow(): Flow<Boolean> = _authenticationState.asStateFlow()

    override suspend fun saveDeviceId(deviceId: String) = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Timber.d("Saved device ID securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save device ID")
            throw e
        }
    }

    override suspend fun getDeviceId(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_DEVICE_ID, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve device ID")
            null
        }
    }

    override suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()
            Timber.d("Saved API key securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save API key")
            throw e
        }
    }

    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve API key")
            null
        }
    }

    override suspend fun clearAllSecureData() = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().clear().apply()
            _authenticationState.value = false
            Timber.d("Cleared all secure data")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all secure data")
            throw e
        }
    }
}