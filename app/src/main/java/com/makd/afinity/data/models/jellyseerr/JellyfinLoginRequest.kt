package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinLoginRequest(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)
