package com.makd.afinity.data.models.server

import kotlinx.serialization.Serializable

@Serializable
enum class StorageFolderKind {
    PROGRAM_DATA,
    METADATA,
    TRANSCODING_TEMP,
    CACHE,
    IMAGE_CACHE,
    LOGS,
    WEB,
}

@Serializable data class StorageFolder(val kind: StorageFolderKind?, val path: String)

@Serializable
data class StorageDevice(
    val label: String,
    val storageType: String?,
    val freeSpace: Long,
    val usedSpace: Long,
    val folders: List<StorageFolder>,
    val libraries: List<String>,
) {
    val totalSpace: Long
        get() = freeSpace + usedSpace

    val usedFraction: Float
        get() = if (totalSpace > 0) usedSpace.toFloat() / totalSpace.toFloat() else 0f
}

@Serializable
data class ServerStorage(val devices: List<StorageDevice> = emptyList()) {
    val isEmpty: Boolean
        get() = devices.isEmpty()
}
