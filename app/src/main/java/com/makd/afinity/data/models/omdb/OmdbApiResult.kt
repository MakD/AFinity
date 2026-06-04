package com.makd.afinity.data.models.omdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OmdbApiResult(
    @SerialName("Awards") val awards: String? = null,
    @SerialName("Response") val response: String? = null,
    @SerialName("Error") val error: String? = null,
)
