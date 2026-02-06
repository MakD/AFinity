package com.makd.afinity.data.models.jellyseerr

enum class MediaStatus(val value: Int) {
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5),
    DELETED(6);

    companion object {
        fun fromValue(value: Int): MediaStatus = values().find { it.value == value } ?: UNKNOWN

        fun getDisplayName(status: MediaStatus): String =
            when (status) {
                UNKNOWN -> "Unknown"
                PENDING -> "Pending"
                PROCESSING -> "Processing"
                PARTIALLY_AVAILABLE -> "Partially Available"
                AVAILABLE -> "Available"
                DELETED -> "Deleted"
            }
    }
}
