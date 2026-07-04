package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateRequestBody(
    @SerialName("mediaId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("tvdbId") val tvdbId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("seasons") val seasons: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is4k") val is4k: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("serverId") val serverId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("profileId") val profileId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("rootFolder") val rootFolder: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("languageProfileId")
    val languageProfileId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("tags") val tags: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("userId") val userId: Int? = null,
)
