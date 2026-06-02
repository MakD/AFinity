package com.makd.afinity.ui.admin.refresh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RefreshMode { Default, Validate, Full }

data class RefreshUiState(
    val mode: RefreshMode = RefreshMode.Default,
    val replaceImages: Boolean = false,
    val replaceMetadata: Boolean = false,
    val refreshing: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RefreshMetadataViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(RefreshUiState())
    val uiState: StateFlow<RefreshUiState> = _uiState.asStateFlow()

    fun setMode(mode: RefreshMode) = _uiState.update { it.copy(mode = mode) }

    fun toggleReplaceImages() = _uiState.update { it.copy(replaceImages = !it.replaceImages) }

    fun toggleReplaceMetadata() = _uiState.update { it.copy(replaceMetadata = !it.replaceMetadata) }

    fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            val (metaMode, imageMode, replaceMeta, replaceImages) = when (state.mode) {
                RefreshMode.Default -> RefreshParams("Default", "Default", false, false)
                RefreshMode.Validate -> RefreshParams("ValidationOnly", "ValidationOnly", false, state.replaceImages)
                RefreshMode.Full -> RefreshParams("FullRefresh", "FullRefresh", state.replaceMetadata, state.replaceImages)
            }
            val result = adminRepository.refreshItem(
                itemId = itemId,
                metadataRefreshMode = metaMode,
                imageRefreshMode = imageMode,
                replaceAllMetadata = replaceMeta,
                replaceAllImages = replaceImages,
            )
            _uiState.update {
                it.copy(
                    refreshing = false,
                    done = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private data class RefreshParams(
        val metaMode: String,
        val imageMode: String,
        val replaceMeta: Boolean,
        val replaceImages: Boolean,
    )
}