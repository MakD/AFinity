package com.makd.afinity.ui.person

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class PersonViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val userDataRepository: UserDataRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personIdStr = savedStateHandle.get<String>("personId")
    private val personId: UUID? =
        try {
            personIdStr?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            Timber.e(e, "Invalid or missing personId: $personIdStr")
            null
        }

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    private val pendingItemUpdates = mutableMapOf<UUID, AfinityItem>()
    private val personListUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastLoadedAt = 0L

    init {
        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect { loadPersonDetails() }
        }

        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadPersonDetails()
                } else {
                    _uiState.update { PersonUiState() }
                }
            }
        }
        viewModelScope.launch {
            personListUpdateTrigger.debounce(300L).collect {
                if (pendingItemUpdates.isEmpty()) return@collect

                val currentMovies = _uiState.value.movies
                val currentShows = _uiState.value.shows
                var hasChanges = false

                val newMovies = currentMovies.map { movie ->
                    pendingItemUpdates[movie.id]?.also { hasChanges = true } as? AfinityMovie
                        ?: movie
                }

                val newShows = currentShows.map { show ->
                    pendingItemUpdates[show.id]?.also { hasChanges = true } as? AfinityShow ?: show
                }

                if (hasChanges) {
                    _uiState.update { it.copy(movies = newMovies, shows = newShows) }
                    Timber.d("Applied batched updates to Person screen lists")
                }
                pendingItemUpdates.clear()
            }
        }
        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val currentMovieIds = _uiState.value.movies.map { it.id }
                val currentShowIds = _uiState.value.shows.map { it.id }

                val targetIdToFetch =
                    when {
                        currentMovieIds.contains(event.itemId) -> event.itemId
                        currentShowIds.contains(event.itemId) -> event.itemId
                        event.seriesId != null && currentShowIds.contains(event.seriesId) ->
                            event.seriesId
                        else -> null
                    }

                if (targetIdToFetch != null) {
                    try {
                        val freshItem = mediaRepository.getItemById(targetIdToFetch)
                        freshItem?.let { item ->
                            pendingItemUpdates[item.id] = item
                            personListUpdateTrigger.tryEmit(Unit)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to resolve item for granular update: $targetIdToFetch")
                    }
                }
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastLoadedAt) {
            lastLoadedAt = System.currentTimeMillis()
        }
    }

    private fun loadPersonDetails() {
        if (personId == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = context.getString(R.string.error_invalid_person_id),
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val person = mediaRepository.getPerson(personId)
                if (person == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = context.getString(R.string.error_person_not_found),
                        )
                    }
                    return@launch
                }

                val personItems =
                    mediaRepository.getPersonItems(
                        personId = personId,
                        includeItemTypes = listOf("Movie", "Series"),
                    )

                val movies = personItems.filterIsInstance<AfinityMovie>()
                val shows = personItems.filterIsInstance<AfinityShow>()

                _uiState.update { currentState ->
                    currentState.copy(
                        person = person,
                        movies = movies,
                        shows = shows,
                        isLoading = false,
                    )
                }
                lastLoadedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load person details: $personId")
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error =
                            context.getString(
                                R.string.error_failed_load_person_fmt,
                                e.message ?: "",
                            ),
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val currentPerson = _uiState.value.person ?: return
        val targetStatus = !currentPerson.favorite
        _uiState.update { currentState ->
            currentState.copy(person = currentState.person?.copy(favorite = targetStatus))
        }

        viewModelScope.launch {
            try {
                val success =
                    if (targetStatus) {
                        userDataRepository.addToFavorites(currentPerson.id)
                    } else {
                        userDataRepository.removeFromFavorites(currentPerson.id)
                    }

                if (!success) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            person = currentState.person?.copy(favorite = !targetStatus)
                        )
                    }
                    Timber.e("Failed to toggle favorite for person: ${currentPerson.name}")
                } else {
                    appDataRepository.reloadFavorites()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
                _uiState.update { currentState ->
                    currentState.copy(person = currentState.person?.copy(favorite = !targetStatus))
                }
            }
        }
    }

    fun retry() {
        loadPersonDetails()
    }
}

data class PersonUiState(
    val person: AfinityPersonDetail? = null,
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
