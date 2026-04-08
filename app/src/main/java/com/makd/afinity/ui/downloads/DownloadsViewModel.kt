package com.makd.afinity.ui.downloads

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val downloadRepository: DownloadRepository,
    private val absDownloadRepository: AbsDownloadRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        loadStorageInfo()
        loadDownloadPreferences()
    }

    private fun loadDownloadPreferences() {
        viewModelScope.launch {
            try {
                val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()
                val isImageCacheEnabled = preferencesRepository.getImageCacheEnabled()
                val imageCacheSizeMb = preferencesRepository.getImageCacheSizeMb()

                _uiState.value = _uiState.value.copy(
                    downloadOverWifiOnly = wifiOnly,
                    isImageCacheEnabled = isImageCacheEnabled,
                    imageCacheSizeMb = imageCacheSizeMb
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load download preferences")
            }
        }
    }

    fun setDownloadOverWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDownloadOverWifiOnly(wifiOnly)
                _uiState.value = _uiState.value.copy(downloadOverWifiOnly = wifiOnly)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update download WiFi preference")
            }
        }
    }

    fun setImageCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setImageCacheEnabled(enabled)
                _uiState.value = _uiState.value.copy(isImageCacheEnabled = enabled)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update image cache enabled preference")
            }
        }
    }

    fun setImageCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            try {
                preferencesRepository.setImageCacheSizeMb(sizeMb)
                _uiState.value = _uiState.value.copy(imageCacheSizeMb = sizeMb)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update image cache size preference")
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository
                    .getActiveDownloadsFlow()
                    .catch { e -> Timber.e(e, "Error observing active downloads") }
                    .collect { activeDownloads ->
                        _uiState.value = _uiState.value.copy(activeDownloads = activeDownloads)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe active downloads")
            }
        }

        viewModelScope.launch {
            try {
                downloadRepository
                    .getCompletedDownloadsFlow()
                    .catch { e -> Timber.e(e, "Error observing completed downloads") }
                    .collect { completedDownloads ->
                        _uiState.value =
                            _uiState.value.copy(completedDownloads = completedDownloads)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe completed downloads")
            }
        }

        viewModelScope.launch {
            try {
                absDownloadRepository
                    .getActiveDownloadsFlow()
                    .catch { e -> Timber.e(e, "Error observing ABS active downloads") }
                    .collect { absActive ->
                        _uiState.value = _uiState.value.copy(absActiveDownloads = absActive)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe ABS active downloads")
            }
        }

        viewModelScope.launch {
            try {
                absDownloadRepository
                    .getCompletedDownloadsFlow()
                    .catch { e -> Timber.e(e, "Error observing ABS completed downloads") }
                    .collect { absCompleted ->
                        _uiState.value = _uiState.value.copy(absCompletedDownloads = absCompleted)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe ABS completed downloads")
            }
        }
    }

    private fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val appStorageUsed = downloadRepository.getTotalStorageUsed()
                val allServersStorageUsed = downloadRepository.getTotalStorageUsedAllServers()
                val deviceStats = getDeviceStorageStats()
                _uiState.value =
                    _uiState.value.copy(
                        totalStorageUsed = appStorageUsed,
                        totalStorageUsedAllServers = allServersStorageUsed,
                        deviceStorageStats = deviceStats,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load storage info")
            }
        }
    }

    fun pauseDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                val result = downloadRepository.pauseDownload(downloadId)
                result.onFailure { error ->
                    Timber.e(error, "Failed to pause download")
                    _uiState.value =
                        _uiState.value.copy(error = "Failed to pause download: ${error.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pausing download")
            }
        }
    }

    fun resumeDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                val result = downloadRepository.resumeDownload(downloadId)
                result.onFailure { error ->
                    Timber.e(error, "Failed to resume download")
                    _uiState.value =
                        _uiState.value.copy(error = "Failed to resume download: ${error.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error resuming download")
            }
        }
    }

    fun cancelDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                val result = downloadRepository.cancelDownload(downloadId)
                result
                    .onSuccess {
                        Timber.i("Download cancelled successfully")
                        loadStorageInfo()
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to cancel download")
                        _uiState.value =
                            _uiState.value.copy(
                                error = "Failed to cancel download: ${error.message}"
                            )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling download")
            }
        }
    }

    fun deleteDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                val result = downloadRepository.deleteDownload(downloadId)
                result
                    .onSuccess {
                        Timber.i("Download deleted successfully")
                        loadStorageInfo()
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to delete download")
                        _uiState.value =
                            _uiState.value.copy(
                                error = "Failed to delete download: ${error.message}"
                            )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting download")
            }
        }
    }

    fun cancelAbsDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                absDownloadRepository.cancelDownload(downloadId)
                    .onFailure { Timber.e(it, "Failed to cancel ABS download") }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling ABS download")
            }
        }
    }

    fun deleteAbsDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                absDownloadRepository.deleteDownload(downloadId)
                    .onSuccess { loadStorageInfo() }
                    .onFailure { Timber.e(it, "Failed to delete ABS download") }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting ABS download")
            }
        }
    }

    fun deleteAbsPodcast(libraryItemId: String) {
        viewModelScope.launch {
            uiState.value.absCompletedDownloads
                .filter { it.libraryItemId == libraryItemId }
                .forEach { absDownloadRepository.deleteDownload(it.id) }
            loadStorageInfo()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun retryFailedDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                val download = downloadRepository.getDownload(downloadId)
                if (download != null && download.status == DownloadStatus.FAILED) {
                    downloadRepository.cancelDownload(downloadId)

                    Timber.d("Failed download cleared, user can restart from item detail")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error retrying failed download")
            }
        }
    }

    fun formatStorageSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(
                Locale.getDefault(),
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
            )

            else -> String.format(
                Locale.getDefault(),
                "%.2f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
            )
        }
    }

    data class DeviceStorageStats(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usagePercentage: Float,
    )

    fun getDeviceStorageStats(): DeviceStorageStats {
        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)

        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        return DeviceStorageStats(
            totalBytes = totalBytes,
            freeBytes = availableBytes,
            usedBytes = usedBytes,
            usagePercentage = usedBytes.toFloat() / totalBytes.toFloat(),
        )
    }
}

data class DownloadsUiState(
    val activeDownloads: List<DownloadInfo> = emptyList(),
    val completedDownloads: List<DownloadInfo> = emptyList(),
    val absActiveDownloads: List<AbsDownloadInfo> = emptyList(),
    val absCompletedDownloads: List<AbsDownloadInfo> = emptyList(),
    val totalStorageUsed: Long = 0L,
    val totalStorageUsedAllServers: Long = 0L,
    val downloadOverWifiOnly: Boolean = true,
    val isImageCacheEnabled: Boolean = true,
    val imageCacheSizeMb: Int = 512,
    val deviceStorageStats: DownloadsViewModel.DeviceStorageStats? = null,
    val error: String? = null,
)
