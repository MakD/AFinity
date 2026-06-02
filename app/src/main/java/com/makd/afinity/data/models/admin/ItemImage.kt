package com.makd.afinity.data.models.admin

data class ItemImage(
    val imageType: String,
    val imageIndex: Int?,
    val url: String?,
    val providerName: String?,
    val width: Int,
    val height: Int,
    val communityRating: Double?,
    val voteCount: Int?,
    val language: String?,
    val isServerImage: Boolean,
    val remoteUrl: String?,
)