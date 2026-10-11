package com.makd.afinity.ui.admin.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminApiKeyRow(
    val rowId: String,
    val appName: String,
    val created: LocalDateTime,
    val token: String,
) {
    override fun toString(): String = "AdminApiKeyRow(rowId=$rowId)"
}

data class ApiKeysUiState(
    val keys: List<AdminApiKeyRow>? = null,
    val revealedRowIds: Set<String> = emptySet(),
    val failed: Boolean = false,
    val isBusy: Boolean = false,
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(private val adminRepository: ServerAdminRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(ApiKeysUiState())
    val state: StateFlow<ApiKeysUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun toggleRevealed(rowId: String) {
        _state.update { state ->
            val revealed = state.revealedRowIds
            val toggled = if (rowId in revealed) revealed - rowId else revealed + rowId
            state.copy(revealedRowIds = toggled)
        }
    }

    fun create(appName: String) {
        if (appName.isBlank()) return
        runCommand {
            val tokensBefore = _state.value.keys.orEmpty().map { it.token }.toSet()
            val result = adminRepository.createApiKey(appName)
            fetch()
            if (result.isSuccess) {
                val created =
                    _state.value.keys
                        .orEmpty()
                        .filter { it.token !in tokensBefore }
                        .map { it.rowId }
                _state.update { it.copy(revealedRowIds = it.revealedRowIds + created) }
            }
            result
        }
    }

    fun revoke(rowId: String) {
        val token = _state.value.keys?.firstOrNull { it.rowId == rowId }?.token ?: return
        runCommand {
            val result = adminRepository.revokeApiKey(token)
            fetch()
            result
        }
    }

    private fun runCommand(block: suspend () -> Result<Unit>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (block().isFailure) _commandError.tryEmit(Unit)
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getApiKeys()
            .onSuccess { keys ->
                val rows =
                    keys
                        .mapNotNull { key ->
                            val token = key.accessToken?.takeIf { it.isNotEmpty() }
                            if (token == null) {
                                null
                            } else {
                                AdminApiKeyRow(
                                    rowId = "${key.dateCreated}|${key.appName.orEmpty()}",
                                    appName = key.appName.orEmpty(),
                                    created = key.dateCreated,
                                    token = token,
                                )
                            }
                        }
                        .sortedWith(compareByDescending<AdminApiKeyRow> { it.created })
                _state.update { state ->
                    val rowIds = rows.map { it.rowId }.toSet()
                    state.copy(
                        keys = rows,
                        revealedRowIds = state.revealedRowIds intersect rowIds,
                        failed = false,
                    )
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load api keys")
                _state.update { it.copy(failed = it.keys == null) }
            }
    }
}
