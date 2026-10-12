package com.makd.afinity.ui.admin.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.util.JellyfinImageUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.DeviceInfoDto
import timber.log.Timber

data class DevicesUiState(
    val groups: List<AdminDeviceGroup>? = null,
    val filter: DeviceFilter = DeviceFilter.ALL,
    val totalCount: Int = 0,
    val weekCount: Int = 0,
    val idleCount: Int = 0,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
)

@HiltViewModel
class DevicesViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    private val imageUrlBuilder: JellyfinImageUrlBuilder,
) : ViewModel() {

    companion object {
        private const val IDLE_AFTER_DAYS = 30L
        private const val WEEK_DAYS = 7L
        private const val USER_AVATAR_MAX_WIDTH = 96
        private const val OWN_APP_NAME = "AFinity"
    }

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private var allGroups: List<AdminDeviceGroup>? = null
    private var userImageUrls: Map<UUID, String> = emptyMap()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun setFilter(filter: DeviceFilter) {
        _state.update { it.copy(filter = filter) }
        publish()
    }

    fun rename(groupKey: String, name: String) {
        val ids = allGroups.orEmpty().firstOrNull { it.key == groupKey }?.entries?.map { it.id }
        if (ids.isNullOrEmpty()) return
        runCommand {
            var result = Result.success(Unit)
            for (id in ids) {
                result = adminRepository.renameDevice(id, name)
                if (result.isFailure) break
            }
            result
        }
    }

    fun signOut(id: String) {
        signOutAll(listOf(id))
    }

    fun signOutGroup(groupKey: String) {
        val group = allGroups.orEmpty().firstOrNull { it.key == groupKey } ?: return
        signOutAll(group.removableIds)
    }

    fun signOutIdle() {
        val now = LocalDateTime.now()
        val idle =
            allGroups.orEmpty().filter {
                it.matches(
                    DeviceFilter.IDLE,
                    now.minusDays(WEEK_DAYS),
                    now.minusDays(IDLE_AFTER_DAYS),
                )
            }
        signOutAll(idle.flatMap { it.removableIds })
    }

    private fun signOutAll(ids: List<String>) {
        val ownIds =
            allGroups
                .orEmpty()
                .flatMap { it.entries }
                .filter { it.isThisDevice }
                .map { it.id }
                .toSet()
        val targets = ids.filterNot { it in ownIds }
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
                loadUserImages()
                allGroups = buildDeviceGroups(devices.toRecords(), userImageUrls)
                publish()
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load devices")
                _state.update { it.copy(failed = it.groups == null) }
            }
    }

    private suspend fun loadUserImages() {
        val baseUrl = sessionManager.currentSession.value?.serverUrl ?: return
        adminRepository.getUsers().onSuccess { users ->
            userImageUrls =
                users
                    .mapNotNull { user ->
                        val tag = user.primaryImageTag ?: return@mapNotNull null
                        user.id to
                            imageUrlBuilder.buildUserPrimaryImageUrl(
                                baseUrl = baseUrl,
                                userId = user.id.toString(),
                                tag = tag,
                                maxWidth = USER_AVATAR_MAX_WIDTH,
                            )
                    }
                    .toMap()
        }
    }

    private fun List<DeviceInfoDto>.toRecords(): List<DeviceRecord> {
        val ownId = adminRepository.findOwnDeviceId(this)
        val baseUrl = sessionManager.currentSession.value?.serverUrl
        val sessionIcons =
            adminRepository.sessions.value
                ?.getOrNull()
                .orEmpty()
                .mapNotNull { session ->
                    val deviceId = session.deviceId ?: return@mapNotNull null
                    val icon = session.capabilities?.iconUrl ?: return@mapNotNull null
                    deviceId to icon
                }
                .toMap()
        return mapNotNull { device ->
            val id = device.id ?: return@mapNotNull null
            val appName = device.appName.orEmpty()
            DeviceRecord(
                id = id,
                name = device.customName ?: device.name.orEmpty(),
                appName = appName,
                appVersion = device.appVersion.orEmpty(),
                userId = device.lastUserId,
                userName = device.lastUserName,
                lastActivity = device.dateLastActivity,
                iconUrl =
                    device.iconUrl?.takeIf { it.isNotBlank() }
                        ?: device.capabilities.iconUrl?.takeIf { it.isNotBlank() }
                        ?: sessionIcons[id]
                        ?: AppConstants.CLIENT_ICON_URL.takeIf {
                            appName.equals(OWN_APP_NAME, ignoreCase = true)
                        },
                logoUrl = baseUrl?.let { webDeviceLogoUrl(it, appName, device.name.orEmpty()) },
                isThisDevice = id == ownId,
            )
        }
    }

    private fun publish() {
        val groups = allGroups ?: return
        val now = LocalDateTime.now()
        val weekAgo = now.minusDays(WEEK_DAYS)
        val idleBefore = now.minusDays(IDLE_AFTER_DAYS)
        _state.update { current ->
            current.copy(
                groups = groups.filter { it.matches(current.filter, weekAgo, idleBefore) },
                totalCount = groups.size,
                weekCount =
                    groups.count { it.matches(DeviceFilter.THIS_WEEK, weekAgo, idleBefore) },
                idleCount = groups.count { it.matches(DeviceFilter.IDLE, weekAgo, idleBefore) },
                failed = false,
            )
        }
    }
}
