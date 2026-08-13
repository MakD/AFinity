package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.toItemFilterCriteria
import com.makd.afinity.data.repository.FieldSets
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
    private val studioNames: List<String> = emptyList(),
    private val includeItemTypes: List<String>? = null,
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
                includeItemTypes?.takeIf { it.isNotEmpty() }
                    ?: when (libraryType) {
                        CollectionType.TvShows -> listOf("SERIES")
                        CollectionType.Movies -> listOf("MOVIE")
                        CollectionType.BoxSets -> listOf("BOX_SET")
                        CollectionType.Playlists -> listOf("PLAYLIST")
                        else -> listOf("MOVIE", "SERIES", "BOX_SET", "FOLDER")
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
                        nameStartsWith = nameStartsWith,
                        recursive = true,
                        criteria = filters.toItemFilterCriteria(studioNames),
                        fields =
                            if (libraryType == CollectionType.Playlists) FieldSets.PLAYLIST_GRID
                            else null,
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
