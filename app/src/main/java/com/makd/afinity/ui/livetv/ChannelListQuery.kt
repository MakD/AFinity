package com.makd.afinity.ui.livetv

import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.ChannelSortOrder
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.models.livetv.ProgramCategory
import com.makd.afinity.data.repository.livetv.LiveTvRepository

data class ChannelListQuery(
    val sortOrder: ChannelSortOrder,
    val favoritesFirst: Boolean,
    val favoritesOnly: Boolean,
    val categories: Set<ProgramCategory>?,
)

fun LiveTvViewOptions.channelListQuery(includeCategories: Boolean): ChannelListQuery =
    ChannelListQuery(
        sortOrder = channelSort,
        favoritesFirst = favoritesFirst,
        favoritesOnly = favoritesOnly,
        categories =
            guideCategories.takeIf {
                includeCategories && !it.containsAll(ProgramCategory.entries)
            },
    )

suspend fun LiveTvRepository.getChannelsFor(
    query: ChannelListQuery
): Result<List<AfinityChannel>> {
    fun flag(category: ProgramCategory): Boolean? =
        query.categories?.let { if (category in it) true else null }

    return getChannelsResult(
        isFavorite = if (query.favoritesOnly) true else null,
        isMovie = flag(ProgramCategory.MOVIES),
        isSeries = flag(ProgramCategory.SHOWS),
        isNews = flag(ProgramCategory.NEWS),
        isKids = flag(ProgramCategory.KIDS),
        isSports = flag(ProgramCategory.SPORTS),
        sortOrder = query.sortOrder,
        favoritesFirst = query.favoritesFirst,
    )
}
