package com.makd.afinity.ui.components

import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSelection(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    downloadRepository: DownloadRepository,
) {
    private val _selected = MutableStateFlow<AfinityEpisode?>(null)
    val selected: StateFlow<AfinityEpisode?> = _selected.asStateFlow()

    private val _watchlistStatus = MutableStateFlow(false)
    val watchlistStatus: StateFlow<Boolean> = _watchlistStatus.asStateFlow()

    private val _downloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val downloadInfo: StateFlow<DownloadInfo?> = _downloadInfo.asStateFlow()

    private var loadJob: Job? = null
    private var loadTarget: AfinityEpisode? = null

    init {
        _selected
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else
                    downloadRepository.getAllDownloadsFlow().map { downloads ->
                        downloads.find { it.itemId == id }
                    }
            }
            .onEach { _downloadInfo.value = it }
            .launchIn(scope)
    }

    fun select(episode: AfinityEpisode) {
        val alreadyLoading = loadJob?.isActive == true && loadTarget?.id == episode.id
        if (alreadyLoading || _selected.value?.id == episode.id) return
        loadJob?.cancel()
        loadTarget = episode
        loadJob =
            scope.launch {
                try {
                    val fullEpisode =
                        mediaRepository
                            .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                            ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)
                    _selected.value = fullEpisode ?: episode
                    _watchlistStatus.value = (fullEpisode ?: episode).liked
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load full episode details")
                    _selected.value = episode
                }
            }
    }

    fun clear() {
        _selected.value = null
        _watchlistStatus.value = false
        _downloadInfo.value = null
    }

    fun toggleFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(scope, episode) {
            _selected.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun toggleWatchlist(episode: AfinityEpisode) {
        val isLiked = _watchlistStatus.value
        itemUserDataDelegate.toggleWatchlist(
            scope = scope,
            item = episode,
            updateOptimisticUI = {
                _watchlistStatus.value = !isLiked
                _selected.value = _selected.value?.copy(liked = !isLiked)
            },
            revertUI = {
                _watchlistStatus.value = isLiked
                _selected.value = _selected.value?.copy(liked = isLiked)
            },
        )
    }

    fun toggleWatched(episode: AfinityEpisode) {
        scope.launch {
            _selected.value = episode.copy(played = !episode.played, playbackPositionTicks = 0)
            val success =
                if (episode.played) {
                    userDataRepository.markUnwatched(episode.id)
                } else {
                    userDataRepository.markWatched(episode.id)
                }
            if (!success) _selected.value = episode
        }
    }
}
