package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import java.util.UUID
import timber.log.Timber

class EpisodesPagingSource(
    private val mediaRepository: MediaRepository,
    private val seasonId: UUID,
    private val seriesId: UUID,
) : PagingSource<Int, AfinityEpisode>() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityEpisode> {
        return try {
            val page = params.key ?: 0
            val startIndex = page * PAGE_SIZE

            Timber.d("EpisodesPagingSource load: page=$page, startIndex=$startIndex")

            val episodes =
                mediaRepository.getEpisodes(
                    seasonId = seasonId,
                    seriesId = seriesId,
                    fields = FieldSets.EPISODE_LIST,
                    startIndex = startIndex,
                    limit = PAGE_SIZE,
                )

            Timber.d("EpisodesPagingSource: Loaded ${episodes.size} episodes for page $page")

            LoadResult.Page(
                data = episodes,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (episodes.size < PAGE_SIZE) null else page + 1,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load episodes page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityEpisode>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}