package com.makd.afinity.data.storage

/**
 * Thrown when an operation targets a storage volume that is not currently mounted (e.g. an SD card
 * that has been removed). Carries the user-visible volume name when known so the UI can surface a
 * helpful message.
 */
class VolumeUnavailableException(
    val volumeId: String,
    val volumeName: String?,
) : Exception("Storage volume '${volumeName ?: volumeId}' is not available")
