package com.makd.afinity.ui.item.delegates

import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadQuality
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.storage.StorageVolumeInfo
import com.makd.afinity.data.storage.VolumeUnavailableException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ItemDownloadDelegate
@Inject
constructor(
    private val downloadRepository: DownloadRepository,
    private val storageLocationProvider: StorageLocationProvider,
    private val preferencesRepository: PreferencesRepository,
) {
    fun onDownloadClick(scope: CoroutineScope, item: AfinityItem?, showQualityDialog: () -> Unit) {
        val target = item ?: return
        scope.launch {
            try {
                val sources = target.sources.filter { it.type == AfinitySourceType.REMOTE }
                if (sources.isEmpty()) {
                    Timber.w("No remote sources available for download for item: ${target.name}")
                    return@launch
                }
                if (sources.size == 1) {
                    downloadRepository
                        .startDownload(target.id, sources.first().id)
                        .onSuccess { Timber.i("Download started successfully for: ${target.name}") }
                        .onFailure { error -> Timber.e(error, "Failed to start download") }
                } else {
                    showQualityDialog()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download")
            }
        }
    }

    /**
     * Long-press entry point for a single (leaf) item. Always opens the version/location picker
     * when the item has remote sources, letting the user choose a quality version and/or a storage
     * volume. The available volumes and current defaults are resolved off the main thread and
     * handed back via [showPicker] so the caller can populate its dialog state.
     */
    fun onDownloadLongClick(
        scope: CoroutineScope,
        item: AfinityItem?,
        showPicker:
            (
                volumes: List<StorageVolumeInfo>,
                defaultVolumeId: String?,
                defaultQualityBitrate: Int,
            ) -> Unit,
    ) {
        val target = item ?: return
        scope.launch {
            try {
                val sources = target.sources.filter { it.type == AfinitySourceType.REMOTE }
                if (sources.isEmpty()) {
                    Timber.w("No remote sources available for download for item: ${target.name}")
                    return@launch
                }
                val volumes = storageLocationProvider.listVolumes()
                val defaultVolumeId = preferencesRepository.getDownloadStorageVolumeId()
                val defaultQualityBitrate = preferencesRepository.getVideoDownloadQuality()
                showPicker(volumes, defaultVolumeId, defaultQualityBitrate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download options")
            }
        }
    }

    fun onQualitySelected(
        scope: CoroutineScope,
        item: AfinityItem?,
        sourceId: String,
        volumeId: String? = null,
        quality: DownloadQuality? = null,
        hideQualityDialog: () -> Unit,
    ) {
        val target = item ?: return
        scope.launch {
            try {
                hideQualityDialog()
                downloadRepository
                    .startDownload(target.id, sourceId, volumeId, quality = quality)
                    .onSuccess { Timber.i("Download started successfully for: ${target.name}") }
                    .onFailure { error -> Timber.e(error, "Failed to start download") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error starting download")
            }
        }
    }

    fun pauseDownload(scope: CoroutineScope, info: DownloadInfo?) {
        info?.let {
            scope.launch {
                downloadRepository.pauseDownload(it.id).onFailure { error ->
                    Timber.e(error, "Failed to pause download")
                }
            }
        }
    }

    fun resumeDownload(scope: CoroutineScope, info: DownloadInfo?) {
        info?.let {
            scope.launch {
                downloadRepository.resumeDownload(it.id).onFailure { error ->
                    Timber.e(error, "Failed to resume download")
                }
            }
        }
    }

    fun cancelDownload(scope: CoroutineScope, info: DownloadInfo?) {
        info?.let {
            scope.launch {
                if (it.status == DownloadStatus.COMPLETED) {
                    deleteCompletedDownload(it)
                    return@launch
                }
                downloadRepository
                    .cancelDownload(it.id)
                    .onSuccess { Timber.i("Download cancelled successfully") }
                    .onFailure { error -> Timber.e(error, "Failed to cancel download") }
            }
        }
    }

    private suspend fun deleteCompletedDownload(info: DownloadInfo) {
        downloadRepository
            .deleteDownload(info.id)
            .onSuccess { Timber.i("Download deleted successfully") }
            .onFailure { error ->
                if (error is VolumeUnavailableException) {
                    Timber.w("Volume ${error.volumeId} unavailable, removing download record")
                    downloadRepository.removeDownloadRecord(info.id).onFailure {
                        Timber.e(it, "Failed to remove download record")
                    }
                } else {
                    Timber.e(error, "Failed to delete download")
                }
            }
    }
}
