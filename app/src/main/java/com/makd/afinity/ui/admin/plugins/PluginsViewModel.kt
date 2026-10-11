package com.makd.afinity.ui.admin.plugins

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
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
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus

enum class PendingPluginChange {
    ENABLE,
    DISABLE,
    UNINSTALL,
}

data class AdminPluginRow(
    val id: UUID,
    val name: String,
    val version: String,
    val status: PluginStatus,
    val isBuiltIn: Boolean,
    val imageUrl: String?,
    val settingsUrl: String?,
    val pendingChange: PendingPluginChange?,
) {
    val key: String
        get() = "$id|$version"

    val canEnable: Boolean
        get() =
            !isBuiltIn &&
                (status == PluginStatus.DISABLED ||
                    status == PluginStatus.RESTART ||
                    status == PluginStatus.MALFUNCTIONED)

    val canDisable: Boolean
        get() =
            !isBuiltIn &&
                (status == PluginStatus.ACTIVE ||
                    status == PluginStatus.RESTART ||
                    status == PluginStatus.MALFUNCTIONED ||
                    status == PluginStatus.NOT_SUPPORTED)

    val canUninstall: Boolean
        get() = !isBuiltIn && status != PluginStatus.RESTART && status != PluginStatus.DELETED

    val hasActions: Boolean
        get() = settingsUrl != null || canEnable || canDisable || canUninstall
}

data class PluginsUiState(
    val plugins: List<AdminPluginRow>? = null,
    val updates: List<CatalogPluginRow> = emptyList(),
    val failed: Boolean = false,
    val restartRequired: Boolean = false,
    val serverName: String = "",
    val isBusy: Boolean = false,
)

@StringRes
internal fun PluginStatus.labelRes(): Int? =
    when (this) {
        PluginStatus.ACTIVE -> null
        PluginStatus.RESTART -> R.string.plugin_status_restart
        PluginStatus.DISABLED -> R.string.plugin_status_disabled
        PluginStatus.MALFUNCTIONED -> R.string.plugin_status_malfunctioned
        PluginStatus.NOT_SUPPORTED -> R.string.plugin_status_not_supported
        PluginStatus.SUPERSEDED,
        PluginStatus.SUPERCEDED -> R.string.plugin_status_superseded
        PluginStatus.DELETED -> R.string.plugin_status_deleted
    }

internal val PluginStatus.isProblem: Boolean
    get() = this == PluginStatus.MALFUNCTIONED || this == PluginStatus.NOT_SUPPORTED

@HiltViewModel
class PluginsViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }

    private val settingsPages = MutableStateFlow<Map<UUID, String>>(emptyMap())
    private val pendingChanges = MutableStateFlow<Map<String, PendingPluginChange>>(emptyMap())
    private val isBusy = MutableStateFlow(false)

    private val catalog = MutableStateFlow<List<PackageInfo>?>(null)

    private val updates: Flow<List<CatalogPluginRow>> =
        combine(adminRepository.plugins, catalog) { pluginsResult, packages ->
            val plugins = pluginsResult?.getOrNull()
            if (plugins == null || packages == null) {
                emptyList()
            } else {
                buildCatalogRows(packages, plugins).filter { it.hasUpdate }.sortedBy { it.name }
            }
        }

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val state: StateFlow<PluginsUiState> =
        combine(
                adminRepository.plugins,
                adminRepository.systemInfo,
                settingsPages,
                pendingChanges,
                isBusy,
            ) { pluginsResult, systemInfoResult, pages, pending, busy ->
                val plugins = pluginsResult?.getOrNull()
                val systemInfo = systemInfoResult?.getOrNull()
                PluginsUiState(
                    plugins = plugins?.map { it.toRow(pages, pending) },
                    failed = pluginsResult?.isFailure == true,
                    restartRequired =
                        systemInfo?.hasPendingRestart == true ||
                            plugins.orEmpty().any { it.status == PluginStatus.RESTART },
                    serverName = systemInfo?.serverName.orEmpty(),
                    isBusy = busy,
                )
            }
            .combine(updates) { state, available -> state.copy(updates = available) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PluginsUiState(),
            )

    init {
        viewModelScope.launch {
            adminRepository.getPluginCatalog().onSuccess { catalog.value = it }
        }
        viewModelScope.launch {
            adminRepository.plugins.collect { result ->
                val plugins = result?.getOrNull() ?: return@collect
                val stillPending =
                    plugins
                        .filter { it.status == PluginStatus.RESTART }
                        .map { "${it.id}|${it.version}" }
                        .toSet()
                pendingChanges.update { changes -> changes.filterKeys { it in stillPending } }
                loadSettingsPages()
            }
        }
    }

    fun retry() {
        adminRepository.refresh()
    }

    fun setEnabled(plugin: AdminPluginRow, enabled: Boolean) {
        val change = if (enabled) PendingPluginChange.ENABLE else PendingPluginChange.DISABLE
        runCommand(plugin, change) {
            adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled)
        }
    }

    fun uninstall(plugin: AdminPluginRow) {
        if (!plugin.canUninstall) return
        runCommand(plugin, PendingPluginChange.UNINSTALL) {
            adminRepository.uninstallPlugin(plugin.id, plugin.version)
        }
    }

    fun update(plugin: CatalogPluginRow) {
        if (!plugin.hasUpdate || isBusy.value) return
        isBusy.value = true
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
                adminRepository.refresh()
            } finally {
                isBusy.value = false
            }
        }
    }

    fun restartServer() {
        if (isBusy.value) return
        isBusy.value = true
        viewModelScope.launch {
            try {
                if (adminRepository.restartServer().isFailure) _commandError.tryEmit(Unit)
            } finally {
                isBusy.value = false
            }
        }
    }

    private fun runCommand(
        plugin: AdminPluginRow,
        change: PendingPluginChange,
        block: suspend () -> Result<Unit>,
    ) {
        if (isBusy.value) return
        isBusy.value = true
        viewModelScope.launch {
            try {
                if (block().isSuccess) {
                    pendingChanges.update { it + (plugin.key to change) }
                } else {
                    _commandError.tryEmit(Unit)
                }
                adminRepository.refresh()
            } finally {
                isBusy.value = false
            }
        }
    }

    private fun loadSettingsPages() {
        viewModelScope.launch {
            adminRepository.getPluginSettingsPages().onSuccess { settingsPages.value = it }
        }
    }

    private fun PluginInfo.toRow(
        pages: Map<UUID, String>,
        pending: Map<String, PendingPluginChange>,
    ): AdminPluginRow {
        val baseUrl = sessionManager.currentSession.value?.serverUrl?.trimEnd('/')
        val settingsPage = pages[id]
        return AdminPluginRow(
            id = id,
            name = name,
            version = version,
            status = status,
            isBuiltIn = !canUninstall,
            imageUrl =
                if (hasImage && baseUrl != null) "$baseUrl/Plugins/$id/$version/Image" else null,
            settingsUrl =
                if (settingsPage != null && baseUrl != null) {
                    "$baseUrl/web/#/configurationpage?name=${Uri.encode(settingsPage)}"
                } else {
                    null
                },
            pendingChange = pending["$id|$version"].takeIf { status == PluginStatus.RESTART },
        )
    }
}
