package com.makd.afinity.ui.admin.libraries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.ui.admin.config.ChoiceOptionsLoader
import com.makd.afinity.ui.admin.config.ConfigFormEditor
import com.makd.afinity.ui.admin.config.LoadedConfigForm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

private const val SCAN_FOLLOW_UP_CHECKS = 3
private val SCAN_FOLLOW_UP_DELAY = 1500.milliseconds

data class AdminLibraryDetail(
    val itemId: UUID?,
    val name: String,
    val type: CollectionTypeOptions?,
    val locations: List<String>,
    val scanState: LibraryScanState,
)

data class LibraryDetailUiState(
    val library: AdminLibraryDetail? = null,
    val failed: Boolean = false,
    val missing: Boolean = false,
    val isBusy: Boolean = false,
)

private fun normalizePath(path: String): String =
    path.trim().replace('\\', '/').trimEnd('/').lowercase()

@HiltViewModel
class LibraryDetailViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryName: String = savedStateHandle.get<String>("libraryName").orEmpty()

    private val _state = MutableStateFlow(LibraryDetailUiState())
    val state: StateFlow<LibraryDetailUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private val _refreshQueued = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshQueued: SharedFlow<Unit> = _refreshQueued.asSharedFlow()

    private val optionsLoader = ChoiceOptionsLoader(adminRepository)

    val optionsEditor =
        ConfigFormEditor(
            scope = viewModelScope,
            loadForm = ::loadOptionsForm,
            saveChanges = { changes -> adminRepository.updateLibraryOptions(libraryName, changes) },
        )

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun pollScanStatus() {
        viewModelScope.launch { fetch() }
    }

    fun onScanQueued() {
        _refreshQueued.tryEmit(Unit)
        viewModelScope.launch {
            repeat(SCAN_FOLLOW_UP_CHECKS) {
                delay(SCAN_FOLLOW_UP_DELAY)
                fetch()
                if (_state.value.library?.scanState != LibraryScanState.IDLE) return@launch
            }
        }
    }

    private suspend fun loadOptionsForm(): Result<LoadedConfigForm> {
        val document =
            adminRepository.getLibraryOptions(libraryName).getOrElse {
                return Result.failure(it)
            }
        val type = document.collectionType
        return adminRepository.getLibraryProviders(type, isNewLibrary = false).map { providers ->
            LoadedConfigForm(
                spec = optionsLoader.resolve(libraryOptionsForm(type, providers)),
                document = normalizeProviders(document.options, providers),
                title = libraryName,
            )
        }
    }

    suspend fun browseFolders(path: String?): Result<List<ServerFolder>> =
        adminRepository.getServerFolders(path)

    fun isFolderInLibrary(path: String): Boolean {
        val wanted = normalizePath(path)
        return _state.value.library?.locations.orEmpty().any { normalizePath(it) == wanted }
    }

    fun addFolder(path: String) {
        if (path.isBlank() || isFolderInLibrary(path)) return
        runCommand { adminRepository.addLibraryFolder(libraryName, path) }
    }

    fun removeFolder(path: String) {
        val locations = _state.value.library?.locations.orEmpty()
        if (locations.size <= 1 || path !in locations) return
        runCommand { adminRepository.removeLibraryFolder(libraryName, path) }
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
            .getLibraries()
            .onSuccess { folders ->
                val detail =
                    folders
                        .firstOrNull { it.name == libraryName }
                        ?.let { folder ->
                            AdminLibraryDetail(
                                itemId = folder.itemId?.toUUIDOrNull(),
                                name = folder.name.orEmpty(),
                                type = folder.collectionType,
                                locations = folder.locations.orEmpty(),
                                scanState =
                                    when (folder.refreshStatus) {
                                        "Active" -> LibraryScanState.ACTIVE
                                        "Queued" -> LibraryScanState.QUEUED
                                        else -> LibraryScanState.IDLE
                                    },
                            )
                        }
                _state.update {
                    it.copy(library = detail, failed = false, missing = detail == null)
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load library")
                _state.update { it.copy(failed = it.library == null) }
            }
    }
}
