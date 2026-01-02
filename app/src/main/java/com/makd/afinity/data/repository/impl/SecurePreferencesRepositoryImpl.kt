package com.makd.afinity.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_settings")

@Singleton
class SecurePreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurePreferencesRepository {

    private companion object {
        private const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
        private const val TINK_KEYSET_NAME = "afinity_keyset"
        private const val PREF_FILE_NAME = "afinity_tink_prefs"

        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_SERVER_ID = stringPreferencesKey("server_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_IS_AUTHENTICATED = stringPreferencesKey("is_authenticated")

        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_API_KEY = stringPreferencesKey("api_key")

        val KEY_JELLYSEERR_SERVER_URL = stringPreferencesKey("jellyseerr_server_url")
        val KEY_JELLYSEERR_COOKIE = stringPreferencesKey("jellyseerr_cookie")
        val KEY_JELLYSEERR_USERNAME = stringPreferencesKey("jellyseerr_username")
    }

    private val aead: Aead by lazy {
        try {
            AeadConfig.register()

            AndroidKeysetManager.Builder()
                .withSharedPref(context, TINK_KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Tink Init failed. Clearing broken keys.")
            context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear()
                .apply()
            throw RuntimeException("Crypto Init Failed", e)
        }
    }

    private fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrBlank()) return null
        return try {
            val bytes = Base64.getDecoder().decode(cipherText)
            val decrypted = aead.decrypt(bytes, null)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.tag("CryptoAuth").e(e, "Decryption failed.")
            null
        }
    }

    @Volatile
    private var cachedJellyseerrUrl: String? = null

    @Volatile
    private var cachedJellyseerrCookie: String? = null

    private val _authenticationState = MutableStateFlow(false)

    init {
        runBlocking(Dispatchers.IO) {
            _authenticationState.value = hasValidAuthData()
        }
    }

    private fun encrypt(plainText: String): String {
        return try {
            val bytes = aead.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            ""
        }
    }


    override suspend fun saveAuthenticationData(
        accessToken: String,
        userId: UUID,
        serverId: String,
        serverUrl: String,
        username: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = encrypt(accessToken)
            prefs[KEY_USER_ID] = encrypt(userId.toString())
            prefs[KEY_SERVER_ID] = encrypt(serverId)
            prefs[KEY_SERVER_URL] = encrypt(serverUrl)
            prefs[KEY_USERNAME] = encrypt(username)
            prefs[KEY_IS_AUTHENTICATED] = encrypt("true")
        }
        _authenticationState.value = true
        Timber.d("Saved authentication data securely")
    }

    override suspend fun getAccessToken(): String? = getDecryptedString(KEY_ACCESS_TOKEN)
    override suspend fun getSavedUserId(): String? = getDecryptedString(KEY_USER_ID)
    override suspend fun getSavedServerId(): String? = getDecryptedString(KEY_SERVER_ID)
    override suspend fun getSavedServerUrl(): String? = getDecryptedString(KEY_SERVER_URL)
    override suspend fun getSavedUsername(): String? = getDecryptedString(KEY_USERNAME)

    override suspend fun clearAuthenticationData() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_SERVER_ID)
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_IS_AUTHENTICATED)
        }
        _authenticationState.value = false
        Timber.d("Cleared authentication data")
    }

    override suspend fun hasValidAuthData(): Boolean {
        val token = getAccessToken()
        val url = getSavedServerUrl()
        return !token.isNullOrBlank() && !url.isNullOrBlank()
    }

    override fun getAuthenticationStateFlow(): Flow<Boolean> = _authenticationState.asStateFlow()

    override suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { it[KEY_DEVICE_ID] = encrypt(deviceId) }
    }

    override suspend fun getDeviceId(): String? = getDecryptedString(KEY_DEVICE_ID)

    override suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = encrypt(key) }
    }

    override suspend fun getApiKey(): String? = getDecryptedString(KEY_API_KEY)

    override suspend fun clearAllSecureData() {
        context.dataStore.edit { it.clear() }
        cachedJellyseerrUrl = null
        cachedJellyseerrCookie = null
        _authenticationState.value = false
        Timber.d("Cleared all secure data")
    }


    override suspend fun saveJellyseerrServerUrl(url: String) {
        cachedJellyseerrUrl = url
        context.dataStore.edit { it[KEY_JELLYSEERR_SERVER_URL] = encrypt(url) }
    }

    override suspend fun getJellyseerrServerUrl(): String? {
        if (cachedJellyseerrUrl != null) return cachedJellyseerrUrl

        val url = getDecryptedString(KEY_JELLYSEERR_SERVER_URL)
        cachedJellyseerrUrl = url
        return url
    }

    override suspend fun saveJellyseerrCookie(cookie: String) {
        cachedJellyseerrCookie = cookie
        context.dataStore.edit { it[KEY_JELLYSEERR_COOKIE] = encrypt(cookie) }
    }

    override suspend fun getJellyseerrCookie(): String? {
        if (cachedJellyseerrCookie != null) return cachedJellyseerrCookie

        val cookie = getDecryptedString(KEY_JELLYSEERR_COOKIE)
        cachedJellyseerrCookie = cookie
        return cookie
    }

    override suspend fun saveJellyseerrUsername(username: String) {
        context.dataStore.edit { it[KEY_JELLYSEERR_USERNAME] = encrypt(username) }
    }

    override suspend fun getJellyseerrUsername(): String? =
        getDecryptedString(KEY_JELLYSEERR_USERNAME)

    override suspend fun clearJellyseerrAuthData() {
        cachedJellyseerrUrl = null
        cachedJellyseerrCookie = null
        context.dataStore.edit {
            it.remove(KEY_JELLYSEERR_SERVER_URL)
            it.remove(KEY_JELLYSEERR_COOKIE)
            it.remove(KEY_JELLYSEERR_USERNAME)
        }
    }

    override suspend fun hasValidJellyseerrAuth(): Boolean {
        if (cachedJellyseerrCookie != null && cachedJellyseerrUrl != null) return true

        val cookie = getJellyseerrCookie()
        val url = getJellyseerrServerUrl()
        return !cookie.isNullOrBlank() && !url.isNullOrBlank()
    }

    override fun getCachedJellyseerrServerUrl(): String? = cachedJellyseerrUrl

    override fun getCachedJellyseerrCookie(): String? = cachedJellyseerrCookie

    private suspend fun getDecryptedString(key: Preferences.Key<String>): String? {
        return withContext(Dispatchers.IO) {
            val preferences = context.dataStore.data.first()
            val encryptedValue = preferences[key]
            decrypt(encryptedValue)
        }
    }
}