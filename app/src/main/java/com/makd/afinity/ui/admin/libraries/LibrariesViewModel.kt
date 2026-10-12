package com.makd.afinity.ui.admin.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.util.JellyfinImageUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

private const val LIBRARY_COVER_MAX_WIDTH = 480
private const val SCAN_TASK_KEY = "RefreshLibrary"
private const val FULL_PROGRESS = 100.0
private const val SCAN_FOLLOW_UP_CHECKS = 3
private const val STOP_TIMEOUT_MS = 5_000L
private val SCAN_FOLLOW_UP_DELAY = 1500.milliseconds

enum class LibraryScanState {
    IDLE,
    QUEUED,
    ACTIVE,
}

enum class LibrariesMessage {
    SCAN_STARTED,
    COMMAND_FAILED,
}

data class AdminLibraryRow(
    val key: String,
    val itemId: UUID?,
    val name: String,
    val type: CollectionTypeOptions?,
    val locations: List<String>,
    val scanState: LibraryScanState,
    val scanProgress: Float?,
    val imageUrl: String?,
)

data class LibrariesUiState(
    val libraries: List<AdminLibraryRow>? = null,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
) {
    val scanningCount: Int
        get() = libraries.orEmpty().count { it.scanState != LibraryScanState.IDLE }
}

@HiltViewModel
class LibrariesViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    private val imageUrlBuilder: JellyfinImageUrlBuilder,
) : ViewModel() {

    private val _state = MutableStateFlow(LibrariesUiState())
    val state: StateFlow<LibrariesUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<LibrariesMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<LibrariesMessage> = _messages.asSharedFlow()

    private val scanAllRequested = MutableStateFlow(false)

    val isScanningAll: StateFlow<Boolean> =
        combine(scanAllRequested, adminRepository.scheduledTasks) { requested, tasks ->
                requested ||
                    tasks?.getOrNull().orEmpty().any { task ->
                        task.key == SCAN_TASK_KEY && task.state != TaskState.IDLE
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private var imageTags: Map<UUID, String> = emptyMap()

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch(refreshImages = true) }
    }

    fun pollScanStatus() {
        viewModelScope.launch { fetch(refreshImages = false) }
    }

    fun scanAll() {
        if (scanAllRequested.value) return
        scanAllRequested.value = true
        viewModelScope.launch {
            try {
                if (adminRepository.scanAllLibraries().isSuccess) {
                    adminRepository.refresh()
                    followScan()
                } else {
                    _messages.tryEmit(LibrariesMessage.COMMAND_FAILED)
                }
            } finally {
                scanAllRequested.value = false
            }
        }
    }

    fun scanLibrary(itemId: UUID) {
        viewModelScope.launch {
            if (adminRepository.scanLibrary(itemId).isSuccess) {
                _messages.tryEmit(LibrariesMessage.SCAN_STARTED)
                followScan()
            } else {
                _messages.tryEmit(LibrariesMessage.COMMAND_FAILED)
            }
        }
    }

    fun rename(name: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == name) return
        runCommand { adminRepository.renameLibrary(name, trimmed) }
    }

    fun remove(name: String) {
        runCommand { adminRepository.removeLibrary(name) }
    }

    private fun runCommand(block: suspend () -> Result<Unit>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (block().isFailure) _messages.tryEmit(LibrariesMessage.COMMAND_FAILED)
                fetch(refreshImages = true)
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun followScan() {
        repeat(SCAN_FOLLOW_UP_CHECKS) {
            delay(SCAN_FOLLOW_UP_DELAY)
            fetch(refreshImages = false)
            if (_state.value.scanningCount > 0) return
        }
    }

    private suspend fun fetch(refreshImages: Boolean) {
        adminRepository
            .getLibraries()
            .onSuccess { folders ->
                val itemIds = folders.mapNotNull { it.itemId?.toUUIDOrNull() }
                if (refreshImages || itemIds.any { it !in imageTags }) {
                    adminRepository.getLibraryImageTags(itemIds).onSuccess { imageTags = it }
                }
                val baseUrl = sessionManager.currentSession.value?.serverUrl
                val rows = folders.mapIndexed { index, folder ->
                    val itemId = folder.itemId?.toUUIDOrNull()
                    AdminLibraryRow(
                        key = folder.itemId ?: "library_$index",
                        itemId = itemId,
                        name = folder.name.orEmpty(),
                        type = folder.collectionType,
                        locations = folder.locations.orEmpty(),
                        scanState =
                            when (folder.refreshStatus) {
                                "Active" -> LibraryScanState.ACTIVE
                                "Queued" -> LibraryScanState.QUEUED
                                else -> LibraryScanState.IDLE
                            },
                        scanProgress =
                            folder.refreshProgress?.let { progress ->
                                (progress / FULL_PROGRESS).toFloat().coerceIn(0f, 1f)
                            },
                        imageUrl =
                            if (
                                baseUrl != null &&
                                    itemId != null &&
                                    folder.primaryImageItemId != null
                            ) {
                                imageUrlBuilder.buildPrimaryImageUrl(
                                    baseUrl = baseUrl,
                                    itemId = itemId.toString(),
                                    tag = imageTags[itemId],
                                    maxWidth = LIBRARY_COVER_MAX_WIDTH,
                                )
                            } else {
                                null
                            },
                    )
                }
                _state.update { it.copy(libraries = rows, failed = false) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load libraries")
                _state.update { it.copy(failed = it.libraries == null) }
            }
    }
}
