package com.makd.afinity.data.models.jellyseerr

enum class RequestStatus(val value: Int) {
    PENDING(1),
    APPROVED(2),
    DECLINED(3),
    AVAILABLE(4),
    PARTIALLY_AVAILABLE(5);

    companion object {
        fun fromValue(value: Int): RequestStatus = values().find { it.value == value } ?: PENDING

        fun getDisplayName(status: RequestStatus): String =
            when (status) {
                PENDING -> "Pending"
                APPROVED -> "Approved"
                DECLINED -> "Declined"
                AVAILABLE -> "Available"
                PARTIALLY_AVAILABLE -> "Partially Available"
            }
    }
}
