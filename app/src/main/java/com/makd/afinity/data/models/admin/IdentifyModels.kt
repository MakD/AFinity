package com.makd.afinity.data.models.admin

data class IdentifyResult(
    val name: String,
    val year: Int?,
    val imageUrl: String?,
    val searchProviderName: String?,
    val providerIds: Map<String, String>,
    val overview: String?,
    val premiereDate: String?,
)

data class ExternalIdProvider(
    val name: String,
    val key: String,
    val urlFormatString: String?,
)