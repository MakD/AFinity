package com.makd.afinity.ui.admin.plugins

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PackageInfo
import org.jellyfin.sdk.model.api.PluginStatus
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

data class PluginDetailUiState(
    val isLoaded: Boolean = false,
    val failed: Boolean = false,
    val name: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val installed: AdminPluginRow? = null,
    val catalog: CatalogPluginRow? = null,
    val isBusy: Boolean = false,
    val isInstalling: Boolean = false,
) {
    val installable: CatalogPluginRow?
        get() = catalog?.takeIf { installed == null || it.hasUpdate }

    val isEnabled: Boolean
        get() =
            when (installed?.status) {
                null,
                PluginStatus.DISABLED -> false
                PluginStatus.RESTART -> installed.pendingChange != PendingPluginChange.DISABLE
                else -> true
            }

    val canToggle: Boolean
        get() = installed != null && (installed.canEnable || installed.canDisable)
}

private data class DetailActivity(
    val pendingChange: PendingPluginChange? = null,
    val isBusy: Boolean = false,
    val isInstalling: Boolean = false,
)

private data class DetailCatalog(
    val packages: List<PackageInfo>? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class PluginDetailViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }

    private val pluginId: UUID? = savedStateHandle.get<String>("pluginId")?.toUUIDOrNull()

    private val catalog = MutableStateFlow(DetailCatalog())
    private val settingsPages = MutableStateFlow<Map<UUID, String>>(emptyMap())
    private val activity = MutableStateFlow(DetailActivity())

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val state: StateFlow<PluginDetailUiState> =
        combine(adminRepository.plugins, catalog, settingsPages, activity) {
                pluginsResult,
                loadedCatalog,
                pages,
                running ->
                val plugins = pluginsResult?.getOrNull()
                val packages = loadedCatalog.packages
                val catalogSettled = packages != null || loadedCatalog.failed
                if (plugins == null || !catalogSettled) {
                    return@combine PluginDetailUiState(failed = pluginsResult?.isFailure == true)
                }
                val baseUrl = sessionManager.currentSession.value?.serverUrl?.trimEnd('/')
                val plugin = currentPluginVersions(plugins).firstOrNull { it.id == pluginId }
                val entry =
                    packages
                        ?.let { buildCatalogRows(it, plugins) }
                        ?.firstOrNull { it.id == pluginId }
                if (plugin == null && entry == null) {
                    return@combine PluginDetailUiState(failed = true)
                }
                val installed = plugin?.toAdminRow(baseUrl, pages, running.pendingChange)
                PluginDetailUiState(
                    isLoaded = true,
                    name = plugin?.name ?: entry?.name.orEmpty(),
                    description =
                        entry?.description?.takeIf { it.isNotBlank() }
                            ?: plugin?.description.orEmpty(),
                    imageUrl = installed?.imageUrl ?: entry?.imageUrl,
                    installed = installed,
                    catalog = entry,
                    isBusy = running.isBusy || running.isInstalling,
                    isInstalling = running.isInstalling,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PluginDetailUiState(),
            )

    init {
        loadCatalog(forceRefresh = false)
        loadSettingsPages()
    }

    fun retry() {
        adminRepository.refreshPlugins()
        loadCatalog(forceRefresh = true)
        loadSettingsPages()
    }

    fun setEnabled(enabled: Boolean) {
        val plugin = state.value.installed ?: return
        if (enabled && !plugin.canEnable) return
        if (!enabled && !plugin.canDisable) return
        val change = if (enabled) PendingPluginChange.ENABLE else PendingPluginChange.DISABLE
        runCommand(change) { adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled) }
    }

    fun uninstall() {
        val plugin = state.value.installed ?: return
        if (!plugin.canUninstall) return
        runCommand(PendingPluginChange.UNINSTALL) {
            adminRepository.uninstallPlugin(plugin.id, plugin.version)
        }
    }

    fun install() {
        val plugin = state.value.installable ?: return
        val running = activity.value
        if (running.isBusy || running.isInstalling) return
        activity.update { it.copy(isInstalling = true) }
        viewModelScope.launch {
            try {
                val result =
                    adminRepository.installPlugin(
                        name = plugin.name,
                        id = plugin.id,
                        version = plugin.latestVersion,
                        repositoryUrl = plugin.repositoryUrl,
                    )
                if (result.isFailure) _commandError.tryEmit(Unit)
                adminRepository.refreshPlugins()
            } finally {
                activity.update { it.copy(isInstalling = false) }
            }
        }
    }

    private fun runCommand(change: PendingPluginChange, block: suspend () -> Result<Unit>) {
        val running = activity.value
        if (running.isBusy || running.isInstalling) return
        activity.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (block().isSuccess) {
                    activity.update { it.copy(pendingChange = change) }
                } else {
                    _commandError.tryEmit(Unit)
                }
                adminRepository.refreshPlugins()
            } finally {
                activity.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun loadCatalog(forceRefresh: Boolean) {
        catalog.update { it.copy(failed = false) }
        viewModelScope.launch {
            adminRepository
                .getPluginCatalog(forceRefresh)
                .onSuccess { packages -> catalog.value = DetailCatalog(packages = packages) }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load plugin catalog")
                    catalog.update { it.copy(failed = it.packages == null) }
                }
        }
    }

    private fun loadSettingsPages() {
        viewModelScope.launch {
            adminRepository.getPluginSettingsPages().onSuccess { settingsPages.value = it }
        }
    }
}
