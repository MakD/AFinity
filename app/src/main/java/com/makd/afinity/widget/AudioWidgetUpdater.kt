package com.makd.afinity.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.ItemWithProgress
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.player.LastPlayback
import com.makd.afinity.player.LastPlaybackSnapshot
import com.makd.afinity.player.LastPlaybackStore
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.player.music.MusicQueueManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class AudioWidgetUpdater
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val musicPlaybackManager: Lazy<MusicPlaybackManager>,
    private val musicQueueManager: Lazy<MusicQueueManager>,
    private val absPlaybackManager: Lazy<AudiobookshelfPlaybackManager>,
    private val audiobookshelfRepository: Lazy<AudiobookshelfRepository>,
    private val sessionManager: Lazy<SessionManager>,
    private val lastPlaybackStore: LastPlaybackStore,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val settingsStore: AudioWidgetSettingsStore,
    private val castManager: Lazy<CastManager>,
) {
    private val renderer = AudioWidgetRenderer(context)
    private val started = AtomicBoolean(false)
    @Volatile private var latest: AudioWidgetState? = null
    @Volatile private var cachedCover: Pair<String, Bitmap>? = null

    private data class MusicSlice(
        val title: String?,
        val artist: String?,
        val coverUrl: String?,
        val isPlaying: Boolean,
    )

    private data class AbsSlice(
        val itemId: String?,
        val episodeId: String?,
        val title: String,
        val author: String?,
        val episodeTitle: String?,
        val coverUrl: String?,
        val isPlaying: Boolean,
    )

    private data class QueuedTrack(val title: String, val artist: String?)

    private data class AbsCandidate(
        val itemId: String,
        val episodeId: String?,
        val title: String,
        val subtitle: String?,
        val lastUpdate: Long,
    )

    private data class Inputs(
        val music: MusicSlice,
        val abs: AbsSlice,
        val snapshot: LastPlaybackSnapshot,
        val sessionKey: String?,
        val queuedTrack: QueuedTrack?,
        val absInProgress: List<AbsCandidate>,
        val isCasting: Boolean,
    )

    private data class Secondary(
        val queuedTrack: QueuedTrack?,
        val absInProgress: List<AbsCandidate>,
        val isCasting: Boolean,
    )

    fun startIfWidgetsPlaced() {
        if (started.get()) return
        scope.launch { if (placedWidgetIds().isNotEmpty()) start() }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            liveState().collect { state ->
                latest = state
                push(state)
            }
        }
    }

    fun refreshAsync(onDone: () -> Unit = {}) {
        scope.launch {
            try {
                val state =
                    latest ?: idleState(lastPlaybackStore.snapshot.first(), currentSessionKey())
                push(state)
            } finally {
                onDone()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun liveState(): Flow<AudioWidgetState> {
        val musicFlow =
            musicPlaybackManager
                .get()
                .state
                .map { state ->
                    val track = state.currentTrack
                    MusicSlice(
                        title = track?.name,
                        artist = track?.let { it.artist ?: it.artists.firstOrNull() },
                        coverUrl = track?.images?.primary?.toString(),
                        isPlaying = track != null && state.isPlaying,
                    )
                }
                .distinctUntilChanged()
        val absFlow =
            absPlaybackManager
                .get()
                .playbackState
                .map { state ->
                    AbsSlice(
                        itemId = state.itemId?.takeIf { state.sessionId != null },
                        episodeId = state.episodeId,
                        title = state.displayTitle,
                        author = state.displayAuthor,
                        episodeTitle =
                            state.currentChapter?.title?.takeIf { state.isPodcastPlaylist },
                        coverUrl = state.coverUrl,
                        isPlaying = state.isPlaying,
                    )
                }
                .distinctUntilChanged()
        val sessionKeyFlow =
            sessionManager
                .get()
                .currentSession
                .map { session ->
                    session?.let { LastPlaybackStore.sessionKey(it.serverId, it.userId) }
                }
                .distinctUntilChanged()
        val queue = musicQueueManager.get()
        val queuedTrackFlow =
            combine(queue.queue, queue.currentIndex) { tracks, index ->
                    tracks.getOrNull(index)?.let {
                        QueuedTrack(it.name, it.artist ?: it.artists.firstOrNull())
                    }
                }
                .distinctUntilChanged()
        val absInProgressFlow =
            audiobookshelfRepository
                .get()
                .getInProgressItemsFlow()
                .map { items -> items.mapNotNull(::toAbsCandidate) }
                .catch { e ->
                    Timber.w(e, "Widget: could not read Audiobookshelf progress")
                    emit(emptyList())
                }
                .distinctUntilChanged()
        val castingFlow =
            castManager
                .get()
                .castState
                .map { it.isMusicCasting || it.isAbsCasting }
                .distinctUntilChanged()
        val secondaryFlow = combine(queuedTrackFlow, absInProgressFlow, castingFlow, ::Secondary)
        return combine(
                musicFlow,
                absFlow,
                lastPlaybackStore.snapshot,
                sessionKeyFlow,
                secondaryFlow,
            ) { music, abs, snapshot, sessionKey, secondary ->
                Inputs(
                    music,
                    abs,
                    snapshot,
                    sessionKey,
                    secondary.queuedTrack,
                    secondary.absInProgress,
                    secondary.isCasting,
                )
            }
            .mapLatest(::compute)
            .distinctUntilChanged()
    }

    private suspend fun compute(inputs: Inputs): AudioWidgetState {
        val top = nowPlaying(inputs)
        return top.copy(continueItems = continueItems(top, inputs), isCasting = inputs.isCasting)
    }

    private suspend fun nowPlaying(inputs: Inputs): AudioWidgetState {
        val music = inputs.music
        if (music.title != null) {
            return AudioWidgetState(
                status =
                    if (music.isPlaying) AudioWidgetStatus.PLAYING else AudioWidgetStatus.PAUSED,
                engine = AudioWidgetEngine.MUSIC,
                title = music.title,
                subtitle = music.artist?.let(::byLine),
                isSignedIn = true,
                isLoaded = true,
                coverUrl = music.coverUrl,
            )
        }
        val abs = inputs.abs
        if (abs.itemId != null) {
            val showName = abs.title.ifBlank { null }
            return AudioWidgetState(
                status = if (abs.isPlaying) AudioWidgetStatus.PLAYING else AudioWidgetStatus.PAUSED,
                engine = AudioWidgetEngine.ABS,
                title = abs.episodeTitle ?: showName,
                subtitle = if (abs.episodeTitle != null) showName else abs.author?.let(::byLine),
                isSignedIn = true,
                isLoaded = true,
                absItemId = abs.itemId,
                absEpisodeId = abs.episodeId,
                coverUrl = abs.coverUrl,
            )
        }
        return idleState(inputs.snapshot, inputs.sessionKey)
    }

    private fun continueItems(top: AudioWidgetState, inputs: Inputs): List<ContinueItem> {
        if (!top.isSignedIn) return emptyList()
        val candidates = mutableListOf<Pair<Long, ContinueItem>>()
        inputs.absInProgress.forEach { candidate ->
            val isOnTop =
                top.engine == AudioWidgetEngine.ABS &&
                    top.status != AudioWidgetStatus.STOPPED &&
                    top.absItemId == candidate.itemId &&
                    top.absEpisodeId == candidate.episodeId
            if (!isOnTop) {
                candidates +=
                    candidate.lastUpdate to
                        ContinueItem(
                            engine = AudioWidgetEngine.ABS,
                            title = candidate.title,
                            subtitle = candidate.subtitle,
                            absItemId = candidate.itemId,
                            absEpisodeId = candidate.episodeId,
                        )
            }
        }
        val queued = inputs.queuedTrack
        val memo = inputs.snapshot.music
        val musicOnTop =
            top.engine == AudioWidgetEngine.MUSIC && top.status != AudioWidgetStatus.STOPPED
        val sameSession = inputs.sessionKey == null || inputs.sessionKey == memo?.sessionKey
        if (queued != null && memo != null && !musicOnTop && sameSession) {
            candidates +=
                memo.playedAt to
                    ContinueItem(
                        engine = AudioWidgetEngine.MUSIC,
                        title = queued.title,
                        subtitle = queued.artist?.let(::byLine),
                    )
        }
        return candidates.sortedByDescending { it.first }.take(MAX_CONTINUE_ITEMS).map { it.second }
    }

    private fun toAbsCandidate(entry: ItemWithProgress): AbsCandidate? {
        val progress = entry.progress ?: return null
        if (progress.hideFromContinueListening == true || progress.duration <= 0) return null
        val metadata = entry.item.media.metadata
        val showTitle = metadata.title?.takeIf { it.isNotBlank() } ?: return null
        val episodeId = progress.episodeId
        val episodeTitle = episodeId?.let { id ->
            entry.item.media.episodes?.firstOrNull { it.id == id }?.title
        }
        return AbsCandidate(
            itemId = entry.item.id,
            episodeId = episodeId,
            title = episodeTitle ?: showTitle,
            subtitle =
                when {
                    episodeId == null -> metadata.authorName?.let(::byLine)
                    episodeTitle != null -> showTitle
                    else -> null
                },
            lastUpdate = progress.lastUpdate,
        )
    }

    private suspend fun idleState(
        snapshot: LastPlaybackSnapshot,
        sessionKey: String?,
    ): AudioWidgetState {
        val signedIn = sessionKey != null || hasSavedAuth()
        val last = snapshot.last
        val display = snapshot.display
        if (
            !signedIn ||
                last == null ||
                display == null ||
                (sessionKey != null && sessionKey != last.sessionKey)
        ) {
            return AudioWidgetState(
                status = AudioWidgetStatus.STOPPED,
                engine = AudioWidgetEngine.MUSIC,
                title = null,
                subtitle = null,
                isSignedIn = signedIn,
                isLoaded = false,
            )
        }
        val abs = last as? LastPlayback.Abs
        return AudioWidgetState(
            status = AudioWidgetStatus.PAUSED,
            engine = if (abs != null) AudioWidgetEngine.ABS else AudioWidgetEngine.MUSIC,
            title = display.title,
            subtitle = display.subtitle?.let(::byLine),
            isSignedIn = true,
            isLoaded = false,
            absItemId = abs?.itemId,
            absEpisodeId = abs?.episodeId,
            coverUrl = display.artworkUri,
        )
    }

    private fun currentSessionKey(): String? =
        sessionManager.get().currentSession.value?.let {
            LastPlaybackStore.sessionKey(it.serverId, it.userId)
        }

    private suspend fun hasSavedAuth(): Boolean =
        try {
            securePreferencesRepository.hasValidAuthData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Widget: could not read saved sign-in")
            false
        }

    private fun byLine(name: String): String = context.getString(R.string.widget_by_fmt, name)

    fun currentState(): AudioWidgetState? = latest

    fun deleteSettingsAsync(widgetIds: IntArray, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                settingsStore.delete(widgetIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Widget: could not delete settings")
            } finally {
                onDone()
            }
        }
    }

    private fun placedWidgetIds(): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, AudioWidgetProvider::class.java))

    private suspend fun push(state: AudioWidgetState) {
        try {
            val ids = placedWidgetIds()
            if (ids.isEmpty()) return
            val settingsById = ids.associateWith { settingsStore.get(it) }
            val cover =
                if (settingsById.values.any { it.showCover }) loadCover(state.coverUrl) else null
            val manager = AppWidgetManager.getInstance(context)
            ids.forEach { id ->
                val settings = settingsById.getValue(id)
                val lightText = AudioWidgetLook.isLightText(context, settings)
                manager.updateAppWidget(id, renderer.render(state, settings, lightText, cover))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Widget: update failed")
        }
    }

    private suspend fun loadCover(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cachedCover?.let { (cachedUrl, bitmap) -> if (cachedUrl == url) return bitmap }
        val bitmap =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(COVER_SIZE_PX)
                            .allowHardware(false)
                            .build()
                    val result = SingletonImageLoader.get(context).execute(request)
                    (result as? SuccessResult)?.image?.asDrawable(context.resources)?.toBitmap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Widget: cover load failed")
                    null
                }
            }
        if (bitmap != null) cachedCover = url to bitmap
        return bitmap
    }

    private companion object {
        const val MAX_CONTINUE_ITEMS = 3
        const val COVER_SIZE_PX = 192
    }
}
