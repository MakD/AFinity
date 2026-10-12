package com.makd.afinity.ui.admin.backups

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminBackupRow(
    val path: String,
    val created: LocalDateTime,
    val hasDatabase: Boolean,
    val hasMetadata: Boolean,
    val hasSubtitles: Boolean,
    val hasTrickplay: Boolean,
)

data class ServerBackupsUiState(
    val backups: List<AdminBackupRow>? = null,
    val failed: Boolean = false,
    val isCreating: Boolean = false,
)

private data class LoadState(val backups: List<AdminBackupRow>? = null, val failed: Boolean = false)

@HiltViewModel
class ServerBackupsViewModel
@Inject
constructor(private val adminRepository: ServerAdminRepository) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }

    private val loadState = MutableStateFlow(LoadState())

    private val _backupUnconfirmed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val backupUnconfirmed: SharedFlow<Unit> = _backupUnconfirmed.asSharedFlow()

    val state: StateFlow<ServerBackupsUiState> =
        combine(loadState, adminRepository.isCreatingBackup) { loaded, creating ->
                ServerBackupsUiState(
                    backups = loaded.backups,
                    failed = loaded.failed,
                    isCreating = creating,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ServerBackupsUiState(isCreating = adminRepository.isCreatingBackup.value),
            )

    init {
        load()
        viewModelScope.launch {
            adminRepository.isCreatingBackup
                .drop(1)
                .filter { creating -> !creating }
                .collect { fetch() }
        }
    }

    fun load() {
        loadState.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun create(metadata: Boolean, subtitles: Boolean, trickplay: Boolean) {
        if (adminRepository.isCreatingBackup.value) return
        viewModelScope.launch {
            if (adminRepository.createBackup(metadata, subtitles, trickplay).isFailure) {
                _backupUnconfirmed.tryEmit(Unit)
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getBackups()
            .onSuccess { backups ->
                val rows =
                    backups
                        .map { backup ->
                            AdminBackupRow(
                                path = backup.path,
                                created = backup.dateCreated,
                                hasDatabase = backup.options.database,
                                hasMetadata = backup.options.metadata,
                                hasSubtitles = backup.options.subtitles,
                                hasTrickplay = backup.options.trickplay,
                            )
                        }
                        .sortedByDescending { it.created }
                loadState.value = LoadState(backups = rows)
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load backups")
                loadState.update { it.copy(failed = it.backups == null) }
            }
    }
}
