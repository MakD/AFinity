package com.makd.afinity.data.models

import com.makd.afinity.R
import com.makd.afinity.data.models.common.SortBy

enum class CustomSectionTypeGroup(val cardStyle: CustomSectionCardStyle?) {
    VIDEO(null),
    EPISODE(CustomSectionCardStyle.LANDSCAPE),
    MUSIC(CustomSectionCardStyle.SQUARE),
}

enum class CustomSectionItemType(
    val key: String,
    val labelRes: Int,
    val group: CustomSectionTypeGroup,
) {
    MOVIE("MOVIE", R.string.custom_sections_type_movies, CustomSectionTypeGroup.VIDEO),
    SERIES("SERIES", R.string.custom_sections_type_shows, CustomSectionTypeGroup.VIDEO),
    SEASON("SEASON", R.string.custom_sections_type_seasons, CustomSectionTypeGroup.VIDEO),
    BOX_SET("BOX_SET", R.string.custom_sections_type_boxsets, CustomSectionTypeGroup.VIDEO),
    EPISODE("EPISODE", R.string.custom_sections_type_episodes, CustomSectionTypeGroup.EPISODE);

    companion object {
        fun fromKey(key: String): CustomSectionItemType? = entries.firstOrNull { it.key == key }

        fun availableFor(sourceType: CustomSectionSourceType): List<CustomSectionItemType> =
            if (sourceType == CustomSectionSourceType.PLAYLIST) {
                entries.filterNot { it == SERIES || it == SEASON }
            } else entries
    }
}

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
    SQUARE,
    SPOTLIGHT,
}

data class CustomHomeSection(
    val id: String,
    val position: Int,
    val title: String,
    val sourceType: CustomSectionSourceType,
    val sourceValues: List<String> = emptyList(),
    val includeItemTypes: List<String> = DEFAULT_ITEM_TYPES,
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

    val itemTypes: List<CustomSectionItemType>
        get() = includeItemTypes.mapNotNull { CustomSectionItemType.fromKey(it) }

    val typeGroup: CustomSectionTypeGroup
        get() = itemTypes.firstOrNull()?.group ?: CustomSectionTypeGroup.VIDEO

    val isEpisodeSection: Boolean
        get() = typeGroup == CustomSectionTypeGroup.EPISODE

    val lockedCardStyle: CustomSectionCardStyle?
        get() = typeGroup.cardStyle

    val effectiveCardStyle: CustomSectionCardStyle
        get() = lockedCardStyle ?: cardStyle

    fun withSanitizedItemTypes(): CustomHomeSection {
        val allowed = CustomSectionItemType.availableFor(sourceType)
        val kept = itemTypes.filter { it in allowed }
        if (kept == itemTypes && kept.isNotEmpty()) return this
        val next =
            kept.ifEmpty { allowed.filter { it.key in DEFAULT_ITEM_TYPES }.ifEmpty { listOf(allowed.first()) } }
        return copy(includeItemTypes = next.map { it.key })
    }

    fun withSourceType(newSourceType: CustomSectionSourceType): CustomHomeSection =
        copy(sourceType = newSourceType, sourceValues = emptyList()).withSanitizedItemTypes()

    fun withItemTypeToggled(type: CustomSectionItemType): CustomHomeSection {
        val current = itemTypes
        val next =
            when {
                type in current && current.size == 1 -> current
                type in current -> current - type
                current.none { it.group == type.group } -> listOf(type)
                else -> current + type
            }
        return copy(includeItemTypes = next.map { it.key })
    }

    companion object {
        const val DEFAULT_ITEM_LIMIT = 20
        const val MAX_SECTIONS = 20
        val DEFAULT_ITEM_TYPES =
            listOf(CustomSectionItemType.MOVIE.key, CustomSectionItemType.SERIES.key)
        const val SOURCE_DELIMITER = "\u001F"
    }
}