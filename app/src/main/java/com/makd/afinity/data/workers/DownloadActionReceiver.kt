package com.makd.afinity.data.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAUSE = "com.makd.afinity.action.DOWNLOAD_PAUSE"
        const val ACTION_CANCEL = "com.makd.afinity.action.DOWNLOAD_CANCEL"
        const val ACTION_ABS_CANCEL = "com.makd.afinity.action.ABS_DOWNLOAD_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }

    @Inject lateinit var downloadRepository: DownloadRepository

    @Inject lateinit var absDownloadRepository: AbsDownloadRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val downloadId =
            intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PAUSE ->
                        downloadRepository.pauseDownload(downloadId).onFailure {
                            Timber.e(it, "Failed to pause download from notification")
                        }
                    ACTION_CANCEL ->
                        downloadRepository.cancelDownload(downloadId).onFailure {
                            Timber.e(it, "Failed to cancel download from notification")
                        }
                    ACTION_ABS_CANCEL ->
                        absDownloadRepository.cancelDownload(downloadId).onFailure {
                            Timber.e(it, "Failed to cancel ABS download from notification")
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Download notification action failed")
            } finally {
                pendingResult.finish()
            }
        }
    }
}