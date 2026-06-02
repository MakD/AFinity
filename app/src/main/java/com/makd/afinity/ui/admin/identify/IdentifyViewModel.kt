package com.makd.afinity.ui.admin.identify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentifyUiState(
    val searchName: String = "",
    val year: String = "",
    val providers: List<ExternalIdProvider> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val results: List<IdentifyResult> = emptyList(),
    val searching: Boolean = false,
    val applying: Boolean = false,
    val applied: Boolean = false,
    val replaceAllImages: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class IdentifyViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])
    val itemType: String = savedStateHandle["itemType"] ?: "Movie"

    private val _uiState = MutableStateFlow(IdentifyUiState())
    val uiState: StateFlow<IdentifyUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        viewModelScope.launch {
            val providers = adminRepository.getExternalIdProviders(itemId)
            _uiState.update { it.copy(providers = providers) }
        }
    }

    fun updateSearchName(name: String) = _uiState.update { it.copy(searchName = name) }

    fun updateYear(year: String) = _uiState.update { it.copy(year = year) }

    fun updateProviderId(key: String, value: String) =
        _uiState.update { it.copy(providerIds = it.providerIds + (key to value)) }

    fun toggleReplaceImages() = _uiState.update { it.copy(replaceAllImages = !it.replaceAllImages) }

    fun search() {
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, results = emptyList(), error = null) }
            val state = _uiState.value
            val year = state.year.toIntOrNull()
            val results = when (itemType) {
                "Series" -> adminRepository.searchSeries(itemId, state.searchName, year, state.providerIds)
                else -> adminRepository.searchMovie(itemId, state.searchName, year, state.providerIds)
            }
            _uiState.update { it.copy(searching = false, results = results) }
        }
    }

    fun applyResult(result: IdentifyResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, error = null) }
            val res = adminRepository.applyIdentifyResult(
                itemId = itemId,
                result = result,
                replaceAllImages = _uiState.value.replaceAllImages,
            )
            _uiState.update {
                it.copy(
                    applying = false,
                    applied = res.isSuccess,
                    error = res.exceptionOrNull()?.message,
                )
            }
        }
    }
}