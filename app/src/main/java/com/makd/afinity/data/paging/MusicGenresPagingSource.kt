package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.repository.music.MusicRepository
import timber.log.Timber
import java.util.UUID

class MusicGenresPagingSource(
    private val musicRepository: MusicRepository,
    private val libraryId: UUID,
) : PagingSource<Int, AfinityMusicGenre>() {

    companion object {
        const val PAGE_SIZE = 100
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityMusicGenre> {
        return try {
            val page = params.key ?: 0
            val items = musicRepository.getAllMusicGenres(
                libraryId = libraryId,
                startIndex = page * PAGE_SIZE,
                limit = PAGE_SIZE,
            )
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.size < PAGE_SIZE) null else page + 1,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genres page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityMusicGenre>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
}