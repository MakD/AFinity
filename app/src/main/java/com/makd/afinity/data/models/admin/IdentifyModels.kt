package com.makd.afinity.data.models.admin

data class IdentifyResult(
    val name: String,
    val year: Int?,
    val imageUrl: String?,
    val searchProviderName: String?,
    val providerIds: Map<String, String>,
    val overview: String?,
    val premiereDate: String?,
    val albumArtist: String? = null,
)

data class IdentifyTarget(
    val name: String,
    val year: Int?,
    val type: String,
    val path: String?,
    val imageUrl: String?,
    val albumArtist: String? = null,
)

data class ExternalIdProvider(val name: String, val key: String, val type: String? = null)
