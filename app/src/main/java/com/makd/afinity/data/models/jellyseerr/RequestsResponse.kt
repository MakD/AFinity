package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestsResponse(
    @SerialName("pageInfo")
    val pageInfo: PageInfo,
    @SerialName("results")
    val results: List<JellyseerrRequest>
)

@Serializable
data class PageInfo(
    @SerialName("pages")
    val pages: Int,
    @SerialName("pageSize")
    val pageSize: Int,
    @SerialName("results")
    val results: Int,
    @SerialName("page")
    val page: Int
)