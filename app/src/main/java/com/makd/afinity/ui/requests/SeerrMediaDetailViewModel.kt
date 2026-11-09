package com.makd.afinity.ui.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.jellyseerr.MediaDetails
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.tmdb.TmdbImage
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.watchlist.SeerrWatchlistMirror
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SeerrMediaDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val jellyseerrRepository: JellyseerrRepository,
    private val tmdbApiService: TmdbApiService,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val sessionManager: SessionManager,
    private val seerrWatchlistMirror: SeerrWatchlistMirror,
) : ViewModel() {

    val tmdbId: Int = checkNotNull(savedStateHandle["seerrTmdbId"])
    val mediaType: MediaType =
        if (savedStateHandle.get<String>("seerrMediaType")?.lowercase() == "tv") MediaType.TV
        else MediaType.MOVIE
    val previewTitle: String? =
        savedStateHandle.get<String>("seerrTitle")?.takeIf { it.isNotBlank() }
    val previewImageUrl: String? =
        savedStateHandle.get<String>("seerrBackdrop")?.takeIf { it.isNotBlank() }
            ?: savedStateHandle.get<String>("seerrPoster")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(SeerrMediaDetailUiState(isLoading = true))
    val uiState: StateFlow<SeerrMediaDetailUiState> = _uiState.asStateFlow()

    private var watchlistWriteInFlight = false

    init {
        load()
        observeRequestEvents()
        observeWatchlistEvents()
    }

    fun toggleWatchlist() {
        val details = _uiState.value.details ?: return
        if (watchlistWriteInFlight) return
        val target = !_uiState.value.isOnWatchlist
        watchlistWriteInFlight = true
        _uiState.update { it.copy(isOnWatchlist = target) }
        viewModelScope.launch {
            val result =
                if (target) {
                    jellyseerrRepository.addToWatchlist(
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        title = details.title ?: details.name ?: "",
                    )
                } else {
                    jellyseerrRepository.removeFromWatchlist(tmdbId, mediaType)
                }
            watchlistWriteInFlight = false
            result
                .onSuccess {
                    seerrWatchlistMirror.onSeerrWatchlistChanged(
                        jellyfinItemId = details.mediaInfo?.getJellyfinItemId(),
                        onWatchlist = target,
                    )
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to update Seerr watchlist for $tmdbId, reverted UI")
                    _uiState.update { it.copy(isOnWatchlist = !target) }
                }
        }
    }

    private fun observeWatchlistEvents() {
        viewModelScope.launch {
            jellyseerrRepository.watchlistEvents.collect { event ->
                if (
                    !watchlistWriteInFlight &&
                        event.tmdbId == tmdbId &&
                        event.mediaType == mediaType
                ) {
                    _uiState.update { it.copy(isOnWatchlist = event.onWatchlist) }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            launch { loadLogo() }
            val result =
                if (mediaType == MediaType.TV) jellyseerrRepository.getTvDetails(tmdbId)
                else jellyseerrRepository.getMovieDetails(tmdbId)
            result.fold(
                onSuccess = { details ->
                    jellyseerrRepository.rememberWatchlistArtwork(details, mediaType)
                    _uiState.update {
                        it.copy(
                            details = details,
                            isLoading = false,
                            isOnWatchlist =
                                if (watchlistWriteInFlight) it.isOnWatchlist
                                else details.onUserWatchlist == true,
                        )
                    }
                    details.collection?.let { collection ->
                        launch {
                            jellyseerrRepository.getCollection(collection.id).onSuccess { full ->
                                _uiState.update {
                                    it.copy(
                                        collectionName = full.name,
                                        collectionParts =
                                            full.parts.filter { part -> part.id != tmdbId },
                                    )
                                }
                            }
                        }
                    }
                    launch {
                        jellyseerrRepository.getRecommendations(mediaType, tmdbId).onSuccess { res
                            ->
                            _uiState.update {
                                it.copy(
                                    recommendations =
                                        res.results
                                            .filter { item -> item.getMediaType() != null }
                                            .take(15)
                                )
                            }
                        }
                    }
                    launch {
                        jellyseerrRepository.getSimilar(mediaType, tmdbId).onSuccess { res ->
                            _uiState.update {
                                it.copy(
                                    similar =
                                        res.results
                                            .filter { item -> item.getMediaType() != null }
                                            .take(15)
                                )
                            }
                        }
                    }
                    launch {
                        jellyseerrRepository.getRatings(mediaType, tmdbId).onSuccess { ratings ->
                            _uiState.update { it.copy(ratings = ratings) }
                        }
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load Seerr details for $tmdbId")
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    private suspend fun loadLogo() {
        if (_uiState.value.logoUrl != null) return
        try {
            val session = sessionManager.currentSession.value ?: return
            val tmdbKey =
                securePreferencesRepository.getTmdbApiKey(
                    session.serverId,
                    session.userId.toString(),
                )
            if (tmdbKey.isNullOrBlank()) return
            val images =
                if (mediaType == MediaType.TV)
                    tmdbApiService.getSeriesImages(tmdbId.toString(), tmdbKey)
                else tmdbApiService.getMovieImages(tmdbId.toString(), tmdbKey)
            val logoPath =
                images.logos
                    .filter { it.file_path?.endsWith(".png") == true }
                    .sortedWith(
                        compareByDescending<TmdbImage> { it.iso_639_1 == "en" }
                            .thenByDescending { it.vote_average ?: 0.0 }
                    )
                    .firstOrNull()
                    ?.file_path ?: return
            _uiState.update { it.copy(logoUrl = "https://image.tmdb.org/t/p/w342$logoPath") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load TMDB logo for $tmdbId")
        }
    }

    private fun observeRequestEvents() {
        viewModelScope.launch {
            jellyseerrRepository.requestEvents.collect { event ->
                if (event.request.media.tmdbId == tmdbId) {
                    load()
                }
            }
        }
    }
}

data class SeerrMediaDetailUiState(
    val details: MediaDetails? = null,
    val collectionName: String? = null,
    val collectionParts: List<SearchResultItem> = emptyList(),
    val recommendations: List<SearchResultItem> = emptyList(),
    val similar: List<SearchResultItem> = emptyList(),
    val ratings: RatingsCombined? = null,
    val logoUrl: String? = null,
    val isOnWatchlist: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
