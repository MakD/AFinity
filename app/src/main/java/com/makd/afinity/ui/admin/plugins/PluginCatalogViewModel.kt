package com.makd.afinity.ui.admin.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import timber.log.Timber

data class CatalogSection(val category: String, val plugins: List<CatalogPluginRow>)

data class PluginCatalogUiState(
    val sections: List<CatalogSection>? = null,
    val query: String = "",
    val failed: Boolean = false,
    val installingId: UUID? = null,
) {
    val isBusy: Boolean
        get() = installingId != null
}

@HiltViewModel
class PluginCatalogViewModel
@Inject
constructor(private val adminRepository: ServerAdminRepository) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }

    private val catalog = MutableStateFlow<List<PackageInfo>?>(null)
    private val failed = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val installingId = MutableStateFlow<UUID?>(null)

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val state: StateFlow<PluginCatalogUiState> =
        combine(catalog, adminRepository.plugins, failed, query, installingId) {
                packages,
                pluginsResult,
                loadFailed,
                text,
                installing ->
                val installed = pluginsResult?.getOrNull().orEmpty()
                val needle = text.trim()
                val sections = packages?.let { loaded ->
                    buildCatalogRows(loaded, installed)
                        .filter { row ->
                            needle.isEmpty() ||
                                row.name.contains(needle, ignoreCase = true) ||
                                row.summary.contains(needle, ignoreCase = true)
                        }
                        .groupBy { it.category }
                        .toSortedMap()
                        .map { (category, rows) ->
                            CatalogSection(category, rows.sortedBy { it.name.lowercase() })
                        }
                }
                PluginCatalogUiState(
                    sections = sections,
                    query = text,
                    failed = loadFailed,
                    installingId = installing,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PluginCatalogUiState(),
            )

    init {
        load(forceRefresh = false)
    }

    fun retry() {
        load(forceRefresh = true)
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun install(plugin: CatalogPluginRow) {
        if (installingId.value != null || (plugin.isInstalled && !plugin.hasUpdate)) return
        installingId.value = plugin.id
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
                installingId.value = null
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        failed.value = false
        viewModelScope.launch {
            adminRepository
                .getPluginCatalog(forceRefresh)
                .onSuccess { catalog.value = it }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.w(e, "Failed to load plugin catalog")
                    failed.update { catalog.value == null }
                }
        }
    }
}
