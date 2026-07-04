package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuotaStatus(
    @SerialName("days") val days: Int? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("used") val used: Int = 0,
    @SerialName("remaining") val remaining: Int? = null,
    @SerialName("restricted") val restricted: Boolean = false,
) {
    fun hasLimit(): Boolean = (limit ?: 0) > 0
}

@Serializable
data class UserQuotaResponse(
    @SerialName("movie") val movie: QuotaStatus = QuotaStatus(),
    @SerialName("tv") val tv: QuotaStatus = QuotaStatus(),
)