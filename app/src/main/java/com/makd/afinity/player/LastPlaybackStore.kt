package com.makd.afinity.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.di.ApplicationScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

private val KEY_ENGINE = stringPreferencesKey("last_playback_engine")
private val KEY_SESSION = stringPreferencesKey("last_playback_session")
private val KEY_MUSIC_POSITION = longPreferencesKey("last_playback_music_position_ms")
private val KEY_ABS_ITEM = stringPreferencesKey("last_playback_abs_item_id")
private val KEY_ABS_EPISODE = stringPreferencesKey("last_playback_abs_episode_id")
private val KEY_TITLE = stringPreferencesKey("last_playback_title")
private val KEY_SUBTITLE = stringPreferencesKey("last_playback_subtitle")
private val KEY_ARTWORK = stringPreferencesKey("last_playback_artwork_uri")
private val KEY_MUSIC_SESSION = stringPreferencesKey("last_playback_music_session")
private val KEY_MUSIC_PLAYED_AT = longPreferencesKey("last_playback_music_played_at")

private const val ENGINE_MUSIC = "MUSIC"
private const val ENGINE_ABS = "ABS"

sealed class LastPlayback {
    abstract val sessionKey: String

    data class Music(override val sessionKey: String, val positionMs: Long) : LastPlayback()

    data class Abs(override val sessionKey: String, val itemId: String, val episodeId: String?) :
        LastPlayback()
}

data class ResumeDisplay(val title: String, val subtitle: String?, val artworkUri: String?)

data class MusicMemo(val sessionKey: String, val playedAt: Long)

data class LastPlaybackSnapshot(
    val last: LastPlayback?,
    val display: ResumeDisplay?,
    val music: MusicMemo? = null,
)

@Singleton
class LastPlaybackStore
@Inject
constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun recordMusic(sessionKey: String, positionMs: Long) = write {
        it[KEY_ENGINE] = ENGINE_MUSIC
        it[KEY_SESSION] = sessionKey
        it[KEY_MUSIC_POSITION] = positionMs.coerceAtLeast(0L)
        it[KEY_MUSIC_SESSION] = sessionKey
        it[KEY_MUSIC_PLAYED_AT] = System.currentTimeMillis()
    }

    fun recordMusicPosition(positionMs: Long) = write {
        if (it[KEY_ENGINE] == ENGINE_MUSIC) it[KEY_MUSIC_POSITION] = positionMs.coerceAtLeast(0L)
    }

    fun recordAbs(sessionKey: String, itemId: String, episodeId: String?) = write {
        it[KEY_ENGINE] = ENGINE_ABS
        it[KEY_SESSION] = sessionKey
        it[KEY_ABS_ITEM] = itemId
        if (episodeId != null) it[KEY_ABS_EPISODE] = episodeId else it.remove(KEY_ABS_EPISODE)
    }

    fun recordDisplay(title: String, subtitle: String?, artworkUri: String?) = write {
        it[KEY_TITLE] = title
        if (subtitle != null) it[KEY_SUBTITLE] = subtitle else it.remove(KEY_SUBTITLE)
        if (artworkUri != null) it[KEY_ARTWORK] = artworkUri else it.remove(KEY_ARTWORK)
    }

    val snapshot: Flow<LastPlaybackSnapshot> =
        dataStore.data
            .map { LastPlaybackSnapshot(parseLast(it), parseDisplay(it), parseMusic(it)) }
            .catch { e ->
                Timber.w(e, "Failed to observe last playback")
                emit(LastPlaybackSnapshot(null, null))
            }
            .distinctUntilChanged()

    suspend fun readDisplay(): ResumeDisplay? = readPrefs()?.let(::parseDisplay)

    suspend fun read(): LastPlayback? = readPrefs()?.let(::parseLast)

    suspend fun readMusicPosition(): Long = readPrefs()?.get(KEY_MUSIC_POSITION) ?: 0L

    private fun parseMusic(prefs: Preferences): MusicMemo? {
        val sessionKey = prefs[KEY_MUSIC_SESSION] ?: return null
        return MusicMemo(sessionKey, prefs[KEY_MUSIC_PLAYED_AT] ?: 0L)
    }

    private fun parseDisplay(prefs: Preferences): ResumeDisplay? {
        val title = prefs[KEY_TITLE] ?: return null
        return ResumeDisplay(title, prefs[KEY_SUBTITLE], prefs[KEY_ARTWORK])
    }

    private fun parseLast(prefs: Preferences): LastPlayback? {
        val sessionKey = prefs[KEY_SESSION] ?: return null
        return when (prefs[KEY_ENGINE]) {
            ENGINE_MUSIC -> LastPlayback.Music(sessionKey, prefs[KEY_MUSIC_POSITION] ?: 0L)
            ENGINE_ABS ->
                prefs[KEY_ABS_ITEM]?.let {
                    LastPlayback.Abs(sessionKey, it, prefs[KEY_ABS_EPISODE])
                }
            else -> null
        }
    }

    private suspend fun readPrefs(): Preferences? =
        try {
            dataStore.data.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to read last playback")
            null
        }

    private fun write(block: (MutablePreferences) -> Unit) {
        scope.launch {
            try {
                dataStore.edit(block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to save last playback")
            }
        }
    }

    companion object {
        fun sessionKey(serverId: String, userId: UUID): String = "$serverId|$userId"
    }
}
