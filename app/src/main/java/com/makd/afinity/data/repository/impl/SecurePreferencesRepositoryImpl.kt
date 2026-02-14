package com.makd.afinity.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.makd.afinity.data.repository.AudiobookshelfAuthData
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.ServerUserToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "secure_settings")

@Singleton
class SecurePreferencesRepositoryImpl
@Inject
constructor(@ApplicationContext private val context: Context) : SecurePreferencesRepository {

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
                .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Tink Init failed. Clearing broken keys.")
            context
                .getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
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
            Timber.tag("CryptoAuth").d(e, "Decryption failed - likely stale or missing data")
            null
        }
    }

    @Volatile private var cachedJellyseerrUrl: String? = null

    @Volatile private var cachedJellyseerrCookie: String? = null

    @Volatile private var cachedJellyseerrUsername: String? = null

    @Volatile private var cachedAudiobookshelfUrl: String? = null

    @Volatile private var cachedAudiobookshelfToken: String? = null

    @Volatile private var cachedAudiobookshelfUserId: String? = null

    @Volatile private var cachedAudiobookshelfUsername: String? = null

    @Volatile private var cachedAudiobookshelfRefreshToken: String? = null

    @Volatile private var activeAbsServerId: String? = null
    @Volatile private var activeAbsUserId: UUID? = null

    private val persistScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authenticationState = MutableStateFlow(false)

    init {
        runBlocking(Dispatchers.IO) { _authenticationState.value = hasValidAuthData() }
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
        username: String,
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
        cachedJellyseerrUsername = null
        cachedAudiobookshelfUrl = null
        cachedAudiobookshelfToken = null
        cachedAudiobookshelfUserId = null
        cachedAudiobookshelfUsername = null
        cachedAudiobookshelfRefreshToken = null
        activeAbsServerId = null
        activeAbsUserId = null
        _authenticationState.value = false
        Timber.d("Cleared all secure data")
    }

    private fun getJellyseerrKey(
        prefix: String,
        serverId: String,
        userId: UUID,
    ): Preferences.Key<String> {
        return stringPreferencesKey("${prefix}_${serverId}_$userId")
    }

    override suspend fun saveJellyseerrAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
        url: String,
        cookie: String,
        username: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[getJellyseerrKey("jsr_url", jellyfinServerId, jellyfinUserId)] = encrypt(url)
            prefs[getJellyseerrKey("jsr_cookie", jellyfinServerId, jellyfinUserId)] =
                encrypt(cookie)
            prefs[getJellyseerrKey("jsr_user", jellyfinServerId, jellyfinUserId)] =
                encrypt(username)
        }

        cachedJellyseerrUrl = url
        cachedJellyseerrCookie = cookie
        cachedJellyseerrUsername = username

        Timber.d("Saved Jellyseerr auth for user $jellyfinUserId on server $jellyfinServerId")
    }

    override suspend fun switchJellyseerrContext(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()

            val urlKey = getJellyseerrKey("jsr_url", jellyfinServerId, jellyfinUserId)
            val cookieKey = getJellyseerrKey("jsr_cookie", jellyfinServerId, jellyfinUserId)
            val userKey = getJellyseerrKey("jsr_user", jellyfinServerId, jellyfinUserId)

            val url = decrypt(prefs[urlKey])
            val cookie = decrypt(prefs[cookieKey])
            val username = decrypt(prefs[userKey])

            cachedJellyseerrUrl = url
            cachedJellyseerrCookie = cookie
            cachedJellyseerrUsername = username

            Timber.d(
                "Switched Jellyseerr context. Valid: ${!url.isNullOrBlank() && !cookie.isNullOrBlank()}"
            )

            !url.isNullOrBlank() && !cookie.isNullOrBlank()
        }
    }

    override fun clearActiveJellyseerrCache() {
        cachedJellyseerrUrl = null
        cachedJellyseerrCookie = null
        cachedJellyseerrUsername = null
    }

    override suspend fun getJellyseerrAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): Triple<String?, String?, String?> {
        return withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val url = decrypt(prefs[getJellyseerrKey("jsr_url", jellyfinServerId, jellyfinUserId)])
            val cookie =
                decrypt(prefs[getJellyseerrKey("jsr_cookie", jellyfinServerId, jellyfinUserId)])
            val user =
                decrypt(prefs[getJellyseerrKey("jsr_user", jellyfinServerId, jellyfinUserId)])
            Triple(url, cookie, user)
        }
    }

    override suspend fun clearJellyseerrAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ) {
        context.dataStore.edit { prefs ->
            prefs.remove(getJellyseerrKey("jsr_url", jellyfinServerId, jellyfinUserId))
            prefs.remove(getJellyseerrKey("jsr_cookie", jellyfinServerId, jellyfinUserId))
            prefs.remove(getJellyseerrKey("jsr_user", jellyfinServerId, jellyfinUserId))
        }
    }

    override suspend fun saveJellyseerrServerUrl(url: String) {
        cachedJellyseerrUrl = url
        context.dataStore.edit { it[KEY_JELLYSEERR_SERVER_URL] = encrypt(url) }
    }

    override suspend fun getJellyseerrServerUrl(): String? {
        if (cachedJellyseerrUrl != null) return cachedJellyseerrUrl

        return getDecryptedString(KEY_JELLYSEERR_SERVER_URL)
    }

    override suspend fun saveJellyseerrCookie(cookie: String) {
        cachedJellyseerrCookie = cookie
        context.dataStore.edit { it[KEY_JELLYSEERR_COOKIE] = encrypt(cookie) }
    }

    override suspend fun getJellyseerrCookie(): String? {
        if (cachedJellyseerrCookie != null) return cachedJellyseerrCookie
        return getDecryptedString(KEY_JELLYSEERR_COOKIE)
    }

    override suspend fun saveJellyseerrUsername(username: String) {
        context.dataStore.edit { it[KEY_JELLYSEERR_USERNAME] = encrypt(username) }
    }

    override suspend fun getJellyseerrUsername(): String? =
        cachedJellyseerrUsername ?: getDecryptedString(KEY_JELLYSEERR_USERNAME)

    override suspend fun clearJellyseerrAuthData() {
        clearActiveJellyseerrCache()
        context.dataStore.edit {
            it.remove(KEY_JELLYSEERR_SERVER_URL)
            it.remove(KEY_JELLYSEERR_COOKIE)
            it.remove(KEY_JELLYSEERR_USERNAME)
        }
    }

    override suspend fun hasValidJellyseerrAuth(): Boolean {
        if (!cachedJellyseerrCookie.isNullOrBlank() && !cachedJellyseerrUrl.isNullOrBlank())
            return true

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

    override suspend fun saveServerUserToken(
        serverId: String,
        userId: UUID,
        accessToken: String,
        username: String,
        serverUrl: String,
    ) {
        context.dataStore.edit { prefs ->
            val tokenKey = stringPreferencesKey("token_${serverId}_$userId")
            val usernameKey = stringPreferencesKey("username_${serverId}_$userId")
            val serverUrlKey = stringPreferencesKey("serverUrl_${serverId}_$userId")
            val lastUserKey = stringPreferencesKey("lastUser_$serverId")

            prefs[tokenKey] = encrypt(accessToken)
            prefs[usernameKey] = encrypt(username)
            prefs[serverUrlKey] = encrypt(serverUrl)
            prefs[lastUserKey] = encrypt(userId.toString())
        }
        Timber.d("Saved token for server=$serverId, user=$userId")
    }

    override suspend fun getServerUserToken(serverId: String, userId: UUID): String? {
        val tokenKey = stringPreferencesKey("token_${serverId}_$userId")
        return getDecryptedString(tokenKey)
    }

    override suspend fun getLastUserIdForServer(serverId: String): UUID? {
        val lastUserKey = stringPreferencesKey("lastUser_$serverId")
        val userIdString = getDecryptedString(lastUserKey) ?: return null
        return try {
            UUID.fromString(userIdString)
        } catch (e: IllegalArgumentException) {
            Timber.w("Invalid UUID for server $serverId: $userIdString")
            null
        }
    }

    override suspend fun getAllServerUserTokens(): List<ServerUserToken> {
        return withContext(Dispatchers.IO) {
            val preferences = context.dataStore.data.first()
            val tokens = mutableListOf<ServerUserToken>()

            preferences
                .asMap()
                .keys
                .filter { it.name.startsWith("token_") }
                .forEach { key ->
                    try {
                        val parts = key.name.removePrefix("token_").split("_")
                        if (parts.size == 2) {
                            val serverId = parts[0]
                            val userId = UUID.fromString(parts[1])

                            val token = decrypt(preferences[key] as? String)
                            val username =
                                getDecryptedString(
                                    stringPreferencesKey("username_${serverId}_$userId")
                                )
                            val serverUrl =
                                getDecryptedString(
                                    stringPreferencesKey("serverUrl_${serverId}_$userId")
                                )

                            if (token != null && username != null && serverUrl != null) {
                                tokens.add(
                                    ServerUserToken(
                                        serverId = serverId,
                                        userId = userId,
                                        accessToken = token,
                                        username = username,
                                        serverUrl = serverUrl,
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse token key: ${key.name}")
                    }
                }

            tokens
        }
    }

    override suspend fun clearServerUserToken(serverId: String, userId: UUID) {
        context.dataStore.edit { prefs ->
            val tokenKey = stringPreferencesKey("token_${serverId}_$userId")
            val usernameKey = stringPreferencesKey("username_${serverId}_$userId")
            val serverUrlKey = stringPreferencesKey("serverUrl_${serverId}_$userId")

            prefs.remove(tokenKey)
            prefs.remove(usernameKey)
            prefs.remove(serverUrlKey)
        }
        Timber.d("Cleared token for server=$serverId, user=$userId")
    }

    override suspend fun clearAllServerTokens(serverId: String) {
        context.dataStore.edit { prefs ->
            val keysToRemove =
                prefs.asMap().keys.filter { key ->
                    key.name.contains("_${serverId}_") || key.name == "lastUser_$serverId"
                }

            keysToRemove.forEach { prefs.remove(it) }
        }
        Timber.d("Cleared all tokens for server=$serverId")
    }

    private fun getAudiobookshelfKey(
        prefix: String,
        serverId: String,
        userId: UUID,
    ): Preferences.Key<String> {
        return stringPreferencesKey("${prefix}_${serverId}_$userId")
    }

    override suspend fun saveAudiobookshelfAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
        serverUrl: String,
        accessToken: String,
        absUserId: String,
        username: String,
        refreshToken: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[getAudiobookshelfKey("abs_url", jellyfinServerId, jellyfinUserId)] =
                encrypt(serverUrl)
            prefs[getAudiobookshelfKey("abs_token", jellyfinServerId, jellyfinUserId)] =
                encrypt(accessToken)
            prefs[getAudiobookshelfKey("abs_userid", jellyfinServerId, jellyfinUserId)] =
                encrypt(absUserId)
            prefs[getAudiobookshelfKey("abs_user", jellyfinServerId, jellyfinUserId)] =
                encrypt(username)
            if (refreshToken != null) {
                prefs[getAudiobookshelfKey("abs_refresh_token", jellyfinServerId, jellyfinUserId)] =
                    encrypt(refreshToken)
            }
        }

        cachedAudiobookshelfUrl = serverUrl
        cachedAudiobookshelfToken = accessToken
        cachedAudiobookshelfUserId = absUserId
        cachedAudiobookshelfUsername = username
        if (refreshToken != null) {
            cachedAudiobookshelfRefreshToken = refreshToken
        }
        activeAbsServerId = jellyfinServerId
        activeAbsUserId = jellyfinUserId

        Timber.d("Saved Audiobookshelf auth for user $jellyfinUserId on server $jellyfinServerId")
    }

    override suspend fun switchAudiobookshelfContext(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()

            val urlKey = getAudiobookshelfKey("abs_url", jellyfinServerId, jellyfinUserId)
            val tokenKey = getAudiobookshelfKey("abs_token", jellyfinServerId, jellyfinUserId)
            val userIdKey = getAudiobookshelfKey("abs_userid", jellyfinServerId, jellyfinUserId)
            val userKey = getAudiobookshelfKey("abs_user", jellyfinServerId, jellyfinUserId)
            val refreshTokenKey =
                getAudiobookshelfKey("abs_refresh_token", jellyfinServerId, jellyfinUserId)

            val url = decrypt(prefs[urlKey])
            val token = decrypt(prefs[tokenKey])
            val absUserId = decrypt(prefs[userIdKey])
            val username = decrypt(prefs[userKey])
            val refreshToken = decrypt(prefs[refreshTokenKey])

            cachedAudiobookshelfUrl = url
            cachedAudiobookshelfToken = token
            cachedAudiobookshelfUserId = absUserId
            cachedAudiobookshelfUsername = username
            cachedAudiobookshelfRefreshToken = refreshToken
            activeAbsServerId = jellyfinServerId
            activeAbsUserId = jellyfinUserId

            Timber.d(
                "Switched Audiobookshelf context. Valid: ${!url.isNullOrBlank() && !token.isNullOrBlank()}"
            )

            !url.isNullOrBlank() && !token.isNullOrBlank()
        }
    }

    override fun clearActiveAudiobookshelfCache() {
        cachedAudiobookshelfUrl = null
        cachedAudiobookshelfToken = null
        cachedAudiobookshelfUserId = null
        cachedAudiobookshelfUsername = null
        cachedAudiobookshelfRefreshToken = null
        activeAbsServerId = null
        activeAbsUserId = null
    }

    override suspend fun getAudiobookshelfAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ): AudiobookshelfAuthData? {
        return withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val url =
                decrypt(prefs[getAudiobookshelfKey("abs_url", jellyfinServerId, jellyfinUserId)])
            val token =
                decrypt(prefs[getAudiobookshelfKey("abs_token", jellyfinServerId, jellyfinUserId)])
            val absUserId =
                decrypt(prefs[getAudiobookshelfKey("abs_userid", jellyfinServerId, jellyfinUserId)])
            val username =
                decrypt(prefs[getAudiobookshelfKey("abs_user", jellyfinServerId, jellyfinUserId)])
            val refreshToken =
                decrypt(
                    prefs[
                        getAudiobookshelfKey("abs_refresh_token", jellyfinServerId, jellyfinUserId)]
                )

            if (url != null && token != null && absUserId != null && username != null) {
                AudiobookshelfAuthData(
                    serverUrl = url,
                    accessToken = token,
                    absUserId = absUserId,
                    username = username,
                    refreshToken = refreshToken,
                )
            } else {
                null
            }
        }
    }

    override suspend fun clearAudiobookshelfAuthForUser(
        jellyfinServerId: String,
        jellyfinUserId: UUID,
    ) {
        context.dataStore.edit { prefs ->
            prefs.remove(getAudiobookshelfKey("abs_url", jellyfinServerId, jellyfinUserId))
            prefs.remove(getAudiobookshelfKey("abs_token", jellyfinServerId, jellyfinUserId))
            prefs.remove(getAudiobookshelfKey("abs_userid", jellyfinServerId, jellyfinUserId))
            prefs.remove(getAudiobookshelfKey("abs_user", jellyfinServerId, jellyfinUserId))
            prefs.remove(
                getAudiobookshelfKey("abs_refresh_token", jellyfinServerId, jellyfinUserId)
            )
        }
        clearActiveAudiobookshelfCache()
        Timber.d("Cleared Audiobookshelf auth for user $jellyfinUserId on server $jellyfinServerId")
    }

    override fun getCachedAudiobookshelfServerUrl(): String? = cachedAudiobookshelfUrl

    override fun getCachedAudiobookshelfToken(): String? = cachedAudiobookshelfToken

    override fun getCachedAudiobookshelfRefreshToken(): String? = cachedAudiobookshelfRefreshToken

    override fun updateCachedAudiobookshelfTokens(accessToken: String, refreshToken: String?) {
        cachedAudiobookshelfToken = accessToken
        cachedAudiobookshelfRefreshToken = refreshToken
        val serverId = activeAbsServerId
        val userId = activeAbsUserId
        if (serverId == null || userId == null) {
            Timber.w("Cannot persist ABS tokens - no active context")
            return
        }
        persistScope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[getAudiobookshelfKey("abs_token", serverId, userId)] =
                        encrypt(accessToken)
                    if (refreshToken != null) {
                        prefs[getAudiobookshelfKey("abs_refresh_token", serverId, userId)] =
                            encrypt(refreshToken)
                    } else {
                        prefs.remove(getAudiobookshelfKey("abs_refresh_token", serverId, userId))
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist updated ABS tokens")
            }
        }
    }

    override suspend fun hasValidAudiobookshelfAuth(): Boolean {
        if (!cachedAudiobookshelfToken.isNullOrBlank() && !cachedAudiobookshelfUrl.isNullOrBlank())
            return true
        return false
    }

    @Volatile
    override var onAbsAuthInvalidated: (() -> Unit)? = null
}
