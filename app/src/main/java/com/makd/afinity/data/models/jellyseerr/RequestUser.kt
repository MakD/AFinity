package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestUser(
    @SerialName("id")
    val id: Int,
    @SerialName("displayName")
    val displayName: String?,
    @SerialName("avatar")
    val avatar: String?
)