package com.makd.afinity.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appDataRepository: AppDataRepository,
    private val userDataRepository: UserDataRepository,
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

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadPersonDetails()
                } else {
                    _uiState.update { PersonUiState() }
                }
            }
        }
    }

    private fun loadPersonDetails() {
        if (personId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid or missing Person ID") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val person = jellyfinRepository.getPersonDetail(personId)
                if (person == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Person not found") }
                    return@launch
                }

                val personItems =
                    jellyfinRepository.getPersonItems(
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to load person details: $personId")
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Failed to load person details: ${e.message}",
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
