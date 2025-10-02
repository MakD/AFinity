package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.FieldSets
import timber.log.Timber
import java.util.UUID

class EpisodesPagingSource(
    private val mediaRepository: MediaRepository,
    private val seasonId: UUID,
    private val seriesId: UUID
) : PagingSource<Int, AfinityEpisode>() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    private var allEpisodes: List<AfinityEpisode>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityEpisode> {
        return try {
            val page = params.key ?: 0
            val startIndex = page * PAGE_SIZE

            Timber.d("EpisodesPagingSource load: page=$page, startIndex=$startIndex")

            if (allEpisodes == null) {
                Timber.d("EpisodesPagingSource: Loading all episodes from API")
                allEpisodes = mediaRepository.getEpisodes(
                    seasonId = seasonId,
                    seriesId = seriesId,
                    fields = FieldSets.EPISODE_LIST
                ).sortedBy { it.indexNumber ?: 0 }
                Timber.d("EpisodesPagingSource: Loaded ${allEpisodes?.size} total episodes")
            }

            val paginatedEpisodes = allEpisodes!!
                .drop(startIndex)
                .take(PAGE_SIZE)

            Timber.d("EpisodesPagingSource: Returning page $page with ${paginatedEpisodes.size} episodes")

            LoadResult.Page(
                data = paginatedEpisodes,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (startIndex + paginatedEpisodes.size >= allEpisodes!!.size) null else page + 1
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load episodes page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityEpisode>): Int? {
        allEpisodes = null
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}