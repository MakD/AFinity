package com.makd.afinity.data.models.mdblist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MdbListUser(
    @SerialName("api_requests") val apiRequests: Int? = null,
    @SerialName("api_requests_count") val apiRequestsCount: Int? = null,
    @SerialName("patron_status") val patronStatus: String? = null,
)

data class MdbListUsage(val used: Int, val limit: Int) {
    val remaining: Int
        get() = (limit - used).coerceAtLeast(0)
}