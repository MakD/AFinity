package com.makd.afinity.ui.admin.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber

internal object PolicyKeys {
    const val IS_ADMINISTRATOR = "IsAdministrator"
    const val IS_HIDDEN = "IsHidden"
    const val ENABLE_REMOTE_ACCESS = "EnableRemoteAccess"
    const val ENABLE_ALL_FOLDERS = "EnableAllFolders"
    const val ENABLED_FOLDERS = "EnabledFolders"
    const val ENABLE_ALL_CHANNELS = "EnableAllChannels"
    const val ENABLED_CHANNELS = "EnabledChannels"
    const val ENABLE_MEDIA_PLAYBACK = "EnableMediaPlayback"
    const val ENABLE_AUDIO_TRANSCODING = "EnableAudioPlaybackTranscoding"
    const val ENABLE_VIDEO_TRANSCODING = "EnableVideoPlaybackTranscoding"
    const val ENABLE_REMUXING = "EnablePlaybackRemuxing"
    const val ENABLE_DOWNLOADING = "EnableContentDownloading"
    const val ENABLE_DELETION = "EnableContentDeletion"
    const val DELETION_FOLDERS = "EnableContentDeletionFromFolders"
    const val FORCE_REMOTE_TRANSCODING = "ForceRemoteSourceTranscoding"
    const val SYNC_PLAY_ACCESS = "SyncPlayAccess"
    const val REMOTE_BITRATE_LIMIT = "RemoteClientBitrateLimit"
    const val ENABLE_COLLECTIONS = "EnableCollectionManagement"
    const val ENABLE_SUBTITLES = "EnableSubtitleManagement"
    const val ENABLE_LIVE_TV_ACCESS = "EnableLiveTvAccess"
    const val ENABLE_LIVE_TV_MANAGEMENT = "EnableLiveTvManagement"
    const val ENABLE_CONTROL_OTHERS = "EnableRemoteControlOfOtherUsers"
    const val ENABLE_SHARED_DEVICES = "EnableSharedDeviceControl"
    const val MAX_ACTIVE_SESSIONS = "MaxActiveSessions"
    const val LOGIN_ATTEMPTS = "LoginAttemptsBeforeLockout"
    const val ENABLE_ALL_DEVICES = "EnableAllDevices"
    const val ENABLED_DEVICES = "EnabledDevices"
    const val MAX_PARENTAL_RATING = "MaxParentalRating"
    const val MAX_PARENTAL_SUB_RATING = "MaxParentalSubRating"
    const val BLOCK_UNRATED_ITEMS = "BlockUnratedItems"
    const val ALLOWED_TAGS = "AllowedTags"
    const val BLOCKED_TAGS = "BlockedTags"
    const val ACCESS_SCHEDULES = "AccessSchedules"
}

private const val MAX_SESSIONS_LIMIT = 999
private const val LOCKOUT_DISABLED = -1
private const val MAX_LOCKOUT_ATTEMPTS = 999
private const val BITRATE_DECIMALS = 6
private val MAX_BITRATE_MEGABITS = BigDecimal("2147.25")

private fun megabitsOrNull(text: String): BigDecimal? =
    if (text.isBlank()) BigDecimal.ZERO else text.toBigDecimalOrNull()

private fun megabitsText(bitsPerSecond: Long): String =
    if (bitsPerSecond <= 0) ""
    else
        BigDecimal(bitsPerSecond)
            .movePointLeft(BITRATE_DECIMALS)
            .stripTrailingZeros()
            .toPlainString()

data class PermissionLibrary(val id: String, val name: String)

data class PermissionDevice(val id: String, val name: String, val appName: String?)

