package com.makd.afinity.ui.components.mediainfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.toAfinitySource
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MediaInfoSources {
    data object Loading : MediaInfoSources

    data class Ready(val sources: List<AfinitySource>) : MediaInfoSources

    data object Failed : MediaInfoSources
}

@HiltViewModel
class MediaInfoViewModel
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val offlineModeManager: OfflineModeManager,
    sessionManager: SessionManager,
) : ViewModel() {

    val isJellyfinAdmin: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.isAdmin == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _sources = MutableStateFlow<MediaInfoSources>(MediaInfoSources.Loading)
    val sources: StateFlow<MediaInfoSources> = _sources.asStateFlow()

    private val _isAudiobookshelfAdmin = MutableStateFlow(false)
    val isAudiobookshelfAdmin: StateFlow<Boolean> = _isAudiobookshelfAdmin.asStateFlow()

    private val _downloadedTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<AudioTrack>> = _downloadedTracks.asStateFlow()

    private var sourcesJob: Job? = null
    private var audiobookshelfJob: Job? = null

    fun loadSources(itemId: UUID) {
        sourcesJob?.cancel()
        _sources.value = MediaInfoSources.Loading
        sourcesJob = viewModelScope.launch {
            val remote =
                if (offlineModeManager.isOffline.first()) null
                else mediaRepository.getItemResult(itemId, FieldSets.MEDIA_INFO).getOrNull()
            val baseUrl = mediaRepository.getBaseUrl()
            val sources =
                remote
                    ?.mediaSources
                    .orEmpty()
                    .map { it.toAfinitySource(baseUrl, itemId) }
                    .ifEmpty {
                        databaseRepository.getSourcesForItem(itemId).filter {
                            it.type == AfinitySourceType.LOCAL && !it.path.endsWith(".download")
                        }
                    }
            _sources.value =
                if (sources.isEmpty()) MediaInfoSources.Failed else MediaInfoSources.Ready(sources)
        }
    }

    fun loadAudiobookshelf(itemId: String, episodeId: String?) {
        audiobookshelfJob?.cancel()
        _downloadedTracks.value = emptyList()
        audiobookshelfJob = viewModelScope.launch {
            _downloadedTracks.value =
                audiobookshelfRepository.getDownloadedTracks(itemId, episodeId)
            _isAudiobookshelfAdmin.value = audiobookshelfRepository.isCurrentUserAdmin()
        }
    }
}
