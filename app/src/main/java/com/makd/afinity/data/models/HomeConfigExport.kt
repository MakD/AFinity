package com.makd.afinity.data.models

import com.makd.afinity.data.models.media.LibraryFilters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val filters: LibraryFilters? = null,
)