data class UserPermissionsUiState(
    val userName: String = "",
    val policy: JsonObject? = null,
    val changes: Map<String, JsonElement> = emptyMap(),
    val libraries: List<PermissionLibrary> = emptyList(),
    val channels: List<PermissionLibrary> = emptyList(),
    val deletableChannels: List<PermissionLibrary> = emptyList(),
    val devices: List<PermissionDevice> = emptyList(),
    val maxSessionsText: String = "",
    val lockoutText: String = "",
    val bitrateText: String = "",
    val isSelf: Boolean = false,
    val isOnlyAdmin: Boolean = false,
    val areUserFactsLoaded: Boolean = false,
    val failed: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = changes.isNotEmpty()

    val canChangeAdministrator: Boolean
        get() = areUserFactsLoaded && !isSelf && !isOnlyAdmin

    val isMaxSessionsValid: Boolean
        get() = maxSessionsText.toIntOrNull()?.let { it in 0..MAX_SESSIONS_LIMIT } == true

    val isLockoutValid: Boolean
        get() =
            lockoutText.toIntOrNull()?.let { it in LOCKOUT_DISABLED..MAX_LOCKOUT_ATTEMPTS } == true

    val isBitrateValid: Boolean
        get() =
            megabitsOrNull(bitrateText)?.let {
                it >= BigDecimal.ZERO && it <= MAX_BITRATE_MEGABITS
            } == true

    val canSave: Boolean
        get() = isDirty && isMaxSessionsValid && isLockoutValid && isBitrateValid && !isSaving

    val enabledFolders: Set<String>
        get() = folderIds(value(PolicyKeys.ENABLED_FOLDERS))

    val enabledChannels: Set<String>
        get() = folderIds(value(PolicyKeys.ENABLED_CHANNELS))

    val deletionFolders: Set<String>
        get() = folderIds(value(PolicyKeys.DELETION_FOLDERS))

    val syncPlayAccess: String?
        get() = (value(PolicyKeys.SYNC_PLAY_ACCESS) as? JsonPrimitive)?.contentOrNull

    val enabledDevices: Set<String>
        get() = stringSet(value(PolicyKeys.ENABLED_DEVICES))

    fun flag(key: String): Boolean = (value(key) as? JsonPrimitive)?.booleanOrNull == true

    private fun value(key: String): JsonElement? = changes[key] ?: policy?.get(key)
}

private fun normalizeId(id: String): String = id.replace("-", "").lowercase()

private fun stringSet(element: JsonElement?): Set<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()

private fun folderIds(element: JsonElement?): Set<String> =
    stringSet(element).map(::normalizeId).toSet()

