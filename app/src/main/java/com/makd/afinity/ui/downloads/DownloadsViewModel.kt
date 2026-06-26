package com.makd.afinity.ui.downloads

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.storage.StorageVolumeInfo
import com.makd.afinity.data.storage.VolumeUnavailableException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val absDownloadRepository: AbsDownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val storageLocationProvider: StorageLocationProvider,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        loadStorageInfo()
        loadDownloadPreferences()
        loadStorageVolumes()
    }

    private fun loadStorageVolumes() {
        viewModelScope.launch {
            try {
                val volumes = storageLocationProvider.listVolumes()
                val defaultVolumeId = preferencesRepository.getDownloadStorageVolumeId()
                _uiState.value =
                    _uiState.value.copy(
                        availableVolumes = volumes,
                        defaultStorageVolumeId = defaultVolumeId,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load storage volumes")
            }
        }
    }

    fun setDefaultStorageVolume(volumeId: String) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDownloadStorageVolumeId(volumeId)
                _uiState.value = _uiState.value.copy(defaultStorageVolumeId = volumeId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update default storage volume")
            }
        }
    }

    private fun loadDownloadPreferences() {
        viewModelScope.launch {
            try {
                val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()
                val isImageCacheEnabled = preferencesRepository.getImageCacheEnabled()
                val imageCacheSizeMb = preferencesRepository.getImageCacheSizeMb()
                val maxConcurrentDownloads = preferencesRepository.getMaxDownloads()

                _uiState.value =
                    _uiState.value.copy(
                        downloadOverWifiOnly = wifiOnly,
                        isImageCacheEnabled = isImageCacheEnabled,
                        imageCacheSizeMb = imageCacheSizeMb,
                        maxConcurrentDownloads = maxConcurrentDownloads,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load download preferences")
            }
        }
    }

    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            try {
                preferencesRepository.setMaxDownloads(count)
                _uiState.value = _uiState.value.copy(maxConcurrentDownloads = count)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update max concurrent downloads preference")
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
                val jellyfinStorageUsed = downloadRepository.getTotalStorageUsed()
                val jellyfinAllServersStorageUsed =
                    downloadRepository.getTotalStorageUsedAllServers()
                val absStorageUsed = absDownloadRepository.getTotalStorageUsed()
                val absAllServersStorageUsed = absDownloadRepository.getTotalStorageUsedAllServers()
                val deviceStats = getDeviceStorageStats()
                val perVolume = getPerVolumeStorageStats()
                _uiState.value =
                    _uiState.value.copy(
                        totalStorageUsed = jellyfinStorageUsed + absStorageUsed,
                        totalStorageUsedAllServers =
                            jellyfinAllServersStorageUsed + absAllServersStorageUsed,
                        deviceStorageStats = deviceStats,
                        volumeStorageStats = perVolume,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load storage info")
            }
        }
    }

    private suspend fun getPerVolumeStorageStats(): List<VolumeStorageStats> {
        val volumes = storageLocationProvider.listVolumes()
        val usedThisServer =
            mergeUsage(
                downloadRepository.getStorageUsedPerVolume(),
                absDownloadRepository.getStorageUsedPerVolume(),
            )
        val usedAllServers =
            mergeUsage(
                downloadRepository.getStorageUsedPerVolumeAllServers(),
                absDownloadRepository.getStorageUsedPerVolumeAllServers(),
            )
        val mountedStats = volumes.mapNotNull { volume ->
            val device =
                try {
                    // StatFs throws on a non-existent path; ensure the base dir exists first.
                    if (!volume.baseDir.exists()) volume.baseDir.mkdirs()
                    val statPath =
                        if (volume.baseDir.exists()) volume.baseDir else volume.baseDir.parentFile
                    val stat = StatFs((statPath ?: volume.baseDir).path)
                    val totalBytes = stat.totalBytes
                    val availableBytes = stat.availableBytes
                    DeviceStorageStats(
                        totalBytes = totalBytes,
                        freeBytes = availableBytes,
                        usedBytes = totalBytes - availableBytes,
                        usagePercentage =
                            if (totalBytes > 0)
                                (totalBytes - availableBytes).toFloat() / totalBytes.toFloat()
                            else 0f,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to stat volume ${volume.id}")
                    return@mapNotNull null
                }
            VolumeStorageStats(
                volumeId = volume.id,
                displayName = volume.displayName,
                isRemovable = volume.isRemovable,
                isAvailable = true,
                usedThisServer = usedThisServer[volume.id] ?: 0L,
                usedAllServers = usedAllServers[volume.id] ?: 0L,
                device = device,
            )
        }

        // Any volume that still has downloads recorded against it but is no longer mounted
        // (e.g. an SD card that was removed) gets a synthetic "unavailable" card so the user can
        // see the orphaned usage and manage those entries.
        val mountedIds = mountedStats.map { it.volumeId }.toSet()
        val unavailableStats =
            (usedThisServer.keys + usedAllServers.keys)
                .filter { it !in mountedIds }
                .map { volumeId ->
                    VolumeStorageStats(
                        volumeId = volumeId,
                        displayName = context.getString(R.string.storage_unavailable_volume),
                        isRemovable = true,
                        isAvailable = false,
                        usedThisServer = usedThisServer[volumeId] ?: 0L,
                        usedAllServers = usedAllServers[volumeId] ?: 0L,
                        device = null,
                    )
                }

        return mountedStats + unavailableStats
    }

    private fun mergeUsage(vararg usageMaps: Map<String, Long>): Map<String, Long> = buildMap {
        usageMaps.forEach { usage ->
            usage.forEach { (volumeId, bytes) ->
                put(volumeId, (get(volumeId) ?: 0L) + bytes)
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
                        if (error is VolumeUnavailableException) {
                            // Files live on storage that isn't mounted; we can't delete them now.
                            // Surface a confirmation so the user can drop the list entry anyway.
                            Timber.w("Delete blocked: volume ${error.volumeId} unavailable")
                            _uiState.value =
                                _uiState.value.copy(pendingUnavailableDelete = downloadId)
                        } else {
                            Timber.e(error, "Failed to delete download")
                            _uiState.value =
                                _uiState.value.copy(
                                    error = "Failed to delete download: ${error.message}"
                                )
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting download")
            }
        }
    }

    fun dismissUnavailableDelete() {
        _uiState.value = _uiState.value.copy(pendingUnavailableDelete = null)
    }

    fun confirmRemoveUnavailableDelete() {
        val downloadId = _uiState.value.pendingUnavailableDelete ?: return
        _uiState.value = _uiState.value.copy(pendingUnavailableDelete = null)
        viewModelScope.launch {
            try {
                downloadRepository
                    .removeDownloadRecord(downloadId)
                    .onSuccess {
                        Timber.i("Download record removed for unavailable volume")
                        loadStorageInfo()
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to remove download record")
                        _uiState.value =
                            _uiState.value.copy(error = "Failed to remove entry: ${error.message}")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error removing download record")
            }
        }
    }

    fun cancelAbsDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                absDownloadRepository.cancelDownload(downloadId).onFailure {
                    Timber.e(it, "Failed to cancel ABS download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling ABS download")
            }
        }
    }

    fun deleteAbsDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                absDownloadRepository
                    .deleteDownload(downloadId)
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

    fun deleteMusicAlbum(albumSeriesId: String) {
        viewModelScope.launch {
            uiState.value.completedDownloads
                .filter { it.itemType == "Audio" && it.seriesId == albumSeriesId }
                .forEach {
                    downloadRepository.deleteDownload(it.id)
                }
            loadStorageInfo()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun retryFailedDownload(downloadId: UUID) {
        viewModelScope.launch {
            try {
                downloadRepository
                    .resumeDownload(downloadId)
                    .onSuccess { Timber.i("Failed download requeued: $downloadId") }
                    .onFailure { error -> Timber.e(error, "Failed to retry download") }
            } catch (e: Exception) {
                Timber.e(e, "Error retrying failed download")
            }
        }
    }

    fun formatStorageSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 ->
                String.format(
                    Locale.getDefault(),
                    "%.2f MB",
                    bytes / (1024.0 * 1024.0),
                )

            else ->
                String.format(
                    Locale.getDefault(),
                    "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0),
                )
        }
    }

    data class DeviceStorageStats(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usagePercentage: Float,
    )

    data class VolumeStorageStats(
        val volumeId: String,
        val displayName: String,
        val isRemovable: Boolean,
        val isAvailable: Boolean,
        val usedThisServer: Long,
        val usedAllServers: Long,
        val device: DeviceStorageStats?,
    )

    fun getDeviceStorageStats(): DeviceStorageStats {
        val path = storageLocationProvider.primaryBaseDir()
        if (!path.exists()) path.mkdirs()
        val statPath = if (path.exists()) path else path.parentFile ?: path
        val stat = StatFs(statPath.path)

        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        return DeviceStorageStats(
            totalBytes = totalBytes,
            freeBytes = availableBytes,
            usedBytes = usedBytes,
            usagePercentage =
                if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f,
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
    val maxConcurrentDownloads: Int = 3,
    val isImageCacheEnabled: Boolean = true,
    val imageCacheSizeMb: Int = 512,
    val deviceStorageStats: DownloadsViewModel.DeviceStorageStats? = null,
    val volumeStorageStats: List<DownloadsViewModel.VolumeStorageStats> = emptyList(),
    val availableVolumes: List<StorageVolumeInfo> = emptyList(),
    val defaultStorageVolumeId: String = StorageLocationProvider.PRIMARY_VOLUME_ID,
    val pendingUnavailableDelete: UUID? = null,
    val error: String? = null,
)
