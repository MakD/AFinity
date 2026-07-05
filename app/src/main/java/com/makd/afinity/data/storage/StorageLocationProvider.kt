package com.makd.afinity.data.storage

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.makd.afinity.data.storage.StorageLocationProvider.Companion.PRIMARY_VOLUME_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

    @Volatile private var lastLoggedVolumeSummary: String? = null

    /**
     * Returns the currently mounted volumes the app can use, each with its `AFinity/Downloads` base
     * directory. Volumes that are unmounted (a `null` entry in [Context.getExternalFilesDirs]) are
     * skipped. The primary volume is always included and always keyed [PRIMARY_VOLUME_ID].
     */
    fun listVolumes(): List<StorageVolumeInfo> {
        val dirs =
            try {
                context.getExternalFilesDirs(null)
            } catch (e: Exception) {
                Timber.w(e, "getExternalFilesDirs failed — no volumes available")
                return emptyList()
            }
        val result = mutableListOf<StorageVolumeInfo>()

        for (dir in dirs) {
            if (dir == null) continue
            try {
                val state = Environment.getExternalStorageState(dir)
                if (state != Environment.MEDIA_MOUNTED) {
                    Timber.d("Skipping volume at ${dir.absolutePath} — state=$state")
                    continue
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not query storage state for ${dir.absolutePath}, skipping")
                continue
            }
            try {
                val volume = storageManager.getStorageVolume(dir) ?: continue
                val isPrimary = volume.isPrimary
                val id =
                    if (isPrimary) PRIMARY_VOLUME_ID else volume.uuid ?: "vol:${dir.absolutePath}"
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

        if (result.none { it.isPrimary }) {
            val external =
                try {
                    context.getExternalFilesDir(null)
                } catch (e: Exception) {
                    Timber.w(e, "getExternalFilesDir failed while synthesizing primary volume")
                    null
                }
            result.add(
                0,
                StorageVolumeInfo(
                    id = PRIMARY_VOLUME_ID,
                    displayName = "Internal storage",
                    isRemovable = false,
                    isPrimary = true,
                    baseDir = File(external ?: context.filesDir, DOWNLOADS_SUBPATH),
                ),
            )
        }

        val summary =
            "${dirs.size} dir(s) reported, ${result.size} usable: " +
                result.joinToString {
                    "${it.id} [primary=${it.isPrimary}, removable=${it.isRemovable}, path=${it.baseDir.absolutePath}]"
                }
        if (summary != lastLoggedVolumeSummary) {
            lastLoggedVolumeSummary = summary
            Timber.i("Storage volumes: $summary")
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
     * Whether the given volume is currently mounted and writable. Delegates to [resolveBaseDir] for
     * all volumes including primary — a non-null base dir means the volume passed the
     * [Environment.getExternalStorageState] check in [listVolumes].
     */
    fun isVolumeAvailable(volumeId: String): Boolean = resolveBaseDir(volumeId) != null

    /** Stable ids of all currently mounted volumes. */
    fun mountedVolumeIds(): Set<String> = listVolumes().map { it.id }.toSet()

    /** User-visible name for a volume id, or `null` when it is not currently mounted. */
    fun displayNameFor(volumeId: String): String? =
        listVolumes().firstOrNull { it.id == volumeId }?.displayName

    /**
     * The primary volume's base directory, used as the fallback when a stored volume id can no
     * longer be resolved.
     *
     * Falls back through three levels:
     * 1. Primary volume resolved via [listVolumes] (state-checked, preferred)
     * 2. [Context.getExternalFilesDir] — may return null when external storage is unavailable
     * 3. [Context.getFilesDir] — internal storage, always non-null and always writable
     */
    fun primaryBaseDir(): File {
        resolveBaseDir(PRIMARY_VOLUME_ID)?.let {
            return it
        }
        val external =
            try {
                context.getExternalFilesDir(null)
            } catch (e: Exception) {
                Timber.w(e, "getExternalFilesDir failed in primaryBaseDir fallback")
                null
            }
        return File(external ?: context.filesDir, DOWNLOADS_SUBPATH)
    }
}
