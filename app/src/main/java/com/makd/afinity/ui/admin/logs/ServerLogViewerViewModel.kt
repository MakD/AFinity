package com.makd.afinity.ui.admin.logs

import androidx.lifecycle.SavedStateHandle
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
import timber.log.Timber

data class ServerLogViewerUiState(
    val lines: List<String>? = null,
    val totalLines: Int = 0,
    val failed: Boolean = false,
)

@HiltViewModel
class ServerLogViewerViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val MAX_LINES = 2_000
    }

    val fileName: String = savedStateHandle.get<String>("name").orEmpty()

    private val _state = MutableStateFlow(ServerLogViewerUiState())
    val state: StateFlow<ServerLogViewerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            adminRepository
                .getLogTail(fileName, MAX_LINES)
                .onSuccess { tail ->
                    _state.value =
                        ServerLogViewerUiState(lines = tail.lines, totalLines = tail.totalLines)
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load log file")
                    _state.update { it.copy(failed = it.lines == null) }
                }
        }
    }
}
