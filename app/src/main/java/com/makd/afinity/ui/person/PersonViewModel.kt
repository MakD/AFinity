package com.makd.afinity.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: UUID = UUID.fromString(
        savedStateHandle.get<String>("personId") ?: throw IllegalArgumentException("personId is required")
    )

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        loadPersonDetails()
    }

    private fun loadPersonDetails() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val person = jellyfinRepository.getPersonDetail(personId)
                if (person == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Person not found"
                    )
                    return@launch
                }

                val personItems = jellyfinRepository.getPersonItems(
                    personId = personId,
                    includeItemTypes = listOf("Movie", "Series")
                )

                val movies = personItems.filterIsInstance<AfinityMovie>()
                val shows = personItems.filterIsInstance<AfinityShow>()

                _uiState.value = _uiState.value.copy(
                    person = person,
                    movies = movies,
                    shows = shows,
                    isLoading = false
                )

            } catch (e: Exception) {
                Timber.e(e, "Failed to load person details: $personId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load person details: ${e.message}"
                )
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
    val error: String? = null
)