package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.ui.library.FilterType
import timber.log.Timber
import java.util.UUID

class JellyfinItemsPagingSource(
    private val mediaRepository: MediaRepository,
    private val parentId: UUID?,
    private val libraryType: CollectionType,
    private val sortBy: SortBy,
    private val sortDescending: Boolean,
    private val filter: FilterType,
    private val baseUrl: String,
    private val nameStartsWith: String? = null,
    private val studioName: String? = null
) : PagingSource<Int, AfinityItem>() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityItem> {
        return try {
            val page = params.key ?: 0
            val startIndex = page * PAGE_SIZE

            Timber.d("PagingSource load: page=$page, nameStartsWith='$nameStartsWith', libraryType=$libraryType")

            val filterIsPlayed = when (filter) {
                FilterType.WATCHED -> true
                FilterType.UNWATCHED -> false
                else -> null
            }

            val filterIsLiked = when (filter) {
                FilterType.WATCHLIST -> true
                else -> null
            }

            val items = if (nameStartsWith != null || studioName != null) {
                val includeTypes = when (libraryType) {
                    CollectionType.TvShows -> listOf("SERIES")
                    CollectionType.Movies -> listOf("MOVIE")
                    CollectionType.BoxSets -> listOf("BOXSET")
                    CollectionType.Mixed -> listOf("MOVIE", "SERIES", "BOXSET", "FOLDER")
                    else -> listOf("MOVIE", "SERIES", "BOXSET", "FOLDER")
                }

                val response = mediaRepository.getItems(
                    parentId = parentId,
                    sortBy = sortBy,
                    sortDescending = sortDescending,
                    limit = PAGE_SIZE,
                    startIndex = startIndex,
                    includeItemTypes = includeTypes,
                    isFavorite = when (filter) {
                        FilterType.FAVORITES -> true
                        else -> null
                    },
                    isPlayed = filterIsPlayed,
                    isLiked = filterIsLiked,
                    nameStartsWith = nameStartsWith,
                    studios = if (studioName != null) listOf(studioName) else emptyList()
                )

                response.items?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()

            } else {
                when (libraryType) {
                    CollectionType.TvShows -> {
                        if (filter == FilterType.FAVORITES || filter == FilterType.WATCHLIST) {
                            val response = mediaRepository.getItems(
                                parentId = parentId,
                                sortBy = sortBy,
                                sortDescending = sortDescending,
                                limit = PAGE_SIZE,
                                startIndex = startIndex,
                                includeItemTypes = listOf("SERIES"),
                                isFavorite = when (filter) {
                                    FilterType.FAVORITES -> true
                                    else -> null
                                },
                                isPlayed = filterIsPlayed,
                                isLiked = filterIsLiked
                            )
                            response.items?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
                        } else {
                            mediaRepository.getShows(
                                parentId = parentId,
                                sortBy = sortBy,
                                sortDescending = sortDescending,
                                limit = PAGE_SIZE,
                                startIndex = startIndex,
                                isPlayed = filterIsPlayed
                            )
                        }
                    }

                    CollectionType.Movies -> {
                        if (filter == FilterType.FAVORITES || filter == FilterType.WATCHLIST) {
                            val response = mediaRepository.getItems(
                                parentId = parentId,
                                sortBy = sortBy,
                                sortDescending = sortDescending,
                                limit = PAGE_SIZE,
                                startIndex = startIndex,
                                includeItemTypes = listOf("MOVIE"),
                                isFavorite = when (filter) {
                                    FilterType.FAVORITES -> true
                                    else -> null
                                },
                                isPlayed = filterIsPlayed,
                                isLiked = filterIsLiked
                            )
                            response.items?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
                        } else {
                            mediaRepository.getMovies(
                                parentId = parentId,
                                sortBy = sortBy,
                                sortDescending = sortDescending,
                                limit = PAGE_SIZE,
                                startIndex = startIndex,
                                isPlayed = filterIsPlayed
                            )
                        }
                    }

                    else -> {
                        val includeTypes = when (libraryType) {
                            CollectionType.BoxSets -> listOf("BOXSET")
                            CollectionType.Mixed -> listOf("MOVIE", "SERIES", "BOXSET", "FOLDER")
                            else -> listOf("MOVIE", "SERIES", "BOXSET", "FOLDER")
                        }

                        val response = mediaRepository.getItems(
                            parentId = parentId,
                            sortBy = sortBy,
                            sortDescending = sortDescending,
                            limit = PAGE_SIZE,
                            startIndex = startIndex,
                            includeItemTypes = includeTypes,
                            isFavorite = when (filter) {
                                FilterType.FAVORITES -> true
                                else -> null
                            },
                            isPlayed = filterIsPlayed,
                            isLiked = filterIsLiked
                        )
                        response.items?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
                    }
                }
            }

            Timber.d("PagingSource: Loaded ${items.size} items for nameStartsWith='$nameStartsWith'")

            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty() || items.size < PAGE_SIZE) null else page + 1
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