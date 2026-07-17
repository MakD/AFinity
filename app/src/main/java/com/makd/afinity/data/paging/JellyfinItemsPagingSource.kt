package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryFeature
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.VideoTypeFilter
import com.makd.afinity.data.repository.media.MediaRepository
import timber.log.Timber
import java.util.UUID

class JellyfinItemsPagingSource(
    private val mediaRepository: MediaRepository,
    private val parentId: UUID?,
    private val libraryType: CollectionType,
    private val sortBy: SortBy,
    private val sortDescending: Boolean,
    private val filters: LibraryFilters,
    private val baseUrl: String,
    private val nameStartsWith: String? = null,
    private val studioName: String? = null,
) : PagingSource<Int, AfinityItem>() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityItem> {
        return try {
            val page = params.key ?: 0
            val startIndex = page * PAGE_SIZE

            Timber.d(
                "PagingSource load: page=$page, nameStartsWith='$nameStartsWith', libraryType=$libraryType"
            )

            val includeTypes =
                when (libraryType) {
                    CollectionType.TvShows -> listOf("SERIES")
                    CollectionType.Movies -> listOf("MOVIE")
                    CollectionType.BoxSets -> listOf("BOX_SET")
                    else -> listOf("MOVIE", "SERIES", "BOX_SET", "FOLDER")
                }

            val isPlayed =
                when {
                    filters.played && !filters.unplayed -> true
                    filters.unplayed && !filters.played -> false
                    else -> null
                }

            val videoTypeNames = buildList {
                if (VideoTypeFilter.BLU_RAY in filters.videoTypes) add("BluRay")
                if (VideoTypeFilter.DVD in filters.videoTypes) add("Dvd")
            }
            val isHd =
                when {
                    VideoTypeFilter.HD in filters.videoTypes &&
                        VideoTypeFilter.SD !in filters.videoTypes -> true
                    VideoTypeFilter.SD in filters.videoTypes &&
                        VideoTypeFilter.HD !in filters.videoTypes -> false
                    else -> null
                }

            val response =
                mediaRepository
                    .getItemsResult(
                        parentId = parentId,
                    sortBy = sortBy,
                    sortDescending = sortDescending,
                    limit = PAGE_SIZE,
                    startIndex = startIndex,
                    includeItemTypes = includeTypes,
                    genres = filters.genres.toList(),
                    years = filters.years.toList(),
                    isFavorite = if (filters.favorites) true else null,
                    isPlayed = isPlayed,
                    isLiked = if (filters.watchlist) true else null,
                    isResumable = if (filters.resumable) true else null,
                    nameStartsWith = nameStartsWith,
                    studios = if (studioName != null) listOf(studioName) else emptyList(),
                    officialRatings = filters.officialRatings.toList(),
                    tags = filters.tags.toList(),
                    videoTypes = videoTypeNames,
                    seriesStatuses = filters.seriesStatuses.map { it.serialName },
                    hasSubtitles = if (LibraryFeature.SUBTITLES in filters.features) true else null,
                    hasTrailer = if (LibraryFeature.TRAILER in filters.features) true else null,
                    hasSpecialFeature =
                        if (LibraryFeature.SPECIAL_FEATURE in filters.features) true else null,
                    hasThemeSong = if (LibraryFeature.THEME_SONG in filters.features) true else null,
                    hasThemeVideo =
                        if (LibraryFeature.THEME_VIDEO in filters.features) true else null,
                    isHd = isHd,
                    is4k = if (VideoTypeFilter.UHD_4K in filters.videoTypes) true else null,
                    is3d = if (VideoTypeFilter.THREE_D in filters.videoTypes) true else null,
                    )
                    .getOrThrow()

            val items = response.items.mapNotNull { it.toAfinityItem(baseUrl) }

            Timber.d(
                "PagingSource: Loaded ${items.size} items for nameStartsWith='$nameStartsWith'"
            )

            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty() || items.size < PAGE_SIZE) null else page + 1,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}