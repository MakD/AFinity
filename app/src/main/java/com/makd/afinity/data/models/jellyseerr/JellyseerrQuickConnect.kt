package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuickConnectInitiateResponse(
    @SerialName("code") val code: String,
    @SerialName("secret") val secret: String,
)

@Serializable
data class QuickConnectAuthenticateRequest(@SerialName("secret") val secret: String)
