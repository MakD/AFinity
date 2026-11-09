package com.makd.afinity.ui.favorites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.AdminChangeKind
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.components.EpisodeSelection
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(FlowPreview::class)
@HiltViewModel
class FavoritesViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val musicRepository: MusicRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val downloadPermissions: DownloadPermissions,
    private val itemStore: ItemStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    private var lastFavoritesLoadedAt = System.currentTimeMillis()

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    val uiState: StateFlow<FavoritesUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val movies = live(state.movies)
            val shows = live(state.shows)
            val seasons = live(state.seasons)
            val episodes = live(state.episodes)
            val boxSets = live(state.boxSets)
            val albums = liveOwners(state.favoriteAlbums)
            val artists = liveOwners(state.favoriteArtists)
            val tracks = liveOwners(state.favoriteTracks)
            if (
                movies === state.movies &&
                    shows === state.shows &&
                    seasons === state.seasons &&
                    episodes === state.episodes &&
                    boxSets === state.boxSets &&
                    albums === state.favoriteAlbums &&
                    artists === state.favoriteArtists &&
                    tracks === state.favoriteTracks
            ) {
                state
            } else {
                state.copy(
                    movies = movies,
                    shows = shows,
                    seasons = seasons,
                    episodes = episodes,
                    boxSets = boxSets,
                    favoriteAlbums = albums,
                    favoriteArtists = artists,
                    favoriteTracks = tracks,
                )
            }
        }

    private val episodeSelection =
        EpisodeSelection(
            scope = viewModelScope,
            mediaRepository = mediaRepository,
            userDataRepository = userDataRepository,
            itemUserDataDelegate = itemUserDataDelegate,
            downloadRepository = downloadRepository,
        )

    val selectedEpisode: StateFlow<AfinityEpisode?> = episodeSelection.selected

    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> = episodeSelection.watchlistStatus

    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> = episodeSelection.downloadInfo

    init {
        viewModelScope.launch {
            combine(
                    appDataRepository.favoritesData,
                    appDataRepository.favoritesLoaded,
                    appDataRepository.favoritesLoadFailed,
                ) { data, loaded, failed ->
                    Triple(data, loaded, failed)
                }
                .collect { (data, loaded, failed) ->
                    _uiState.update {
                        it.copy(
                            movies = data.movies,
                            shows = data.shows,
                            seasons = data.seasons,
                            episodes = data.episodes,
                            boxSets = data.boxSets,
                            channels = data.channels,
                            people = data.people,
                            favoriteAlbums = data.favoriteAlbums,
                            favoriteArtists = data.favoriteArtists,
                            favoriteTracks = data.favoriteTracks,
                            favoritePlaylists = data.favoritePlaylists,
                            isLoading = !loaded && !failed,
                            error =
                                if (failed && !loaded) {
                                    context.getString(R.string.error_content_unavailable_server)
                                } else {
                                    null
                                },
                        )
                    }
                    if (loaded) lastFavoritesLoadedAt = System.currentTimeMillis()
                }
        }

        viewModelScope.launch {
            adminChangeBroadcaster.changes
                .filter { change ->
                    when (change.kind) {
                        AdminChangeKind.MEMBERSHIP -> false
                        AdminChangeKind.IMAGES ->
                            _uiState.value.people.any { it.id.toString() == change.itemId }
                        else -> true
                    }
                }
                .collect { appDataRepository.reloadFavorites() }
        }

        viewModelScope.launch {
            appDataRepository.lastResyncAt.collect { at ->
                if (at <= lastFavoritesLoadedAt) return@collect
                lastFavoritesLoadedAt = System.currentTimeMillis()
                appDataRepository.reloadFavorites()
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastFavoritesLoadedAt) {
            lastFavoritesLoadedAt = System.currentTimeMillis()
            viewModelScope.launch { appDataRepository.reloadFavorites() }
        }
    }

    fun loadFavorites() {
        if (appDataRepository.favoritesLoaded.value) return
        viewModelScope.launch { appDataRepository.reloadFavorites() }
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            appDataRepository.reloadFavorites()
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Favorite item clicked: ${item.name} (${item.id})")
    }

    fun selectEpisode(episode: AfinityEpisode) = episodeSelection.select(episode)

    fun clearSelectedEpisode() = episodeSelection.clear()

    fun toggleTrackFavorite(track: AfinityTrack) {
        val tracks = _uiState.value.favoriteTracks
        val newFavorite = !track.favorite
        _uiState.update { state ->
            if (newFavorite) {
                state.copy(
                    favoriteTracks =
                        (tracks.filterNot { it.id == track.id } + track.copy(favorite = true))
                            .sortedBy { it.name }
                )
            } else {
                state.copy(favoriteTracks = tracks.filterNot { it.id == track.id })
            }
        }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(track.id, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(favoriteTracks = tracks) } }
        }
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) = episodeSelection.toggleFavorite(episode)

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) = episodeSelection.toggleWatchlist(episode)

    fun toggleEpisodeWatched(episode: AfinityEpisode) = episodeSelection.toggleWatched(episode)
}

data class FavoritesUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val people: List<AfinityPersonDetail> = emptyList(),
    val channels: List<AfinityChannel> = emptyList(),
    val favoriteAlbums: List<AfinityAlbum> = emptyList(),
    val favoriteArtists: List<AfinityArtist> = emptyList(),
    val favoriteTracks: List<AfinityTrack> = emptyList(),
    val favoritePlaylists: List<AfinityPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
