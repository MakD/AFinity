package com.makd.afinity.ui.admin.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfo

data class TaskDetailUiState(
    val task: TaskInfo? = null,
    val failed: Boolean = false,
    val missing: Boolean = false,
    val isSavingTriggers: Boolean = false,
    val isRunCommandPending: Boolean = false,
)

@HiltViewModel
class TaskDetailViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TASK_REFRESH_DELAY_MS = 500L
        private const val TRIGGER_CONFIRM_TIMEOUT_MS = 5_000L
    }

    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()

    private val isSavingTriggers = MutableStateFlow(false)
    private val isRunCommandPending = MutableStateFlow(false)

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val state: StateFlow<TaskDetailUiState> =
        combine(adminRepository.scheduledTasks, isSavingTriggers, isRunCommandPending) {
                result,
                saving,
                pending ->
                val tasks = result?.getOrNull()
                val task = tasks.findTask(taskId)
                TaskDetailUiState(
                    task = task,
                    failed = result?.isFailure == true,
                    missing = tasks != null && task == null,
                    isSavingTriggers = saving,
                    isRunCommandPending = pending,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                TaskDetailUiState(task = currentTask()),
            )

    fun retry() {
        adminRepository.refreshTasks()
    }

    fun run() {
        sendRunCommand { adminRepository.startTask(taskId) }
    }

    fun stop() {
        sendRunCommand { adminRepository.stopTask(taskId) }
    }

    fun addTrigger(trigger: TaskTriggerInfo) {
        updateTriggers { current -> if (trigger in current) current else current + trigger }
    }

    fun replaceTrigger(old: TaskTriggerInfo, new: TaskTriggerInfo) {
        if (old !in currentTask()?.triggers.orEmpty()) {
            _commandError.tryEmit(Unit)
            return
        }
        updateTriggers { current ->
            val index = current.indexOf(old)
            when {
                index < 0 -> current
                new != old && new in current -> current - old
                else -> current.toMutableList().also { it[index] = new }
            }
        }
    }

    fun removeTrigger(trigger: TaskTriggerInfo) {
        updateTriggers { current -> current - trigger }
    }

    private fun currentTask(): TaskInfo? =
        adminRepository.scheduledTasks.value?.getOrNull().findTask(taskId)

    private fun List<TaskInfo>?.findTask(id: String): TaskInfo? = this?.firstOrNull { it.id == id }

    private fun sendRunCommand(block: suspend () -> Result<Unit>) {
        if (isRunCommandPending.value) return
        isRunCommandPending.value = true
        viewModelScope.launch {
            try {
                if (block().isSuccess) {
                    delay(TASK_REFRESH_DELAY_MS.milliseconds)
                } else {
                    _commandError.tryEmit(Unit)
                }
                adminRepository.refreshTasks()
            } finally {
                isRunCommandPending.value = false
            }
        }
    }

    private fun updateTriggers(transform: (List<TaskTriggerInfo>) -> List<TaskTriggerInfo>) {
        if (isSavingTriggers.value) return
        val task = currentTask() ?: return
        val current = task.triggers.orEmpty()
        val updated = transform(current)
        if (updated == current) return
        isSavingTriggers.value = true
        viewModelScope.launch {
            try {
                val saved = adminRepository.updateTaskTriggers(taskId, updated).isSuccess
                if (!saved) _commandError.tryEmit(Unit)
                adminRepository.refreshTasks()
                if (saved) {
                    withTimeoutOrNull(TRIGGER_CONFIRM_TIMEOUT_MS.milliseconds) {
                        adminRepository.scheduledTasks.first { result ->
                            result?.getOrNull().findTask(taskId)?.triggers.orEmpty() == updated
                        }
                    }
                }
            } finally {
                isSavingTriggers.value = false
            }
        }
    }
}
