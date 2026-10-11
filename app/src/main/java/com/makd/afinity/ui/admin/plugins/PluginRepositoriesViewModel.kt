package com.makd.afinity.ui.admin.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.RepositoryInfo
import timber.log.Timber

data class PluginRepositoriesUiState(
    val repositories: List<RepositoryInfo>? = null,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
)

internal fun isValidRepositoryUrl(url: String): Boolean {
    val trimmed = url.trim()
    val hasScheme =
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    return hasScheme && trimmed.substringAfter("://").isNotBlank() && trimmed.none { it == ' ' }
}

@HiltViewModel
class PluginRepositoriesViewModel
@Inject
constructor(private val adminRepository: ServerAdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(PluginRepositoriesUiState())
    val state: StateFlow<PluginRepositoriesUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch { fetch() }
    }

    fun add(name: String, url: String) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || !isValidRepositoryUrl(cleanUrl)) return
        save { current ->
            if (current.any { it.url.equals(cleanUrl, ignoreCase = true) }) {
                current
            } else {
                current + RepositoryInfo(name = cleanName, url = cleanUrl, enabled = true)
            }
        }
    }

    fun remove(index: Int, repository: RepositoryInfo) {
        save { current ->
            if (current.getOrNull(index) == repository) {
                current.filterIndexed { position, _ -> position != index }
            } else {
                current
            }
        }
    }

    fun setEnabled(index: Int, repository: RepositoryInfo, enabled: Boolean) {
        save { current ->
            if (current.getOrNull(index) == repository) {
                current.mapIndexed { position, item ->
                    if (position == index) item.copy(enabled = enabled) else item
                }
            } else {
                current
            }
        }
    }

    private fun save(transform: (List<RepositoryInfo>) -> List<RepositoryInfo>) {
        val current = _state.value.repositories ?: return
        if (_state.value.isBusy) return
        val updated = transform(current)
        if (updated == current) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (adminRepository.setPluginRepositories(updated).isFailure) {
                    _commandError.tryEmit(Unit)
                }
                fetch()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun fetch() {
        adminRepository
            .getPluginRepositories()
            .onSuccess { repositories ->
                _state.update { it.copy(repositories = repositories, failed = false) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) {
                    Timber.w(e, "Failed to load plugin repositories")
                }
                _state.update { it.copy(failed = it.repositories == null) }
            }
    }
}
