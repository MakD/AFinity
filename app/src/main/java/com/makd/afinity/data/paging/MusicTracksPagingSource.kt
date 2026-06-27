package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.repository.music.MusicRepository
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

class MusicTracksPagingSource(
    private val musicRepository: MusicRepository,
    private val libraryId: UUID,
    private val sortBy: ItemSortBy,
    private val sortOrder: SortOrder,
    private val filters: MusicFilters,
) : PagingSource<Int, AfinityTrack>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityTrack> {
        return try {
            val page = params.key ?: 0
            val effectiveSortBy = if (sortBy == ItemSortBy.SORT_NAME) ItemSortBy.NAME else sortBy
            val items =
                musicRepository.getTracks(
                    libraryId = libraryId,
                    sortBy = effectiveSortBy,
                    sortOrder = sortOrder,
                    filters = filters,
                    startIndex = page * PAGE_SIZE,
                    limit = PAGE_SIZE,
                    nameStartsWith = null,
                )
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.size < PAGE_SIZE) null else page + 1,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load tracks page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityTrack>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
}
