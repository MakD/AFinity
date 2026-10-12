package com.makd.afinity.ui.admin.plugins

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
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
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus
import timber.log.Timber

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
}

data class PluginsUiState(
    val cards: List<PluginCardRow>? = null,
    val tab: PluginTab = PluginTab.INSTALLED,
    val installedCount: Int = 0,
    val availableCount: Int? = null,
    val categories: List<String> = emptyList(),
    val category: String? = null,
    val query: String = "",
    val searchActive: Boolean = false,
    val failed: Boolean = false,
    val catalogFailed: Boolean = false,
    val restartRequired: Boolean = false,
    val serverName: String = "",
    val installingId: UUID? = null,
    val isBusy: Boolean = false,
) {
    val isCatalogLoading: Boolean
        get() = availableCount == null && !catalogFailed
}

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

internal fun PluginInfo.toAdminRow(
    baseUrl: String?,
    settingsPages: Map<UUID, String>,
    pendingChange: PendingPluginChange?,
): AdminPluginRow {
    val settingsPage = settingsPages[id]
    return AdminPluginRow(
        id = id,
        name = name,
        version = version,
        status = status,
        isBuiltIn = !canUninstall,
        imageUrl = pluginImageUrl(this, baseUrl),
        settingsUrl =
            if (settingsPage != null && baseUrl != null) {
                "$baseUrl/web/#/configurationpage?name=${Uri.encode(settingsPage)}"
            } else {
                null
            },
        pendingChange = pendingChange.takeIf { status == PluginStatus.RESTART },
    )
}

private data class PluginFilters(
    val tab: PluginTab = PluginTab.INSTALLED,
    val category: String? = null,
    val query: String = "",
    val searchActive: Boolean = false,
)

private data class PluginActivity(val installingId: UUID? = null, val isBusy: Boolean = false)

private data class PluginData(
    val cards: List<PluginCardRow>?,
    val failed: Boolean,
    val catalogLoaded: Boolean,
    val catalogFailed: Boolean,
    val restartRequired: Boolean,
    val serverName: String,
)

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

    private val catalog = MutableStateFlow<List<PackageInfo>?>(null)
    private val catalogFailed = MutableStateFlow(false)
    private val filters = MutableStateFlow(PluginFilters())
    private val activity = MutableStateFlow(PluginActivity())

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    private val data =
        combine(adminRepository.plugins, adminRepository.systemInfo, catalog, catalogFailed) {
            pluginsResult,
            systemInfoResult,
            packages,
            packagesFailed ->
            val plugins = pluginsResult?.getOrNull()
            val systemInfo = systemInfoResult?.getOrNull()
            val baseUrl = sessionManager.currentSession.value?.serverUrl?.trimEnd('/')
            PluginData(
                cards = plugins?.let { buildPluginCards(it, packages, baseUrl) },
                failed = pluginsResult?.isFailure == true,
                catalogLoaded = packages != null,
                catalogFailed = packagesFailed,
                restartRequired =
                    systemInfo?.hasPendingRestart == true ||
                        plugins.orEmpty().any { it.status == PluginStatus.RESTART },
                serverName = systemInfo?.serverName.orEmpty(),
            )
        }

    val state: StateFlow<PluginsUiState> =
        combine(data, filters, activity) { loaded, chosen, running ->
                val all = loaded.cards
                val inTab = all?.filter { it.isInstalled == (chosen.tab == PluginTab.INSTALLED) }
                val categories = inTab.orEmpty().mapNotNull { it.category }.distinct().sorted()
                val category = chosen.category?.takeIf { it in categories }
                val needle = chosen.query.trim()
                PluginsUiState(
                    cards =
                        inTab
                            ?.filter { card ->
                                (category == null || card.category == category) &&
                                    (needle.isEmpty() ||
                                        card.name.contains(needle, ignoreCase = true) ||
                                        card.summary.contains(needle, ignoreCase = true))
                            }
                            ?.sortedBy { it.name.lowercase() },
                    tab = chosen.tab,
                    installedCount = all.orEmpty().count { it.isInstalled },
                    availableCount =
                        if (loaded.catalogLoaded) all.orEmpty().count { !it.isInstalled } else null,
                    categories = categories,
                    category = category,
                    query = chosen.query,
                    searchActive = chosen.searchActive,
                    failed = loaded.failed,
                    catalogFailed = loaded.catalogFailed,
                    restartRequired = loaded.restartRequired,
                    serverName = loaded.serverName,
                    installingId = running.installingId,
                    isBusy = running.isBusy || running.installingId != null,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PluginsUiState(),
            )

    init {
        loadCatalog(forceRefresh = false)
    }

    fun retry() {
        adminRepository.refresh()
        loadCatalog(forceRefresh = true)
    }

    fun selectTab(tab: PluginTab) {
        filters.update { it.copy(tab = tab, category = null) }
    }

    fun selectCategory(category: String?) {
        filters.update { it.copy(category = category) }
    }

    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
    }

    fun setSearchActive(active: Boolean) {
        filters.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    }

    fun install(plugin: CatalogPluginRow) {
        val running = activity.value
        if (running.isBusy || running.installingId != null) return
        if (plugin.isInstalled && !plugin.hasUpdate) return
        activity.update { it.copy(installingId = plugin.id) }
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
                activity.update { it.copy(installingId = null) }
            }
        }
    }

    fun restartServer() {
        val running = activity.value
        if (running.isBusy || running.installingId != null) return
        activity.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (adminRepository.restartServer().isFailure) _commandError.tryEmit(Unit)
            } finally {
                activity.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun loadCatalog(forceRefresh: Boolean) {
        catalogFailed.value = false
        viewModelScope.launch {
            adminRepository
                .getPluginCatalog(forceRefresh)
                .onSuccess { catalog.value = it }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load plugin catalog")
                    catalogFailed.value = catalog.value == null
                }
        }
    }
}
