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

private val ChannelListQuery.favoriteFilter: Boolean?
    get() = if (favoritesOnly) true else null

private fun ChannelListQuery.flag(category: ProgramCategory): Boolean? = categories?.let {
    if (category in it) true else null
}

suspend fun LiveTvRepository.getChannelsFor(
    query: ChannelListQuery,
    withCurrentProgram: Boolean = true,
): Result<List<AfinityChannel>> =
    getChannelsResult(
        isFavorite = query.favoriteFilter,
        isMovie = query.flag(ProgramCategory.MOVIES),
        isSeries = query.flag(ProgramCategory.SHOWS),
        isNews = query.flag(ProgramCategory.NEWS),
        isKids = query.flag(ProgramCategory.KIDS),
        isSports = query.flag(ProgramCategory.SPORTS),
        sortOrder = query.sortOrder,
        favoritesFirst = query.favoritesFirst,
        withCurrentProgram = withCurrentProgram,
    )

fun LiveTvRepository.cachedChannelsFor(query: ChannelListQuery): List<AfinityChannel>? =
    getCachedChannels(
        isFavorite = query.favoriteFilter,
        isMovie = query.flag(ProgramCategory.MOVIES),
        isSeries = query.flag(ProgramCategory.SHOWS),
        isNews = query.flag(ProgramCategory.NEWS),
        isKids = query.flag(ProgramCategory.KIDS),
        isSports = query.flag(ProgramCategory.SPORTS),
        sortOrder = query.sortOrder,
        favoritesFirst = query.favoritesFirst,
    )
