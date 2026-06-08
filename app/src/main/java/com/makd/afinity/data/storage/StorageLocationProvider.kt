package com.makd.afinity.data.storage

import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Resolves the available storage volumes (internal + removable SD cards / USB-OTG) that the app can
 * write downloads to, keyed by a stable volume id.
 *
 * The primary volume is always keyed as [PRIMARY_VOLUME_ID]; removable volumes are keyed by their
 * [android.os.storage.StorageVolume.getUuid] (the FAT serial, e.g. `ABCD-1234`), which is stable
 * across reboots and only changes on reformat.
 */
@Singleton
class StorageLocationProvider
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        const val PRIMARY_VOLUME_ID = "primary"
        private const val DOWNLOADS_SUBPATH = "AFinity/Downloads"
    }

    private val storageManager: StorageManager by lazy {
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    /**
     * Returns the currently mounted volumes the app can use, each with its `AFinity/Downloads`
     * base directory. Volumes that are unmounted (a `null` entry in [Context.getExternalFilesDirs])
     * are skipped. The primary volume is always included and always keyed [PRIMARY_VOLUME_ID].
     */
    fun listVolumes(): List<StorageVolumeInfo> {
        val dirs = context.getExternalFilesDirs(null)
        val result = mutableListOf<StorageVolumeInfo>()

        for (dir in dirs) {
            if (dir == null) continue
            try {
                val volume = storageManager.getStorageVolume(dir) ?: continue
                val isPrimary = volume.isPrimary
                val id = if (isPrimary) PRIMARY_VOLUME_ID else volume.uuid ?: continue
                val displayName =
                    volume.getDescription(context)
                        ?: if (isPrimary) "Internal storage" else "SD card"
                result.add(
                    StorageVolumeInfo(
                        id = id,
                        displayName = displayName,
                        isRemovable = volume.isRemovable,
                        isPrimary = isPrimary,
                        baseDir = File(dir, DOWNLOADS_SUBPATH),
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to resolve storage volume for dir: ${dir.absolutePath}")
            }
        }

        return result
    }

    /**
     * Resolves the `AFinity/Downloads` base directory for the given volume id, or `null` when that
     * volume is not currently mounted (e.g. the SD card was removed).
     */
    fun resolveBaseDir(volumeId: String): File? =
        listVolumes().firstOrNull { it.id == volumeId }?.baseDir

    /**
     * Whether the given volume is currently mounted and writable. The primary volume is always
     * considered available.
     */
    fun isVolumeAvailable(volumeId: String): Boolean =
        volumeId == PRIMARY_VOLUME_ID || resolveBaseDir(volumeId) != null

    /** Stable ids of all currently mounted volumes. */
    fun mountedVolumeIds(): Set<String> = listVolumes().map { it.id }.toSet()

    /** User-visible name for a volume id, or `null` when it is not currently mounted. */
    fun displayNameFor(volumeId: String): String? =
        listVolumes().firstOrNull { it.id == volumeId }?.displayName

    /**
     * The primary (internal) volume's base directory. Always available, used as the fallback when a
     * stored volume id can no longer be resolved.
     */
    fun primaryBaseDir(): File =
        resolveBaseDir(PRIMARY_VOLUME_ID)
            ?: File(context.getExternalFilesDir(null), DOWNLOADS_SUBPATH)
}
