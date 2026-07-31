package com.makd.afinity.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeConfigExport(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val hiddenRows: List<String> = emptyList(),
    val discovery: DiscoveryExport = DiscoveryExport(),
    val customSections: List<CustomSectionExport> = emptyList(),
) {
    companion object {
        const val FORMAT = "afinity.home"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class DiscoveryExport(
    val density: String = DiscoveryDensity.default.key,
    val disabled: List<String> = emptyList(),
    val overrides: Map<String, Int> = emptyMap(),
)

@Serializable
data class CustomSectionExport(
    val title: String,
    val sourceType: String,
    val sourceValues: List<String> = emptyList(),
    val includeItemTypes: List<String> = emptyList(),
    val itemLimit: Int = CustomHomeSection.DEFAULT_ITEM_LIMIT,
    val sortBy: String,
    val sortDescending: Boolean = false,
    val randomOrder: Boolean = false,
    val cardStyle: String,
    val enabled: Boolean = true,
    @SerialName("seasonStart") val seasonStart: String? = null,
    @SerialName("seasonEnd") val seasonEnd: String? = null,
)