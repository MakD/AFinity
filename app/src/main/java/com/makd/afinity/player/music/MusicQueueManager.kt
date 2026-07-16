package com.makd.afinity.player.music

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.makd.afinity.data.database.dao.MusicQueueDao
import com.makd.afinity.data.database.entities.MusicQueueEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LoadQueueEvent(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
    val startPositionMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class RearrangeQueueEvent(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
)

private val KEY_CURRENT_INDEX = intPreferencesKey("music_current_index")
private val KEY_REPEAT_MODE = stringPreferencesKey("music_repeat_mode")
private val KEY_SHUFFLED = booleanPreferencesKey("music_shuffled")

private const val MAX_QUEUE_SIZE = 5000

@Singleton
class MusicQueueManager
@Inject
constructor(
    private val musicQueueDao: MusicQueueDao,
    private val sessionManager: SessionManager,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queue = MutableStateFlow<List<AfinityTrack>>(emptyList())
    val queue: StateFlow<List<AfinityTrack>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _loadQueueEvents =
        MutableSharedFlow<LoadQueueEvent>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val loadQueueEvents: SharedFlow<LoadQueueEvent> = _loadQueueEvents.asSharedFlow()

    private val _rearrangeQueueEvents =
        MutableSharedFlow<RearrangeQueueEvent>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val rearrangeQueueEvents: SharedFlow<RearrangeQueueEvent> = _rearrangeQueueEvents.asSharedFlow()

    private var originalQueue: List<AfinityTrack> = emptyList()

    val currentTrack: AfinityTrack?
        get() = _queue.value.getOrNull(_currentIndex.value)

    init {
        scope.launch { restoreFromRoom() }
    }

    fun loadQueue(tracks: List<AfinityTrack>, startIndex: Int, startPositionMs: Long = 0L) {
        val clamped = tracks.take(MAX_QUEUE_SIZE)
        _queue.value = clamped
        _currentIndex.value = startIndex.coerceIn(0, (clamped.size - 1).coerceAtLeast(0))
        originalQueue = emptyList()

        scope.launch {
            persistQueue(clamped)
            saveState(startIndex, RepeatMode.OFF.name, false)
        }

        emitLoadEvent(clamped, startIndex, startPositionMs)
    }

    fun addNext(tracks: List<AfinityTrack>) {
        val wasEmpty = _queue.value.isEmpty()
        val current = _queue.value.toMutableList()
        val insertAt = (_currentIndex.value + 1).coerceAtMost(current.size)
        current.addAll(insertAt, tracks)
        val clamped = current.take(MAX_QUEUE_SIZE)
        _queue.value = clamped
        scope.launch { persistQueue(clamped) }

        val mediaItems = clamped.map { buildMediaItem(it) }
        scope.launch {
            if (wasEmpty) {
                _loadQueueEvents.emit(
                    LoadQueueEvent(
                        mediaItems = mediaItems,
                        startIndex = _currentIndex.value,
                        startPositionMs = 0L,
                    )
                )
            } else {
                _rearrangeQueueEvents.emit(RearrangeQueueEvent(mediaItems, _currentIndex.value))
            }
        }
    }

    fun addLast(tracks: List<AfinityTrack>) {
        val current = _queue.value.toMutableList()
        current.addAll(tracks)

        val trimmed: List<AfinityTrack>
        val newIndex: Int
        if (current.size > MAX_QUEUE_SIZE) {
            val excess = current.size - MAX_QUEUE_SIZE
            val trimCount = excess.coerceAtMost(_currentIndex.value)
            trimmed = current.drop(trimCount).take(MAX_QUEUE_SIZE)
            newIndex = (_currentIndex.value - trimCount).coerceAtLeast(0)
        } else {
            trimmed = current
            newIndex = _currentIndex.value
        }

        _queue.value = trimmed
        if (newIndex != _currentIndex.value) {
            _currentIndex.value = newIndex
            scope.launch { dataStore.edit { prefs -> prefs[KEY_CURRENT_INDEX] = newIndex } }
        }
        scope.launch { persistQueue(trimmed) }
        val mediaItems = trimmed.map { buildMediaItem(it) }
        scope.launch {
            _rearrangeQueueEvents.emit(RearrangeQueueEvent(mediaItems, newIndex))
        }
    }

    fun removeAt(index: Int) {
        val current = _queue.value.toMutableList()
        if (index < 0 || index >= current.size) return
        val removingCurrent = index == _currentIndex.value
        current.removeAt(index)
        _queue.value = current
        if (index < _currentIndex.value) {
            _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
        } else {
            _currentIndex.value =
                _currentIndex.value.coerceAtMost((current.size - 1).coerceAtLeast(0))
        }
        val mediaItems = current.map { buildMediaItem(it) }
        scope.launch {
            persistQueue(current)
            if (removingCurrent) {
                _loadQueueEvents.emit(
                    LoadQueueEvent(
                        mediaItems = mediaItems,
                        startIndex = _currentIndex.value,
                        startPositionMs = 0L,
                    )
                )
            } else {
                _rearrangeQueueEvents.emit(RearrangeQueueEvent(mediaItems, _currentIndex.value))
            }
        }
    }

    fun moveTrack(from: Int, to: Int) {
        if (from == to) return
        val current = _queue.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        _queue.value = current
        val cur = _currentIndex.value
        _currentIndex.value =
            when {
                from == cur -> to
                cur in (from + 1)..to -> cur - 1
                cur in to..<from -> cur + 1
                else -> cur
            }
        val mediaItems = current.map { buildMediaItem(it) }
        scope.launch {
            persistQueue(current)
            _rearrangeQueueEvents.emit(RearrangeQueueEvent(mediaItems, _currentIndex.value))
        }
    }

    fun updateTrackInQueue(track: AfinityTrack) {
        _queue.value = _queue.value.map { if (it.id == track.id) track else it }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = 0
        scope.launch { musicQueueDao.clearQueue() }
    }

    fun onTrackChanged(newIndex: Int) {
        _currentIndex.value = newIndex.coerceIn(0, (_queue.value.size - 1).coerceAtLeast(0))
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_CURRENT_INDEX] = newIndex }
        }
    }

    fun toggleShuffle(currentPositionMs: Long = 0L) {
        val current = _queue.value
        if (current.isEmpty()) return

        val currentTrack = current.getOrNull(_currentIndex.value)
        val willShuffle = originalQueue.isEmpty()

        val newQueue: List<AfinityTrack>
        val newCurrentIndex: Int

        if (willShuffle) {
            originalQueue = current
            newQueue =
                current.toMutableList().also {
                    if (currentTrack != null) it.remove(currentTrack)
                    it.shuffle()
                    if (currentTrack != null) it.add(0, currentTrack)
                }
            newCurrentIndex = 0
        } else {
            newCurrentIndex =
                originalQueue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
            newQueue = originalQueue
            originalQueue = emptyList()
        }

        _queue.value = newQueue
        _currentIndex.value = newCurrentIndex

        val mediaItems = newQueue.map { buildMediaItem(it) }
        scope.launch {
            _rearrangeQueueEvents.emit(RearrangeQueueEvent(mediaItems, newCurrentIndex))
            dataStore.edit { prefs ->
                prefs[KEY_SHUFFLED] = willShuffle
                prefs[KEY_CURRENT_INDEX] = newCurrentIndex
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index.coerceIn(0, (_queue.value.size - 1).coerceAtLeast(0))
    }

    private fun emitLoadEvent(tracks: List<AfinityTrack>, startIndex: Int, startPositionMs: Long) {
        val mediaItems = tracks.map { buildMediaItem(it) }
        scope.launch {
            _loadQueueEvents.emit(
                LoadQueueEvent(
                    mediaItems = mediaItems,
                    startIndex = startIndex,
                    startPositionMs = startPositionMs,
                )
            )
        }
    }

    private fun buildMediaItem(track: AfinityTrack): MediaItem {
        val artworkUri = track.images.primary
        val metadata =
            MediaMetadata.Builder()
                .setTitle(track.name)
                .setArtist(track.artist ?: track.artists.firstOrNull())
                .setAlbumTitle(track.album)
                .setArtworkUri(artworkUri)
                .build()
        return MediaItem.Builder()
            .setUri(buildStreamUri(track))
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildStreamUri(track: AfinityTrack): Uri {
        val localPath = track.localFilePath
        if (!localPath.isNullOrBlank()) {
            val localUri = localPath.toUri()
            if (localUri.scheme == "file") return localUri
        }
        val baseUrl = sessionManager.currentSession.value?.serverUrl?.trimEnd('/') ?: ""
        return "$baseUrl/Audio/${track.id}/stream?static=true".toUri()
    }

    private suspend fun persistQueue(tracks: List<AfinityTrack>) {
        val serverId = sessionManager.currentSession.value?.serverId ?: ""
        val entities = tracks.mapIndexed { index, track -> track.toEntity(index, serverId) }
        runCatching {
            musicQueueDao.clearQueue()
            musicQueueDao.insertAll(entities)
        }
            .onFailure { Timber.w(it, "Failed to persist music queue") }
    }

    private suspend fun restoreFromRoom() {
        runCatching {
            val entities = musicQueueDao.getQueue()
            if (entities.isEmpty()) return@runCatching
            val tracks = entities.map { it.toAfinityTrack() }
            _queue.value = tracks
            val prefs = dataStore.data.first()
            _currentIndex.value =
                (prefs[KEY_CURRENT_INDEX] ?: 0).coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        }
            .onFailure { Timber.w(it, "Failed to restore music queue") }
    }

    private suspend fun saveState(index: Int, repeatMode: String, shuffled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT_INDEX] = index
            prefs[KEY_REPEAT_MODE] = repeatMode
            prefs[KEY_SHUFFLED] = shuffled
        }
    }
}

private fun AfinityTrack.toEntity(position: Int, serverId: String) =
    MusicQueueEntity(
        position = position,
        trackId = id.toString(),
        name = name,
        artist = artist,
        albumId = albumId?.toString(),
        album = album,
        durationMs = runtimeTicks / 10_000L,
        imageUrl = images.primary?.toString(),
        normalizationGain = normalizationGain,
        indexNumber = indexNumber,
        discNumber = discNumber,
        serverId = serverId,
    )

private fun MusicQueueEntity.toAfinityTrack() =
    AfinityTrack(
        id = UUID.fromString(trackId),
        name = name,
        albumId = albumId?.let { UUID.fromString(it) },
        album = album,
        artistId = null,
        artist = artist,
        artists = listOfNotNull(artist),
        indexNumber = indexNumber,
        discNumber = discNumber,
        productionYear = null,
        runtimeTicks = durationMs * 10_000L,
        playbackPositionTicks = 0L,
        played = false,
        favorite = false,
        playCount = null,
        normalizationGain = normalizationGain,
        images = AfinityImages(primary = imageUrl?.toUri()),
    )
