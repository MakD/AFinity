package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.ItemFilterCriteria
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.toItemFilterCriteria
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

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
    private val recursive: Boolean = true,
    private val randomOrder: RandomOrder? = null,
) : PagingSource<Int, AfinityItem>() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    private val shuffledIdsMutex = Mutex()
    private var shuffledIds: List<UUID>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityItem> {
        return try {
            val page = params.key ?: 0

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
                        CollectionType.Books -> listOf("BOOK")
                        CollectionType.MusicVideos -> listOf("MUSIC_VIDEO")
                        CollectionType.HomeVideos,
                        CollectionType.Photos -> listOf("PHOTO", "VIDEO")
                        else -> listOf("MOVIE", "SERIES", "BOX_SET", "FOLDER")
                    }
            val criteria = filters.toItemFilterCriteria(studioNames)
            val fields =
                when {
                    includeTypes.any { it == "PHOTO" || it == "PHOTO_ALBUM" } ->
                        FieldSets.PHOTO_GRID
                    libraryType == CollectionType.Playlists -> FieldSets.PLAYLIST_GRID
                    libraryType == CollectionType.Books -> FieldSets.BOOK_GRID
                    libraryType == CollectionType.MusicVideos -> FieldSets.MUSIC_VIDEO_GRID
                    else -> null
                }

            if (randomOrder != null) {
                return loadShuffledPage(page, randomOrder, includeTypes, criteria, fields)
            }

            val response =
                mediaRepository
                    .getItemsResult(
                        parentId = parentId,
                        sortBy = sortBy,
                        sortDescending = sortDescending,
                        limit = PAGE_SIZE,
                        startIndex = page * PAGE_SIZE,
                        includeItemTypes = includeTypes,
                        nameStartsWith = nameStartsWith,
                        recursive = recursive,
                        criteria = criteria,
                        fields = fields,
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load page")
            LoadResult.Error(e)
        }
    }

    private suspend fun loadShuffledPage(
        page: Int,
        order: RandomOrder,
        includeTypes: List<String>,
        criteria: ItemFilterCriteria,
        fields: List<ItemFields>?,
    ): LoadResult<Int, AfinityItem> {
        val ids = shuffledIds(order, includeTypes, criteria)
        val pageIds = ids.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        val items =
            if (pageIds.isEmpty()) {
                emptyList()
            } else {
                val loaded =
                    mediaRepository
                        .getItemsResult(
                            parentId = parentId,
                            includeItemTypes = includeTypes,
                            recursive = recursive,
                            criteria = criteria.copy(ids = pageIds),
                            fields = fields,
                        )
                        .getOrThrow()
                        .items
                        .associateBy { it.id }
                pageIds.mapNotNull { id -> loaded[id]?.toAfinityItem(baseUrl) }
            }

        return LoadResult.Page(
            data = items,
            prevKey = if (page == 0) null else page - 1,
            nextKey = if ((page + 1) * PAGE_SIZE < ids.size) page + 1 else null,
        )
    }

    private suspend fun shuffledIds(
        order: RandomOrder,
        includeTypes: List<String>,
        criteria: ItemFilterCriteria,
    ): List<UUID> = shuffledIdsMutex.withLock {
        shuffledIds
            ?: order
                .reconcile(
                    mediaRepository
                        .getItemsResult(
                            parentId = parentId,
                            sortBy = SortBy.RANDOM,
                            includeItemTypes = includeTypes,
                            nameStartsWith = nameStartsWith,
                            recursive = recursive,
                            criteria = criteria,
                            fields = emptyList(),
                            enableImages = false,
                            enableUserData = false,
                        )
                        .getOrThrow()
                        .items
                        .map { it.id }
                )
                .also { shuffledIds = it }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
