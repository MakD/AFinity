package com.makd.afinity.ui.admin.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.ServerConfigSource
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.ui.admin.config.ConfigFormEditor
import com.makd.afinity.ui.admin.config.LoadedConfigForm
import com.makd.afinity.ui.admin.config.SERVER_LOGS_FORM
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class ServerLogFileRow(val name: String, val size: Long, val modified: LocalDateTime)

data class ServerLogsUiState(
    val files: List<ServerLogFileRow>? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class ServerLogsViewModel @Inject constructor(private val adminRepository: ServerAdminRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(ServerLogsUiState())
    val state: StateFlow<ServerLogsUiState> = _state.asStateFlow()

    val settingsEditor =
        ConfigFormEditor(
            scope = viewModelScope,
            loadForm = {
                adminRepository.getServerConfig(ServerConfigSource.SYSTEM).map { document ->
                    LoadedConfigForm(spec = SERVER_LOGS_FORM, document = document)
                }
            },
            saveChanges = { changes ->
                adminRepository.updateServerConfig(ServerConfigSource.SYSTEM, changes)
            },
            initialSpec = SERVER_LOGS_FORM,
        )

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            adminRepository
                .getLogFiles()
                .onSuccess { files ->
                    val rows = files.map { ServerLogFileRow(it.name, it.size, it.dateModified) }
                    _state.value = ServerLogsUiState(files = rows)
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load log files")
                    _state.update { it.copy(failed = it.files == null) }
                }
        }
    }
}
