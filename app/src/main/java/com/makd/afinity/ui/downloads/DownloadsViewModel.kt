package com.makd.afinity.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        loadStorageInfo()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.getActiveDownloadsFlow()
                    .catch { e ->
                        Timber.e(e, "Error observing active downloads")
                    }
                    .collect { activeDownloads ->
                        _uiState.value = _uiState.value.copy(
                            activeDownloads = activeDownloads
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe active downloads")
            }
        }

        viewModelScope.launch {
            try {
                downloadRepository.getCompletedDownloadsFlow()
                    .catch { e ->
                        Timber.e(e, "Error observing completed downloads")
                    }
                    .collect { completedDownloads ->
                        _uiState.value = _uiState.value.copy(
                            completedDownloads = completedDownloads
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe completed downloads")
            }
        }
    }

    private fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val storageUsed = downloadRepository.getTotalStorageUsed()
                _uiState.value = _uiState.value.copy(
                    totalStorageUsed = storageUsed
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
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to pause download: ${error.message}"
                    )
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
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to resume download: ${error.message}"
                    )
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
                result.onSuccess {
                    Timber.i("Download cancelled successfully")
                    loadStorageInfo()
                }.onFailure { error ->
                    Timber.e(error, "Failed to cancel download")
                    _uiState.value = _uiState.value.copy(
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
                result.onSuccess {
                    Timber.i("Download deleted successfully")
                    loadStorageInfo()
                }.onFailure { error ->
                    Timber.e(error, "Failed to delete download")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete download: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting download")
            }
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
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

data class DownloadsUiState(
    val activeDownloads: List<DownloadInfo> = emptyList(),
    val completedDownloads: List<DownloadInfo> = emptyList(),
    val totalStorageUsed: Long = 0L,
    val error: String? = null
)
