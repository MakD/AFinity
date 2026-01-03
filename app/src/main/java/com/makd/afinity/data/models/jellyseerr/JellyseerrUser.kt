package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrUser(
    @SerialName("id")
    val id: Int,
    @SerialName("email")
    val email: String? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("displayName")
    val displayName: String? = null,
    @SerialName("permissions")
    val permissions: Int,
    @SerialName("avatar")
    val avatar: String? = null,
    @SerialName("requestCount")
    val requestCount: Int? = null,
    @SerialName("movieQuotaLimit")
    val movieQuotaLimit: Int? = null,
    @SerialName("movieQuotaDays")
    val movieQuotaDays: Int? = null,
    @SerialName("tvQuotaLimit")
    val tvQuotaLimit: Int? = null,
    @SerialName("tvQuotaDays")
    val tvQuotaDays: Int? = null
)