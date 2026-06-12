package com.makd.afinity.data.storage

import java.io.File

/**
 * Describes a storage volume the app can download to.
 *
 * @property id Stable key persisted in the DB and preferences.
 *   [StorageLocationProvider.PRIMARY_VOLUME_ID] for the primary volume, otherwise the volume UUID.
 * @property displayName User-visible name (e.g. "Internal storage", "SD card").
 * @property isRemovable Whether the volume is removable (SD card / USB-OTG).
 * @property isPrimary Whether this is the primary (internal) volume.
 * @property baseDir The `AFinity/Downloads` directory on this volume.
 */
data class StorageVolumeInfo(
    val id: String,
    val displayName: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val baseDir: File,
)
