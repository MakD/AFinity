package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PublicSettings(
    @SerialName("partialRequestsEnabled") val partialRequestsEnabled: Boolean = true,
    @SerialName("enableSpecialEpisodes") val enableSpecialEpisodes: Boolean = false,
    @SerialName("movie4kEnabled") val movie4kEnabled: Boolean = false,
    @SerialName("series4kEnabled") val series4kEnabled: Boolean = false,
    @SerialName("localLogin") val localLogin: Boolean = true,
    @SerialName("hideAvailable") val hideAvailable: Boolean = false,
    @SerialName("applicationTitle") val applicationTitle: String? = null,
    @SerialName("applicationUrl") val applicationUrl: String? = null,
)