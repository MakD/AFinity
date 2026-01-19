package com.makd.afinity.data.manager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SavedSession(
    val serverId: String,
    val userId: UUID,
    val serverUrl: String
)

@Singleton
class SessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        private val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        private val ACTIVE_SERVER_URL = stringPreferencesKey("active_server_url")
        private val LAST_ACTIVE_TIMESTAMP = stringPreferencesKey("last_active_timestamp")
    }

    suspend fun saveActiveSession(serverId: String, userId: UUID, serverUrl: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_SERVER_ID] = serverId
            prefs[ACTIVE_USER_ID] = userId.toString()
            prefs[ACTIVE_SERVER_URL] = serverUrl
            prefs[LAST_ACTIVE_TIMESTAMP] = System.currentTimeMillis().toString()
        }
        Timber.d("Saved active session: serverId=$serverId, userId=$userId")
    }

    suspend fun getActiveSession(): SavedSession? {
        val prefs = dataStore.data.first()
        val serverId = prefs[ACTIVE_SERVER_ID] ?: return null
        val userId = prefs[ACTIVE_USER_ID]?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                Timber.w("Invalid UUID in saved session: $it")
                return null
            }
        } ?: return null
        val serverUrl = prefs[ACTIVE_SERVER_URL] ?: return null

        return SavedSession(serverId, userId, serverUrl)
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIVE_SERVER_ID)
            prefs.remove(ACTIVE_USER_ID)
            prefs.remove(ACTIVE_SERVER_URL)
            prefs.remove(LAST_ACTIVE_TIMESTAMP)
        }
        Timber.d("Cleared active session")
    }

    suspend fun getLastActiveTimestamp(): Long? {
        val prefs = dataStore.data.first()
        return prefs[LAST_ACTIVE_TIMESTAMP]?.toLongOrNull()
    }
}