package com.makd.afinity.data.database.dao

/** Aggregated download storage usage for a single storage volume. */
data class VolumeUsage(
    val storageVolumeId: String,
    val totalBytes: Long,
)
