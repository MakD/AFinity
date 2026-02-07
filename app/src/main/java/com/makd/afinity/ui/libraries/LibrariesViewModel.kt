package com.makd.afinity.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LibrariesViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appDataRepository: AppDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadLibraries(forceRefresh = true)
                } else {
                    _uiState.value = LibrariesUiState()
                }
            }
        }
    }

    private fun loadLibraries(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val libraries = jellyfinRepository.getLibraries()
                _uiState.value =
                    _uiState.value.copy(libraries = libraries, isLoading = false, error = null)

                Timber.d("Successfully loaded ${libraries.size} libraries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load libraries")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load libraries: ${e.message}",
                    )
            }
        }
    }

    fun onLibraryClick(library: AfinityCollection) {
        Timber.d("Library clicked: ${library.name} (${library.type})")
    }
}

data class LibrariesUiState(
    val libraries: List<AfinityCollection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
