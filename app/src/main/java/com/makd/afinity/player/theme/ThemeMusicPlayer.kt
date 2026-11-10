package com.makd.afinity.player.theme

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.ThemeSong
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(UnstableApi::class)
@Singleton
class ThemeMusicPlayer
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val databaseProvider: DatabaseProvider,
    private val musicPlaybackManager: MusicPlaybackManager,
    private val audiobookshelfPlaybackManager: AudiobookshelfPlaybackManager,
) {
    private class Deck(val songId: UUID, val ownerId: UUID, val player: ExoPlayer) {
        var level = 0f
        var fadeJob: Job? = null
        var endJob: Job? = null
    }

    private class CachedTheme(val song: ThemeSong?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            databaseProvider,
        )
    }

    private val lookups = HashMap<UUID, CachedTheme>()
    private var lookupSessionKey: String? = null

    private val decks = mutableListOf<Deck>()
    private var current: Deck? = null
    private var holder: Any? = null
    private var finishedOwnerId: UUID? = null
    private var resumeSongId: UUID? = null
    private var resumePositionMs = 0L
    private var lastOwnAudioAt = 0L

    private var enabled = false
    private var volume = DEFAULT_VOLUME
    private var onMobileData = false
    private var hasFocus = false

    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            hasFocus = false
                            fadeOutCurrent(rememberPosition = false)
                        }
                    }
                },
                mainHandler,
            )
            .build()

    init {
        scope.launch {
            preferencesRepository.getThemeMusicEnabledFlow().collect {
                enabled = it
                if (!it) fadeOutCurrent(rememberPosition = false)
            }
        }
        scope.launch {
            preferencesRepository.getThemeMusicVolumeFlow().collect {
                volume = it / 100f
                decks.forEach(::applyVolume)
            }
        }
        scope.launch {
            preferencesRepository.getThemeMusicOnMobileDataFlow().collect { onMobileData = it }
        }
    }

    suspend fun resolve(item: AfinityItem): ThemeSong? {
        val chain =
            when (item) {
                is AfinityEpisode -> listOf(item.id, item.seasonId, item.seriesId)
                is AfinitySeason -> listOf(item.id, item.seriesId)
                is AfinityMovie,
                is AfinityShow,
                is AfinityBoxSet -> listOf(item.id)
                else -> return null
            }
        if (!preferencesRepository.getThemeMusicEnabled()) return null
        if (!networkMonitor.isOnWifi() && !preferencesRepository.getThemeMusicOnMobileData()) {
            return null
        }
        if (otherAudioActive()) return null

        val session = sessionManager.currentSession.value ?: return null
        val sessionKey = "${session.serverId}:${session.userId}"
        if (sessionKey != lookupSessionKey) {
            lookups.clear()
            lookupSessionKey = sessionKey
        }

        lookups[item.id]?.let {
            return it.song
        }
        if (item is AfinityEpisode) {
            lookups[item.seasonId]?.let {
                return it.song
            }
        }

        val result = mediaRepository.getThemeSong(item.id) ?: return null
        if (sessionKey != lookupSessionKey) return result.song
        val ownerIndex = result.ownerId?.let(chain::indexOf) ?: -1
        val covered =
            when {
                result.song == null -> chain
                ownerIndex >= 0 -> chain.take(ownerIndex + 1)
                else -> chain.take(1)
            }
        covered.forEach { lookups[it] = CachedTheme(result.song) }
        return result.song
    }

    fun hold(owner: Any) {
        holder = owner
    }

    fun play(owner: Any, song: ThemeSong?) {
        if (holder !== owner) return
        if (song == null) {
            fadeOutCurrent(rememberPosition = false)
            return
        }
        val playing = current
        if (playing?.ownerId == song.ownerId) return
        if (song.ownerId == finishedOwnerId) {
            fadeOutCurrent(rememberPosition = false)
            return
        }
        if (!enabled) return
        if (!networkMonitor.isOnWifi() && !onMobileData) return
        val url = streamUrl(song.id) ?: return
        if (playing == null) {
            if (otherAudioActive()) return
            if (!requestFocus()) return
        }
        fadeOutCurrent(rememberPosition = true)
        startDeck(song, url)
    }

    fun release(owner: Any) {
        if (holder === owner) holder = null
    }

    fun stop() {
        holder = null
        fadeOutCurrent(rememberPosition = true)
    }

    private fun otherAudioActive(): Boolean {
        if (musicPlaybackManager.state.value.isPlaying) return true
        if (audiobookshelfPlaybackManager.playbackState.value.isPlaying) return true
        if (decks.isNotEmpty()) return false
        if (SystemClock.elapsedRealtime() - lastOwnAudioAt < OWN_AUDIO_GRACE_MS) return false
        return audioManager.isMusicActive
    }

    private fun startDeck(song: ThemeSong, url: String) {
        val songId = song.id
        val player =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            MIN_BUFFER_MS,
                            MAX_BUFFER_MS,
                            START_BUFFER_MS,
                            REBUFFER_MS,
                        )
                        .build()
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        val deck = Deck(songId, song.ownerId, player)
        decks += deck
        current = deck
        finishedOwnerId = null
        applyVolume(deck)

        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (current === deck) finishedOwnerId = deck.ownerId
                        mainHandler.post { retire(deck) }
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (
                        !playWhenReady &&
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    ) {
                        mainHandler.post { retire(deck) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Theme song playback failed for $songId")
                    mainHandler.post { retire(deck) }
                }
            }
        )

        player.setMediaItem(
            MediaItem.Builder().setUri(url).setCustomCacheKey("theme_$songId").build()
        )
        if (songId == resumeSongId) {
            player.seekTo(resumePositionMs)
        }
        resumeSongId = null
        player.prepare()
        player.playWhenReady = true

        fadeTo(deck, 1f, FADE_IN_MS)
        deck.endJob = scope.launch { fadeBeforeEnd(deck) }
    }

    private suspend fun fadeBeforeEnd(deck: Deck) {
        while (currentCoroutineContext().isActive) {
            val duration = deck.player.duration
            if (duration != C.TIME_UNSET && duration > 0) {
                val remaining = duration - deck.player.currentPosition
                if (remaining <= END_FADE_MS) {
                    if (current === deck) {
                        current = null
                        finishedOwnerId = deck.ownerId
                    }
                    fadeTo(deck, 0f, remaining.coerceIn(STEP_MS, END_FADE_MS)) { retire(deck) }
                    return
                }
            }
            delay(END_POLL_MS.milliseconds)
        }
    }

    private fun fadeOutCurrent(rememberPosition: Boolean) {
        val deck = current ?: return
        current = null
        if (rememberPosition) {
            resumeSongId = deck.songId
            resumePositionMs = deck.player.currentPosition
        }
        deck.endJob?.cancel()
        fadeTo(deck, 0f, FADE_OUT_MS) { retire(deck) }
    }

    private fun fadeTo(deck: Deck, target: Float, durationMs: Long, then: (() -> Unit)? = null) {
        deck.fadeJob?.cancel()
        deck.fadeJob = scope.launch {
            val start = deck.level
            val steps = (durationMs / STEP_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                deck.level = start + (target - start) * i / steps
                applyVolume(deck)
                delay(STEP_MS.milliseconds)
            }
            then?.invoke()
        }
    }

    private fun applyVolume(deck: Deck) {
        deck.player.volume = (deck.level * volume).coerceIn(0f, 1f)
    }

    private fun retire(deck: Deck) {
        if (!decks.remove(deck)) return
        if (current === deck) current = null
        deck.endJob?.cancel()
        deck.player.release()
        deck.fadeJob?.cancel()
        if (decks.isEmpty()) {
            lastOwnAudioAt = SystemClock.elapsedRealtime()
            abandonFocus()
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus =
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun streamUrl(songId: UUID): String? {
        val baseUrl = sessionManager.currentSession.value?.serverUrl?.trimEnd('/') ?: return null
        if (baseUrl.isEmpty()) return null
        return "$baseUrl/Audio/$songId/stream?static=true"
    }

    private fun dataSourceFactory(): CacheDataSource.Factory {
        val token = sessionManager.getCurrentApiClient()?.accessToken.orEmpty()
        val http =
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(NETWORK_TIMEOUT_MS)
                .setReadTimeoutMs(NETWORK_TIMEOUT_MS)
                .setDefaultRequestProperties(
                    mapOf("Authorization" to "MediaBrowser Token=\"$token\"")
                )
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private companion object {
        const val CACHE_DIR = "theme_music_cache"
        const val CACHE_BYTES = 50L * 1024L * 1024L
        const val DEFAULT_VOLUME = 0.5f
        const val MIN_BUFFER_MS = 5_000
        const val MAX_BUFFER_MS = 10_000
        const val START_BUFFER_MS = 1_000
        const val REBUFFER_MS = 2_000
        const val FADE_IN_MS = 1_200L
        const val FADE_OUT_MS = 600L
        const val OWN_AUDIO_GRACE_MS = 1_500L
        const val END_FADE_MS = 3_000L
        const val END_POLL_MS = 250L
        const val STEP_MS = 40L
        const val NETWORK_TIMEOUT_MS = 15_000
    }
}
