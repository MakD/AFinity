package com.makd.afinity.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibrariesViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    private var lastLoadTime = 0L
    private val cacheValidDuration = 2 * 60 * 1000L

    init {
        loadLibraries()
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLoadTime < cacheValidDuration && _uiState.value.libraries.isNotEmpty()) {
                Timber.d("Using cached libraries data")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val libraries = jellyfinRepository.getLibraries()

                val userProfileImageUrl = loadUserProfileImage()

                _uiState.value = _uiState.value.copy(
                    libraries = libraries,
                    isLoading = false,
                    error = null,
                    userProfileImageUrl = userProfileImageUrl
                )

                lastLoadTime = currentTime
                Timber.d("Successfully loaded ${libraries.size} libraries")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load libraries")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load libraries: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadUserProfileImage(): String? {
        return try {
            jellyfinRepository.getUserProfileImageUrl()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
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
    val userProfileImageUrl: String? = null
)