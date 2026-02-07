package com.makd.afinity.ui.settings.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.models.UpdateState

@Composable
fun GlobalUpdateDialog(updateManager: UpdateManager) {
    val updateState by updateManager.updateState.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var hasShownForCurrentUpdate by remember { mutableStateOf(false) }

    when (val state = updateState) {
        is UpdateState.Available -> {
            if (!hasShownForCurrentUpdate) {
                showDialog = true
                hasShownForCurrentUpdate = true
            }
        }

        is UpdateState.Downloaded -> {
            if (!hasShownForCurrentUpdate) {
                showDialog = true
                hasShownForCurrentUpdate = true
            }
        }

        UpdateState.Idle,
        UpdateState.UpToDate -> {
            hasShownForCurrentUpdate = false
        }

        else -> {
            // Keep current state for Checking, Downloading, Error
        }
    }

    if (showDialog) {
        val release =
            when (val state = updateState) {
                is UpdateState.Available -> state.release
                is UpdateState.Downloaded -> state.release
                else -> null
            }

        val downloadedFile =
            when (val state = updateState) {
                is UpdateState.Downloaded -> state.file
                else -> null
            }

        if (release != null) {
            UpdateAvailableDialog(
                currentVersionName = BuildConfig.VERSION_NAME,
                release = release,
                downloadedFile = downloadedFile,
                isDownloading = updateState is UpdateState.Downloading,
                onDownload = { updateManager.downloadUpdate(release) },
                onInstall = { file ->
                    updateManager.installUpdate(file)
                    showDialog = false
                    updateManager.resetState()
                },
                onDismiss = {
                    showDialog = false
                    updateManager.resetState()
                },
            )
        }
    }
}
