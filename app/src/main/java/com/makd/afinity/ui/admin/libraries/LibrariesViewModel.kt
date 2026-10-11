package com.makd.afinity.ui.admin.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import timber.log.Timber

enum class LibraryScanState {
    IDLE,
    QUEUED,
    ACTIVE,
}

data class AdminLibraryRow(
    val key: String,
    val name: String,
    val type: CollectionTypeOptions?,
    val locations: List<String>,
    val scanState: LibraryScanState,
)

data class LibrariesUiState(
    val libraries: List<AdminLibraryRow>? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class LibrariesViewModel @Inject constructor(private val adminRepository: ServerAdminRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(LibrariesUiState())
    val state: StateFlow<LibrariesUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            adminRepository
                .getLibraries()
                .onSuccess { folders ->
                    val rows = folders.mapIndexed { index, folder ->
                        AdminLibraryRow(
                            key = folder.itemId ?: "library_$index",
                            name = folder.name.orEmpty(),
                            type = folder.collectionType,
                            locations = folder.locations.orEmpty(),
                            scanState =
                                when (folder.refreshStatus) {
                                    "Active" -> LibraryScanState.ACTIVE
                                    "Queued" -> LibraryScanState.QUEUED
                                    else -> LibraryScanState.IDLE
                                },
                        )
                    }
                    _state.value = LibrariesUiState(libraries = rows)
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load libraries")
                    _state.update { it.copy(failed = it.libraries == null) }
                }
        }
    }
}