@HiltViewModel
class UserPermissionsViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: UUID? =
        savedStateHandle.get<String>("userId")?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
        }

    private val _state = MutableStateFlow(UserPermissionsUiState())
    val state: StateFlow<UserPermissionsUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        val id = userId ?: return
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { loadPolicy(id) }
        viewModelScope.launch { loadUserFacts(id) }
        viewModelScope.launch { loadLibraries() }
        viewModelScope.launch { loadChannels() }
        viewModelScope.launch { loadDevices() }
    }

    fun setFlag(key: String, enabled: Boolean) {
        if (key == PolicyKeys.IS_ADMINISTRATOR && !_state.value.canChangeAdministrator) return
        change(key, JsonPrimitive(enabled))
    }

    fun setLibraryEnabled(libraryId: String, enabled: Boolean) {
        setFolderListed(
            PolicyKeys.ENABLED_FOLDERS,
            _state.value.enabledFolders,
            libraryId,
            enabled,
        )
    }

    fun setChannelEnabled(channelId: String, enabled: Boolean) {
        setFolderListed(
            PolicyKeys.ENABLED_CHANNELS,
            _state.value.enabledChannels,
            channelId,
            enabled,
        )
    }

    fun setDeletionLibraryEnabled(libraryId: String, enabled: Boolean) {
        setFolderListed(
            PolicyKeys.DELETION_FOLDERS,
            _state.value.deletionFolders,
            libraryId,
            enabled,
        )
    }

    fun setSyncPlayAccess(access: String) {
        change(PolicyKeys.SYNC_PLAY_ACCESS, JsonPrimitive(access))
    }

    fun setBitrateText(text: String) {
        val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        _state.update { it.copy(bitrateText = cleaned) }
        val megabits = megabitsOrNull(cleaned) ?: return
        if (megabits >= BigDecimal.ZERO && megabits <= MAX_BITRATE_MEGABITS) {
            val bitsPerSecond = megabits.movePointRight(BITRATE_DECIMALS).toLong()
            change(PolicyKeys.REMOTE_BITRATE_LIMIT, JsonPrimitive(bitsPerSecond))
        }
    }

    private fun setFolderListed(
        key: String,
        current: Set<String>,
        libraryId: String,
        listed: Boolean,
    ) {
        val updated = if (listed) current + libraryId else current - libraryId
        _state.update { state ->
            val original = folderIds(state.policy?.get(key))
            val changes =
                if (updated == original) {
                    state.changes - key
                } else {
                    val ids = JsonArray(updated.sorted().map { JsonPrimitive(it) })
                    state.changes + (key to ids)
                }
            state.copy(changes = changes)
        }
    }

    fun setMaxSessionsText(text: String) {
        val digits = text.filter { it.isDigit() }
        _state.update { it.copy(maxSessionsText = digits) }
        val number = digits.toIntOrNull()
        if (number != null && number in 0..MAX_SESSIONS_LIMIT) {
            change(PolicyKeys.MAX_ACTIVE_SESSIONS, JsonPrimitive(number))
        }
    }

    fun setLockoutText(text: String) {
        val cleaned = text.filterIndexed { index, char ->
            char.isDigit() || (index == 0 && char == '-')
        }
        _state.update { it.copy(lockoutText = cleaned) }
        val number = cleaned.toIntOrNull()
        if (number != null && number in LOCKOUT_DISABLED..MAX_LOCKOUT_ATTEMPTS) {
            change(PolicyKeys.LOGIN_ATTEMPTS, JsonPrimitive(number))
        }
    }

    fun setDeviceEnabled(deviceId: String, enabled: Boolean) {
        val current = _state.value.enabledDevices
        val updated = if (enabled) current + deviceId else current - deviceId
        _state.update { state ->
            val original = stringSet(state.policy?.get(PolicyKeys.ENABLED_DEVICES))
            val changes =
                if (updated == original) {
                    state.changes - PolicyKeys.ENABLED_DEVICES
                } else {
                    val ids = JsonArray(updated.sorted().map { JsonPrimitive(it) })
                    state.changes + (PolicyKeys.ENABLED_DEVICES to ids)
                }
            state.copy(changes = changes)
        }
    }

    fun save() {
        val id = userId ?: return
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (adminRepository.updateUserPolicy(id, current.changes).isSuccess) {
                    _state.update { it.copy(changes = emptyMap()) }
                    val facts = launch { loadUserFacts(id) }
                    loadPolicy(id)
                    facts.join()
                } else {
                    _commandError.tryEmit(Unit)
                }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun change(key: String, value: JsonElement) {
        _state.update { state ->
            val changes =
                if (state.policy?.get(key) == value) state.changes - key
                else state.changes + (key to value)
            state.copy(changes = changes)
        }
    }

    private suspend fun loadPolicy(id: UUID) {
        adminRepository
            .getUserPolicy(id)
            .onSuccess { user ->
                val policy = user.policy
                val sessions =
                    (policy[PolicyKeys.MAX_ACTIVE_SESSIONS] as? JsonPrimitive)?.intOrNull ?: 0
                val lockout =
                    (policy[PolicyKeys.LOGIN_ATTEMPTS] as? JsonPrimitive)?.intOrNull
                        ?: LOCKOUT_DISABLED
                val bitrate =
                    (policy[PolicyKeys.REMOTE_BITRATE_LIMIT] as? JsonPrimitive)?.longOrNull ?: 0L
                _state.update { state ->
                    state.copy(
                        policy = policy,
                        maxSessionsText =
                            if (state.isDirty) state.maxSessionsText else sessions.toString(),
                        lockoutText = if (state.isDirty) state.lockoutText else lockout.toString(),
                        bitrateText =
                            if (state.isDirty) state.bitrateText else megabitsText(bitrate),
                        failed = false,
                    )
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load user policy")
                _state.update { it.copy(failed = it.policy == null) }
            }
    }

    private suspend fun loadUserFacts(id: UUID) {
        adminRepository.getUsers().onSuccess { users ->
            val user = users.firstOrNull { it.id == id } ?: return@onSuccess
            val isAdmin = user.policy?.isAdministrator == true
            val adminCount = users.count { it.policy?.isAdministrator == true }
            _state.update {
                it.copy(
                    userName = user.name.orEmpty(),
                    isSelf = id == sessionManager.currentSession.value?.userId,
                    isOnlyAdmin = isAdmin && adminCount == 1,
                    areUserFactsLoaded = true,
                )
            }
        }
    }

    private suspend fun loadDevices() {
        adminRepository.getDevices().onSuccess { found ->
            val devices =
                found
                    .mapNotNull { device ->
                        val deviceId = device.id ?: return@mapNotNull null
                        PermissionDevice(
                            id = deviceId,
                            name =
                                device.customName?.takeIf { it.isNotBlank() }
                                    ?: device.name.orEmpty(),
                            appName = device.appName,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            _state.update { it.copy(devices = devices) }
        }
    }

    private suspend fun loadLibraries() {
        adminRepository.getLibraries().onSuccess { folders ->
            val libraries =
                folders
                    .mapNotNull { folder ->
                        val itemId = folder.itemId ?: return@mapNotNull null
                        PermissionLibrary(id = normalizeId(itemId), name = folder.name.orEmpty())
                    }
                    .sortedBy { it.name.lowercase() }
            _state.update { it.copy(libraries = libraries) }
        }
    }

    private suspend fun loadChannels() {
        val channels = adminRepository.getChannels().getOrNull()?.toPermissionEntries().orEmpty()
        val deletable =
            if (channels.isEmpty()) emptyList()
            else
                adminRepository
                    .getChannels(deletableOnly = true)
                    .getOrNull()
                    ?.toPermissionEntries()
                    .orEmpty()
        _state.update { it.copy(channels = channels, deletableChannels = deletable) }
    }

    private fun List<BaseItemDto>.toPermissionEntries(): List<PermissionLibrary> = map {
        PermissionLibrary(id = normalizeId(it.id.toString()), name = it.name.orEmpty())
    }
        .sortedBy { it.name.lowercase() }
}
