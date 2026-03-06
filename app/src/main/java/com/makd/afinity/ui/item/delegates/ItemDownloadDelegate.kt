package com.makd.afinity.ui.item.delegates

import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.download.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ItemDownloadDelegate @Inject constructor(private val downloadRepository: DownloadRepository) {
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
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download")
            }
        }
    }

    fun onQualitySelected(
        scope: CoroutineScope,
        item: AfinityItem?,
        sourceId: String,
        hideQualityDialog: () -> Unit,
    ) {
        val target = item ?: return
        scope.launch {
            try {
                hideQualityDialog()
                downloadRepository
                    .startDownload(target.id, sourceId)
                    .onSuccess { Timber.i("Download started successfully for: ${target.name}") }
                    .onFailure { error -> Timber.e(error, "Failed to start download") }
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
                downloadRepository
                    .cancelDownload(it.id)
                    .onSuccess { Timber.i("Download cancelled successfully") }
                    .onFailure { error -> Timber.e(error, "Failed to cancel download") }
            }
        }
    }
}
