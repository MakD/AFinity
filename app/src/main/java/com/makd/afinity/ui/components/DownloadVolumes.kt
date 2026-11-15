package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.makd.afinity.data.storage.StorageVolumeInfo
import com.makd.afinity.util.rememberPreferencesRepository
import com.makd.afinity.util.rememberStorageLocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class DownloadVolumes(val volumes: List<StorageVolumeInfo>, val defaultVolumeId: String?) {
    val hasChoice: Boolean
        get() = volumes.size > 1

    val defaultSelection: String?
        get() = volumes.firstOrNull { it.id == defaultVolumeId }?.id
}

@Composable
fun rememberDownloadVolumes(): DownloadVolumes {
    val storageLocationProvider = rememberStorageLocationProvider()
    val preferencesRepository = rememberPreferencesRepository()
    val volumes by
        produceState(DownloadVolumes(emptyList(), null), storageLocationProvider) {
            value =
                try {
                    withContext(Dispatchers.IO) {
                        DownloadVolumes(
                            volumes = storageLocationProvider.listVolumes(),
                            defaultVolumeId = preferencesRepository.getDownloadStorageVolumeId(),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to list storage volumes")
                    DownloadVolumes(emptyList(), null)
                }
        }
    return volumes
}
