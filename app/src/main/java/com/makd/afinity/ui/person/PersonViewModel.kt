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

    private val personId: UUID =
        UUID.fromString(
            savedStateHandle.get<String>("personId")
                ?: throw IllegalArgumentException("personId is required")
        )

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadPersonDetails()
                } else {
                    _uiState.value = PersonUiState()
                }
            }
        }
    }

    private fun loadPersonDetails() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val person = jellyfinRepository.getPersonDetail(personId)
                if (person == null) {
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, error = "Person not found")
                    return@launch
                }

                val personItems =
                    jellyfinRepository.getPersonItems(
                        personId = personId,
                        includeItemTypes = listOf("Movie", "Series"),
                    )

                val movies = personItems.filterIsInstance<AfinityMovie>()
                val shows = personItems.filterIsInstance<AfinityShow>()

                _uiState.value =
                    _uiState.value.copy(
                        person = person,
                        movies = movies,
                        shows = shows,
                        isLoading = false,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load person details: $personId")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load person details: ${e.message}",
                    )
            }
        }
    }

    fun toggleFavorite() {
        val person = _uiState.value.person ?: return

        viewModelScope.launch {
            try {
                val newStatus = !person.favorite
                val updatedPerson = person.copy(favorite = newStatus)
                _uiState.value = _uiState.value.copy(person = updatedPerson)
                val success =
                    if (newStatus) {
                        userDataRepository.addToFavorites(person.id)
                    } else {
                        userDataRepository.removeFromFavorites(person.id)
                    }
                if (!success) {
                    _uiState.value = _uiState.value.copy(person = person)
                    Timber.e("Failed to toggle favorite for person: ${person.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
                _uiState.value = _uiState.value.copy(person = person)
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
