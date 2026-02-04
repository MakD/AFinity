package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.ItemWithProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfLibrariesViewModel @Inject constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookshelfLibrariesUiState())
    val uiState: StateFlow<AudiobookshelfLibrariesUiState> = _uiState.asStateFlow()

    val libraries: StateFlow<List<Library>> = audiobookshelfRepository.getLibrariesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgressItems: StateFlow<List<ItemWithProgress>> =
        audiobookshelfRepository.getInProgressItemsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isAuthenticated = audiobookshelfRepository.isAuthenticated

    init {
        refreshLibraries()
    }

    fun refreshLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            val result = audiobookshelfRepository.refreshLibraries()

            result.fold(
                onSuccess = { libraries ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false
                    )
                    Timber.d("Refreshed ${libraries.size} libraries")

                    audiobookshelfRepository.refreshProgress()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message
                    )
                    Timber.e(error, "Failed to refresh libraries")
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AudiobookshelfLibrariesUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null
)
