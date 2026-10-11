package com.makd.afinity.ui.admin.devices

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

data class AdminDeviceRow(
    val id: String,
    val name: String,
    val app: String,
    val lastUser: String?,
    val lastActivity: LocalDateTime?,
    val isThisDevice: Boolean,
)

data class AdminDevices(val recent: List<AdminDeviceRow>, val idle: List<AdminDeviceRow>)

data class DevicesUiState(
    val devices: AdminDevices? = null,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(private val adminRepository: ServerAdminRepository) :
    ViewModel() {

    companion object {
        private const val IDLE_AFTER_DAYS = 30L
    }

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun rename(id: String, name: String) {
        runCommand { adminRepository.renameDevice(id, name) }
    }

    fun signOut(id: String) {
        signOutAll(listOf(id))
    }

    fun signOutIdle() {
        signOutAll(_state.value.devices?.idle.orEmpty().map { it.id })
    }

    private fun signOutAll(ids: List<String>) {
        val ownIds =
            _state.value.devices
                ?.let { it.recent + it.idle }
                .orEmpty()
                .filter { it.isThisDevice }
                .map { it.id }
        val targets = ids - ownIds.toSet()
        if (targets.isEmpty()) return
        runCommand { adminRepository.signOutDevices(targets) }
    }

    private fun runCommand(block: suspend () -> Result<Unit>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (block().isFailure) _commandError.tryEmit(Unit)
                fetch()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getDevices()
            .onSuccess { devices ->
                val ownId = adminRepository.findOwnDeviceId(devices)
                val idleBefore = LocalDateTime.now().minusDays(IDLE_AFTER_DAYS)
                val rows =
                    devices
                        .mapNotNull { device ->
                            val id = device.id ?: return@mapNotNull null
                            AdminDeviceRow(
                                id = id,
                                name = device.customName ?: device.name.orEmpty(),
                                app =
                                    listOfNotNull(device.appName, device.appVersion)
                                        .joinToString(" "),
                                lastUser = device.lastUserName,
                                lastActivity = device.dateLastActivity,
                                isThisDevice = id == ownId,
                            )
                        }
                        .sortedWith(compareByDescending<AdminDeviceRow> { it.lastActivity })
                val (recent, idle) =
                    rows.partition { row ->
                        row.isThisDevice || row.lastActivity?.isAfter(idleBefore) == true
                    }
                _state.update { it.copy(devices = AdminDevices(recent, idle), failed = false) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load devices")
                _state.update { it.copy(failed = it.devices == null) }
            }
    }
}
