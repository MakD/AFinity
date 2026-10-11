package com.makd.afinity.ui.settings.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.TaskInfo

@HiltViewModel
class ScheduledTasksViewModel
@Inject
constructor(private val adminRepository: ServerAdminRepository) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TASK_REFRESH_DELAY_MS = 500L
    }

    private val _pendingTaskCommands = MutableStateFlow<Set<String>>(emptySet())
    val pendingTaskCommands: StateFlow<Set<String>> = _pendingTaskCommands.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val tasks: StateFlow<List<TaskInfo>?> =
        adminRepository.scheduledTasks
            .map { it?.getOrNull() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                adminRepository.scheduledTasks.value?.getOrNull(),
            )

    val tasksFailed: StateFlow<Boolean> =
        adminRepository.scheduledTasks
            .map { it?.isFailure == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun retry() {
        adminRepository.refresh()
    }

    fun runTask(taskId: String) {
        sendTaskCommand(taskId) { adminRepository.startTask(taskId) }
    }

    fun stopTask(taskId: String) {
        sendTaskCommand(taskId) { adminRepository.stopTask(taskId) }
    }

    private fun sendTaskCommand(taskId: String, block: suspend () -> Result<Unit>) {
        if (taskId in _pendingTaskCommands.value) return
        _pendingTaskCommands.update { it + taskId }
        viewModelScope.launch {
            try {
                if (block().isSuccess) {
                    delay(TASK_REFRESH_DELAY_MS)
                } else {
                    _commandError.tryEmit(Unit)
                }
                adminRepository.refresh()
            } finally {
                _pendingTaskCommands.update { it - taskId }
            }
        }
    }
}
