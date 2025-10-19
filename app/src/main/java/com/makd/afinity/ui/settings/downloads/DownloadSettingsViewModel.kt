package com.makd.afinity.ui.settings.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.download.DownloadPriority
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.models.download.QueuedDownloadItem
import com.makd.afinity.data.network.ConnectionType
import com.makd.afinity.data.network.NetworkMonitor
import com.makd.afinity.data.network.NetworkState
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadSettingsUiState())
    val uiState: StateFlow<DownloadSettingsUiState> = _uiState.asStateFlow()
    private val _networkState = MutableStateFlow(NetworkState(false, ConnectionType.NONE))
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()


    init {
        observeDownloadStates()
        loadSettings()
        observeNetworkState()
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadRepository.downloadStates.collect { states ->
                val activeDownloads = states.values.filterIsInstance<DownloadState.Downloading>()
                val completedDownloads = states.values.filterIsInstance<DownloadState.Completed>()
                val queuedDownloads = downloadRepository.getDownloadQueue()

                val totalSize = completedDownloads.sumOf { it.file.length() }

                _uiState.value = _uiState.value.copy(
                    activeDownloads = activeDownloads,
                    completedDownloads = completedDownloads,
                    queuedDownloads = queuedDownloads,
                    storageUsed = formatBytes(totalSize)
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                wifiOnly = preferencesRepository.getDownloadOverWifiOnly(),
                maxConcurrentDownloads = 3 // TODO: Get from preferences
            )
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkMonitor.observeNetworkState().collect { state ->
                _networkState.value = state
            }
        }
    }

    fun cancelDownload(itemId: UUID) {
        downloadRepository.cancelDownload(itemId)
    }

    fun deleteDownload(itemId: UUID) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(itemId)
        }
    }

    fun removeFromQueue(itemId: UUID) {
        downloadRepository.removeFromQueue(itemId)
    }

    fun changePriority(itemId: UUID, priority: DownloadPriority) {
        downloadRepository.changePriority(itemId, priority)
    }

    fun setMaxConcurrentDownloads(max: Int) {
        downloadRepository.setMaxConcurrentDownloads(max)
        _uiState.value = _uiState.value.copy(maxConcurrentDownloads = max)
        // TODO: Save to preferences
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDownloadOverWifiOnly(enabled)
            _uiState.value = _uiState.value.copy(wifiOnly = enabled)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            val downloads = _uiState.value.completedDownloads
            downloads.forEach { download ->
                downloadRepository.deleteDownload(download.itemId)
            }
            Timber.d("Cleared ${downloads.size} downloads")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

data class DownloadSettingsUiState(
    val activeDownloads: List<DownloadState.Downloading> = emptyList(),
    val completedDownloads: List<DownloadState.Completed> = emptyList(),
    val queuedDownloads: List<QueuedDownloadItem> = emptyList(),
    val storageUsed: String = "0 B",
    val maxConcurrentDownloads: Int = 3,
    val wifiOnly: Boolean = false
)