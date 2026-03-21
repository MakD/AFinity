package com.makd.afinity.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.repository.AppDataRepository
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
    private val appDataRepository: AppDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appDataRepository.libraries.collect { libraries ->
                _uiState.value = LibrariesUiState(
                    libraries = libraries,
                    isLoading = false,
                    error = null,
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