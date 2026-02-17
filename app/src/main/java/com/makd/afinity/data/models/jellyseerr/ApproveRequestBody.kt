package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApproveRequestBody(
    @SerialName("serverId") val serverId: Int?,
    @SerialName("profileId") val profileId: Int?,
    @SerialName("rootFolder") val rootFolder: String?,
)
