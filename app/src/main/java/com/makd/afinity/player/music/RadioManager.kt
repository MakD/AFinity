package com.makd.afinity.player.music

import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.RadioMode
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.models.music.RadioState
import com.makd.afinity.data.repository.music.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val MIN_UPCOMING_TRACKS = 5
private const val INITIAL_BATCH_SIZE = 20
private const val APPEND_BATCH_SIZE = 10
private const val CONTINUOUS_INITIAL = 5
private const val CONTINUOUS_APPEND = 2

@Singleton
class RadioManager @Inject constructor(
    private val musicRepository: MusicRepository,
    private val queueManager: MusicQueueManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appendJob: Job? = null

    private val _radioState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    fun startRadio(seed: RadioSeed, mode: RadioMode) {
        appendJob?.cancel()
        scope.launch {
            _radioState.value = RadioState(
                isActive = true,
                mode = mode,
                seedTrackId = seed.trackId,
                albumId = seed.albumId,
                continuousSeedId = seed.trackId,
                sourceTracks = seed.sourceTracks,
                isGenerating = true,
            )
            val count = if (mode == RadioMode.CONTINUOUS) CONTINUOUS_INITIAL else INITIAL_BATCH_SIZE
            val tracks = generateTracks(count)
            if (tracks.isNotEmpty()) {
                queueManager.loadQueue(tracks, startIndex = 0)
            } else {
                Timber.w("RadioManager: no tracks generated for mode $mode, seed=${seed.trackId}")
            }
            _radioState.update { it.copy(isGenerating = false) }
        }
    }

    fun stopRadio() {
        appendJob?.cancel()
        _radioState.value = RadioState()
    }

    fun onTrackChanged(currentTrack: AfinityTrack?) {
        val state = _radioState.value
        if (!state.isActive || state.isGenerating) return
        val queue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        val upcomingCount = queue.size - currentIndex - 1
        if (upcomingCount < MIN_UPCOMING_TRACKS) {
            appendJob?.cancel()
            appendJob = scope.launch { appendTracks() }
        }
    }

    private suspend fun appendTracks() {
        if (_radioState.value.isGenerating) return
        _radioState.update { it.copy(isGenerating = true) }
        val count = if (_radioState.value.mode == RadioMode.CONTINUOUS) CONTINUOUS_APPEND else APPEND_BATCH_SIZE
        val tracks = generateTracks(count)
        if (tracks.isNotEmpty()) {
            queueManager.addLast(tracks)
        }
        _radioState.update { it.copy(isGenerating = false) }
    }

    private suspend fun generateTracks(count: Int): List<AfinityTrack> {
        val state = _radioState.value
        return try {
            when (state.mode) {
                RadioMode.SIMILAR -> similarTracks(state.seedTrackId ?: return emptyList(), count)
                RadioMode.CONTINUOUS -> continuousTracks(
                    state.continuousSeedId ?: state.seedTrackId ?: return emptyList(),
                    count,
                )
                RadioMode.ALBUM_MIX -> albumMixTracks(state.albumId)
                RadioMode.RESHUFFLE -> reshuffleTracks(state.sourceTracks)
                RadioMode.RANDOM -> randomTracks(state.sourceTracks, count)
            }
        } catch (e: Exception) {
            Timber.e(e, "RadioManager: failed to generate tracks for mode ${state.mode}")
            emptyList()
        }
    }

    private suspend fun similarTracks(seedId: UUID, count: Int): List<AfinityTrack> {
        val candidates = musicRepository.getInstantMix(seedId, limit = count + 20)
        val queueIds = queueManager.queue.value.map { it.id }.toSet()
        return candidates.filter { it.id !in queueIds }.take(count)
    }

    private suspend fun continuousTracks(seedId: UUID, count: Int): List<AfinityTrack> {
        val result = mutableListOf<AfinityTrack>()
        var currentSeedId = seedId
        val queueIds = queueManager.queue.value.map { it.id }.toMutableSet()
        repeat(count) {
            val candidates = musicRepository.getInstantMix(currentSeedId, limit = 15)
            val next = candidates.firstOrNull { it.id !in queueIds } ?: return result
            result.add(next)
            queueIds.add(next.id)
            currentSeedId = next.id
        }
        if (result.isNotEmpty()) {
            _radioState.update { it.copy(continuousSeedId = result.last().id) }
        }
        return result
    }

    private suspend fun albumMixTracks(albumId: UUID?): List<AfinityTrack> {
        if (albumId == null) return emptyList()
        val existingAlbumIds = queueManager.queue.value.mapNotNull { it.albumId }.toSet()
        val similarAlbums = musicRepository.getSimilarAlbums(albumId, limit = 15)
        val picked = similarAlbums.firstOrNull { it.id !in existingAlbumIds } ?: return emptyList()
        return musicRepository.getAlbumTracks(picked.id)
    }

    private fun reshuffleTracks(sourceTracks: List<AfinityTrack>): List<AfinityTrack> {
        if (sourceTracks.isEmpty()) return emptyList()
        return sourceTracks.shuffled()
    }

    private fun randomTracks(sourceTracks: List<AfinityTrack>, count: Int): List<AfinityTrack> {
        if (sourceTracks.isEmpty()) return emptyList()
        return sourceTracks.shuffled().take(count)
    }
}