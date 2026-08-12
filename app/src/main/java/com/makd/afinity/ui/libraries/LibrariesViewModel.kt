package com.makd.afinity.ui.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.repository.AppDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibrariesViewModel
@Inject
constructor(
    private val appDataRepository: AppDataRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val serverOrder =
                sessionManager.currentSession.map {
                    it?.userConfiguration?.orderedViews?.isNotEmpty() == true
                }
            combine(appDataRepository.libraries, serverOrder) { libraries, hasServerOrder ->
                    LibrariesUiState(
                        libraries = libraries,
                        hasServerOrder = hasServerOrder,
                        isLoading = false,
                        error = null,
                    )
                }
                .collect { state -> _uiState.value = state }
        }
    }

    fun onLibraryClick(library: AfinityCollection) {
        Timber.d("Library clicked: ${library.name} (${library.type})")
    }
}

data class LibrariesUiState(
    val libraries: List<AfinityCollection> = emptyList(),
    val hasServerOrder: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
