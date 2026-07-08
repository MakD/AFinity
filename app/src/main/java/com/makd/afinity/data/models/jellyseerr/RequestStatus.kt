package com.makd.afinity.data.models.jellyseerr

enum class RequestStatus(val value: Int) {
    PENDING(1),
    APPROVED(2),
    DECLINED(3),
    FAILED(4),
    COMPLETED(5);

    companion object {
        fun fromValue(value: Int): RequestStatus = values().find { it.value == value } ?: PENDING

        fun getDisplayName(status: RequestStatus): String =
            when (status) {
                PENDING -> "Pending"
                APPROVED -> "Approved"
                DECLINED -> "Declined"
                FAILED -> "Failed"
                COMPLETED -> "Completed"
            }
    }
}
