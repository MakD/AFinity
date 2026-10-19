package com.makd.afinity.data.models

import androidx.annotation.StringRes
import com.makd.afinity.R
import com.makd.afinity.data.models.media.LibraryFilters
import java.util.UUID
import kotlinx.serialization.Serializable

enum class HeroContentMode(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    RANDOM(R.string.hero_content_random, R.string.hero_content_random_desc),
    RECOMMENDED(R.string.hero_content_recommended, R.string.hero_content_recommended_desc),
    RECENTLY_ADDED(R.string.hero_content_recently_added, R.string.hero_content_recently_added_desc),
    COLLECTION(R.string.hero_content_collection, R.string.hero_content_collection_desc),
    CUSTOM_FILTER(R.string.hero_content_custom_filter, R.string.hero_content_custom_filter_desc);

    val sourceTypes: List<CustomSectionSourceType>
        get() =
            when (this) {
                COLLECTION ->
                    listOf(CustomSectionSourceType.COLLECTION, CustomSectionSourceType.PLAYLIST)
                CUSTOM_FILTER ->
                    listOf(
                        CustomSectionSourceType.GENRE,
                        CustomSectionSourceType.STUDIO,
                        CustomSectionSourceType.TAG,
                    )
                else -> emptyList()
            }

    val usesLibraryFilters: Boolean
        get() = sourceTypes.isEmpty()
}

enum class HeroIndicator(@param:StringRes val labelRes: Int) {
    DOTS(R.string.hero_indicator_dots),
    COUNTER(R.string.hero_indicator_counter),
    HIDDEN(R.string.hero_indicator_hidden),
}

@Serializable
data class HeroContentSource(
    val mode: HeroContentMode = HeroContentMode.RANDOM,
    val sourceType: CustomSectionSourceType? = null,
    val sourceValues: List<String> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val slideSeconds: Int = DEFAULT_SLIDE_SECONDS,
    val slideCount: Int = DEFAULT_SLIDE_COUNT,
    val indicator: HeroIndicator = HeroIndicator.DOTS,
    val includeWatched: Boolean = false,
    val maxOfficialRating: String? = null,
    val libraryIds: List<String> = emptyList(),
) {
    val isComplete: Boolean
        get() = mode.sourceTypes.isEmpty() || toCustomSection() != null

    val libraryUuids: List<UUID>
        get() = libraryIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    fun normalized(): HeroContentSource =
        if (mode.sourceTypes.isEmpty() || sourceType in mode.sourceTypes) this
        else copy(sourceType = mode.sourceTypes.first(), sourceValues = emptyList())

    fun withMode(newMode: HeroContentMode): HeroContentSource =
        if (newMode == mode) this
        else
            copy(
                mode = newMode,
                sourceType = newMode.sourceTypes.firstOrNull(),
                sourceValues = emptyList(),
                filters = LibraryFilters(),
            )

    fun withSourceType(newSourceType: CustomSectionSourceType): HeroContentSource =
        if (newSourceType == sourceType) this
        else
            copy(sourceType = newSourceType, sourceValues = emptyList(), filters = LibraryFilters())

    fun toCustomSection(): CustomHomeSection? {
        val type = sourceType?.takeIf { it in mode.sourceTypes } ?: return null
        if (sourceValues.isEmpty()) return null
        return CustomHomeSection(
            id = HERO_SECTION_ID,
            position = -1,
            title = "",
            sourceType = type,
            sourceValues = sourceValues,
            randomOrder = true,
            filters = filters.scopedTo(type),
        )
    }

    companion object {
        const val HERO_SECTION_ID = "hero_carousel"
        const val DEFAULT_SLIDE_SECONDS = 5
        const val MAX_SLIDE_SECONDS = 30
        const val DEFAULT_SLIDE_COUNT = 15
        const val MIN_SLIDE_COUNT = 1
        const val MAX_SLIDE_COUNT = 30
    }
}
