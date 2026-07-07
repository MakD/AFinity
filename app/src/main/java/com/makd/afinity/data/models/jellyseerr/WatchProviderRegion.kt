package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchProviderRegion(
    @SerialName("iso_3166_1") val isoCode: String,
    @SerialName("english_name") val englishName: String,
)