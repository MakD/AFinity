package com.makd.afinity.ui.franchise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.franchise.FranchiseRepository
import com.makd.afinity.data.store.ItemStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class FranchiseViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val franchiseRepository: FranchiseRepository,
    private val itemStore: ItemStore,
) : ViewModel() {

    private val franchiseId: String = savedStateHandle["franchiseId"] ?: ""

    private val _uiState = MutableStateFlow(FranchiseUiState())
    val uiState: StateFlow<FranchiseUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val items = live(state.items)
            if (items === state.items) state else state.copy(items = items)
        }

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                val franchise = franchiseRepository.franchise(franchiseId)
                val items = franchise?.let { franchiseRepository.items(it) }.orEmpty()
                _uiState.update {
                    it.copy(
                        name = franchise?.name.orEmpty(),
                        items = items,
                        isLoading = false,
                        hasError = items.isEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load franchise $franchiseId")
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }
}

data class FranchiseUiState(
    val name: String = "",
    val items: List<AfinityItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)
