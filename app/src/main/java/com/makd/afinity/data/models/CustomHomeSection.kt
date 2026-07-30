package com.makd.afinity.data.models

import com.makd.afinity.data.models.common.SortBy

enum class CustomSectionSourceType {
    GENRE,
    STUDIO,
    TAG,
    COLLECTION,
    PLAYLIST,
    LIBRARY;

    val supportsMultipleSources: Boolean
        get() = this == GENRE || this == STUDIO || this == TAG
}

enum class CustomSectionCardStyle {
    PORTRAIT,
    LANDSCAPE,
    SPOTLIGHT,
}

data class CustomHomeSection(
    val id: String,
    val position: Int,
    val title: String,
    val sourceType: CustomSectionSourceType,
    val sourceValues: List<String> = emptyList(),
    val includeItemTypes: List<String> = emptyList(),
    val itemLimit: Int = DEFAULT_ITEM_LIMIT,
    val sortBy: SortBy = SortBy.NAME,
    val sortDescending: Boolean = false,
    val randomOrder: Boolean = false,
    val cardStyle: CustomSectionCardStyle = CustomSectionCardStyle.PORTRAIT,
    val enabled: Boolean = true,
    val seasonStart: String? = null,
    val seasonEnd: String? = null,
) {
    val isSeasonal: Boolean
        get() = seasonStart != null && seasonEnd != null

    val primarySourceValue: String?
        get() = sourceValues.firstOrNull()

    companion object {
        const val DEFAULT_ITEM_LIMIT = 20
        const val MAX_SECTIONS = 20
        const val SOURCE_DELIMITER = "\u001F"
    }
}