package com.makd.afinity.data.repository.music

import androidx.core.net.toUri
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityAlbum
import com.makd.afinity.data.models.extensions.toAfinityArtist
import com.makd.afinity.data.models.extensions.toAfinityPlaylist
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.extensions.toRecentlyPlayedAlbums
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.PlaylistEntry
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityPlaylistContents
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.CachedLyrics
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.models.music.MusicSearchResults
import com.makd.afinity.data.models.music.decodeLyricsJson
import com.makd.afinity.data.models.music.toAfinityLyricLine
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.NoActiveSessionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ArtistApi
import org.jellyfin.sdk.api.operations.FilterApi
import org.jellyfin.sdk.api.operations.GenreApi
import org.jellyfin.sdk.api.operations.InstantMixApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.LyricApi
import org.jellyfin.sdk.api.operations.PlaylistApi
import org.jellyfin.sdk.api.operations.UserDataApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinMusicRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val apiInvoker: JellyfinApiInvoker,
    private val mediaChangeManager: MediaChangeManager,
) : MusicRepository {

    private val playlistsRefreshTrigger = MutableStateFlow(0)

    override fun invalidatePlaylistsCache() {
        playlistsRefreshTrigger.update { it + 1 }
    }

    override fun getPlaylistsFlow(libraryId: UUID?): Flow<List<AfinityPlaylist>> =
        playlistsRefreshTrigger.map {
            getPlaylists(libraryId)
        }

    private fun getBaseUrlInternal(): String =
        sessionManager.currentSession.value?.serverUrl?.trimEnd('/') ?: ""

    private fun getCurrentUserId(): UUID? = sessionManager.currentSession.value?.userId

    private suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T = apiInvoker.apiCall(default, errorMessage, block)

    override fun getBaseUrl(): String = getBaseUrlInternal()

    override suspend fun getTracks(
        libraryId: UUID,
        sortBy: ItemSortBy,
        sortOrder: SortOrder,
        filters: MusicFilters,
        startIndex: Int,
        limit: Int,
        nameStartsWith: String?,
    ): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch tracks for library: $libraryId") { apiClient, userId
            ->
            val baseUrl = getBaseUrlInternal()

            val itemFilters = buildList {
                if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                if (filters.unplayedOnly) add(ItemFilter.IS_UNPLAYED)
                if (filters.playedOnly) add(ItemFilter.IS_PLAYED)
            }

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        sortBy = listOf(sortBy),
                        sortOrder = listOf(sortOrder),
                        filters = itemFilters.ifEmpty { null },
                        genres = filters.genres.toList().ifEmpty { null },
                        years = filters.years.toList().ifEmpty { null },
                        startIndex = startIndex,
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        recursive = true,
                        nameStartsWith = nameStartsWith,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getAlbums(
        libraryId: UUID,
        sortBy: ItemSortBy,
        sortOrder: SortOrder,
        filters: MusicFilters,
        startIndex: Int,
        limit: Int,
        nameStartsWith: String?,
    ): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch albums for library: $libraryId") { apiClient, userId
            ->
            val baseUrl = getBaseUrlInternal()

            val itemFilters = buildList {
                if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                if (filters.unplayedOnly) add(ItemFilter.IS_UNPLAYED)
                if (filters.playedOnly) add(ItemFilter.IS_PLAYED)
            }

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(sortBy),
                        sortOrder = listOf(sortOrder),
                        filters = itemFilters.ifEmpty { null },
                        genres = filters.genres.toList().ifEmpty { null },
                        years = filters.years.toList().ifEmpty { null },
                        startIndex = startIndex,
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        nameStartsWith = nameStartsWith,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtists(
        libraryId: UUID,
        sortBy: ItemSortBy,
        sortOrder: SortOrder,
        filters: MusicFilters,
        startIndex: Int,
        limit: Int,
        nameStartsWith: String?,
        albumArtistsOnly: Boolean,
    ): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch artists for library: $libraryId") { apiClient, userId
            ->
            val baseUrl = getBaseUrlInternal()

            val itemFilters = buildList { if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE) }

            val items =
                if (albumArtistsOnly) {
                    ArtistApi(apiClient)
                        .getAlbumArtists(
                            userId = userId,
                            parentId = libraryId,
                            sortBy = listOf(sortBy),
                            sortOrder = listOf(sortOrder),
                            filters = itemFilters.ifEmpty { null },
                            genres = filters.genres.toList().ifEmpty { null },
                            years = filters.years.toList().ifEmpty { null },
                            startIndex = startIndex,
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                            nameStartsWith = nameStartsWith,
                            enableTotalRecordCount = false,
                        )
                        .content
                        .items
                } else {
                    LibraryApi(apiClient)
                        .getItems(
                            userId = userId,
                            parentId = libraryId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                            sortBy = listOf(sortBy),
                            sortOrder = listOf(sortOrder),
                            filters = itemFilters.ifEmpty { null },
                            genres = filters.genres.toList().ifEmpty { null },
                            years = filters.years.toList().ifEmpty { null },
                            startIndex = startIndex,
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                            recursive = true,
                            nameStartsWith = nameStartsWith,
                            enableTotalRecordCount = false,
                        )
                        .content
                        .items
                }
            items.mapNotNull { dto -> runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull() }
        }

    override suspend fun getMusicFilterOptions(
        libraryId: UUID,
        itemType: BaseItemKind,
    ): MusicFilterOptions =
        apiCall(
            MusicFilterOptions(),
            "Failed to fetch music filter options for library: $libraryId",
        ) { apiClient, userId ->
            val content =
                FilterApi(apiClient)
                    .getQueryFiltersLegacy(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = listOf(itemType),
                    )
                    .content
            MusicFilterOptions(
                genres = content.genres.orEmpty(),
                years = content.years.orEmpty().sortedDescending(),
            )
        }

    override suspend fun getAlbumById(albumId: UUID): AfinityAlbum? {
        suspend fun fromDb(): AfinityAlbum? {
            val userId = sessionManager.currentSession.value?.userId ?: return null
            return databaseRepository.getAllMusicAlbumsByUser(userId).firstOrNull {
                it.id == albumId
            }
        }
        return apiInvoker
            .apiResult { apiClient, userId ->
                val baseUrl = getBaseUrlInternal()
                LibraryApi(apiClient).getItem(itemId = albumId, userId = userId).content.let {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            }
            .getOrElse { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to fetch album: $albumId — trying DB cache")
                }
                null
            } ?: fromDb()
    }

    override suspend fun getAlbumTracks(albumId: UUID): List<AfinityTrack> {
        val session = sessionManager.currentSession.value
        val serverId = session?.serverId
        val userId = session?.userId

        fun toFileUri(rawPath: String): String =
            if (rawPath.startsWith("/")) android.net.Uri.fromFile(java.io.File(rawPath)).toString()
            else rawPath

        suspend fun patchLocalPaths(tracks: List<AfinityTrack>): List<AfinityTrack> {
            if (serverId == null || userId == null) return tracks
            return tracks.map { track ->
                if (track.localFilePath != null) return@map track
                val download = databaseRepository.getDownloadByItemId(track.id)
                if (download?.status == DownloadStatus.COMPLETED && download.filePath != null) {
                    track.copy(localFilePath = toFileUri(download.filePath))
                } else {
                    track
                }
            }
        }

        suspend fun tracksFromDownloads(): List<AfinityTrack> {
            if (serverId == null || userId == null) return emptyList()
            return databaseRepository
                .getCompletedAudioDownloadsByAlbum(albumId.toString(), serverId, userId)
                .map { dl ->
                    val cached =
                        databaseRepository.getMusicTrack(dl.itemId, serverId, userId.toString())
                    AfinityTrack(
                        id = dl.itemId,
                        name = dl.itemName,
                        albumId =
                            dl.seriesId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        album = dl.seriesName,
                        artistId = null,
                        artist = null,
                        artists = emptyList(),
                        indexNumber = dl.episodeNumber,
                        discNumber = dl.seasonNumber,
                        productionYear = dl.releaseYear?.toIntOrNull(),
                        runtimeTicks = dl.runtimeTicks ?: 0L,
                        playbackPositionTicks = 0L,
                        played = false,
                        favorite = false,
                        playCount = null,
                        normalizationGain = cached?.normalizationGain,
                        albumNormalizationGain = cached?.albumNormalizationGain,
                        images = AfinityImages(primary = dl.imageUrl?.toUri()),
                        localFilePath = dl.filePath?.let { toFileUri(it) },
                    )
                }
        }

        suspend fun dbFallback(): List<AfinityTrack> {
            if (serverId == null || userId == null) return emptyList()
            val dbTracks =
                databaseRepository.getMusicAlbumTracks(albumId, serverId, userId.toString())
            val patched = patchLocalPaths(dbTracks)
            return patched.ifEmpty { tracksFromDownloads() }
        }

        return apiInvoker
            .apiResult { apiClient, apiUserId ->
                val baseUrl = getBaseUrlInternal()
                val tracks =
                    LibraryApi(apiClient)
                        .getItems(
                            userId = apiUserId,
                            parentId = albumId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            sortBy =
                                listOf(
                                    ItemSortBy.PARENT_INDEX_NUMBER,
                                    ItemSortBy.INDEX_NUMBER,
                                    ItemSortBy.SORT_NAME,
                                ),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = FieldSets.MUSIC_TRACK,
                            enableUserData = true,
                            recursive = false,
                            enableTotalRecordCount = false,
                        )
                        .content
                        .items
                        .mapNotNull { dto ->
                            runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                        }
                patchLocalPaths(tracks)
            }
            .getOrElse { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to fetch tracks for album: $albumId — trying DB cache")
                }
                dbFallback()
            }
    }

    override suspend fun getArtistById(artistId: UUID): AfinityArtist? =
        apiCall(null, "Failed to fetch artist: $artistId") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            LibraryApi(apiClient).getItem(itemId = artistId, userId = userId).content.let {
                runCatching { it.toAfinityArtist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtistsByIds(artistIds: List<UUID>): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch artists by ids") { apiClient, userId ->
            if (artistIds.isEmpty()) return@apiCall emptyList()
            val baseUrl = getBaseUrlInternal()
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    ids = artistIds,
                    fields = FieldSets.MUSIC_ARTIST,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { runCatching { it.toAfinityArtist(baseUrl) }.getOrNull() }
        }

    override suspend fun getArtistAlbums(
        artistId: UUID,
        libraryId: UUID?,
        excludeAlbumId: UUID?,
    ): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch albums for artist: $artistId") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        excludeItemIds = listOfNotNull(excludeAlbumId),
                        albumArtistIds = listOf(artistId),
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtistTopTracks(
        artistId: UUID,
        libraryId: UUID?,
        limit: Int,
    ): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch top tracks for artist: $artistId") { apiClient, userId
            ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        artistIds = listOf(artistId),
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        sortBy = listOf(ItemSortBy.PLAY_COUNT, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtistAppearsOn(artistId: UUID, libraryId: UUID?): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch 'appears on' albums for artist: $artistId") {
            apiClient,
            userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        contributingArtistIds = listOf(artistId),
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getPlaylists(libraryId: UUID?): List<AfinityPlaylist> =
        apiCall(emptyList(), "Failed to fetch playlists") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields = FieldSets.MUSIC_PLAYLIST,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityPlaylist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getFavoritePlaylists(): List<AfinityPlaylist> =
        apiCall(emptyList(), "Failed to fetch favorite playlists") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    fields = FieldSets.MUSIC_PLAYLIST,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { dto -> runCatching { dto.toAfinityPlaylist(baseUrl) }.getOrNull() }
        }

    override suspend fun getPlaylistById(playlistId: UUID): AfinityPlaylist? =
        apiCall(null, "Failed to fetch playlist: $playlistId") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    ids = listOf(playlistId),
                    fields = FieldSets.MUSIC_PLAYLIST,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .firstOrNull()
                ?.let { runCatching { it.toAfinityPlaylist(baseUrl) }.getOrNull() }
        }

    override suspend fun getPlaylistContents(playlistId: UUID): AfinityPlaylistContents =
        apiCall(AfinityPlaylistContents(), "Failed to fetch contents for playlist: $playlistId") {
            apiClient,
            userId ->
            val baseUrl = getBaseUrlInternal()
            val items =
                PlaylistApi(apiClient)
                    .getPlaylistItems(
                        playlistId = playlistId,
                        userId = userId,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                    )
                    .content
                    .items
            val entries =
                items
                    .filter { it.mediaType == MediaType.AUDIO }
                    .mapNotNull { dto ->
                        runCatching {
                            PlaylistEntry.Audio(
                                playlistItemId = dto.playlistItemId,
                                track = dto.toAfinityTrack(baseUrl),
                            )
                        }
                            .getOrNull()
                    }
            AfinityPlaylistContents(
                entries = entries,
                audioCount = entries.size,
                videoCount = items.count { it.mediaType == MediaType.VIDEO },
            )
        }

    override suspend fun createPlaylist(
        name: String,
        trackIds: List<UUID>,
        isPublic: Boolean,
        mediaType: MediaType,
    ): AfinityPlaylist? =
        apiCall(null, "Failed to create playlist: $name") { apiClient, userId ->
            val result =
                PlaylistApi(apiClient)
                    .createPlaylist(
                        org.jellyfin.sdk.model.api.CreatePlaylistDto(
                            name = name,
                            ids = emptyList(),
                            userId = userId,
                            mediaType = mediaType,
                            users = emptyList(),
                            isPublic = isPublic,
                        )
                    )

            val rawId = result.content.id
            Timber.d("createPlaylist: server returned id='$rawId' for '$name'")
            val playlistId =
                parseUuid(rawId)
                    ?: run {
                        Timber.e("createPlaylist: could not parse id='$rawId' as UUID")
                        return@apiCall null
                    }

            if (trackIds.isNotEmpty()) {
                runCatching {
                    PlaylistApi(apiClient)
                        .addItemToPlaylist(
                            playlistId = playlistId,
                            ids = trackIds,
                            userId = userId,
                        )
                }
                    .onFailure { Timber.e(it, "createPlaylist: addItemToPlaylist failed") }
            }

            invalidatePlaylistsCache()
            mediaChangeManager.notifyLibraryContentChanged("playlist_created")

            AfinityPlaylist(
                id = playlistId,
                name = name,
                overview = null,
                songCount = trackIds.size.takeIf { it > 0 },
                runtimeTicks = 0L,
                favorite = false,
                images = AfinityImages(primary = null, primaryImageBlurHash = null),
            )
        }

    override suspend fun addTracksToPlaylist(playlistId: UUID, trackIds: List<UUID>) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId()
                PlaylistApi(apiClient)
                    .addItemToPlaylist(
                        playlistId = playlistId,
                        ids = trackIds,
                        userId = userId,
                    )
                invalidatePlaylistsCache()
                mediaChangeManager.notifyLibraryContentChanged("playlist_items_added")
            } catch (e: Exception) {
                Timber.e(e, "Failed to add tracks to playlist $playlistId")
                throw e
            }
        }

    override suspend fun removeTracksFromPlaylist(playlistId: UUID, entryIds: List<String>) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                PlaylistApi(apiClient)
                    .removeItemFromPlaylist(
                        playlistId = playlistId.toString(),
                        entryIds = entryIds,
                    )
                invalidatePlaylistsCache()
                mediaChangeManager.notifyLibraryContentChanged("playlist_items_removed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove tracks from playlist $playlistId")
                throw e
            }
        }

    override suspend fun deletePlaylist(playlistId: UUID) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                LibraryApi(apiClient).deleteItem(itemId = playlistId)
                invalidatePlaylistsCache()
                mediaChangeManager.notifyLibraryContentChanged("playlist_deleted")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete playlist $playlistId")
                throw e
            }
        }

    override suspend fun getInstantMix(itemId: UUID, limit: Int): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch instant mix for item: $itemId") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                InstantMixApi(apiClient)
                    .getInstantMixFromItem(
                        itemId = itemId,
                        userId = userId,
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtistRadio(artistId: UUID, limit: Int): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch artist radio for artist: $artistId") {
            apiClient,
            userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                InstantMixApi(apiClient)
                    .getInstantMixFromArtists(
                        itemId = artistId,
                        userId = userId,
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getSimilarAlbums(
        itemId: UUID,
        limit: Int,
        excludeArtistId: UUID?,
    ): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to get similar albums for item: $itemId") { apiClient, userId
            ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getSimilarItems(
                        itemId = itemId,
                        userId = userId,
                        excludeArtistIds = listOfNotNull(excludeArtistId),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                    )
            response.content.items
                .filter { it.type == BaseItemKind.MUSIC_ALBUM }
                .mapNotNull { runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull() }
        }

    override suspend fun getLyrics(trackId: UUID): List<AfinityLyricLine> =
        withContext(Dispatchers.IO) {
            val cached = readCachedLyrics(trackId)
            if (cached != null && cached.cueAware) return@withContext cached.lines

            val fallback = cached?.lines.orEmpty()
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext fallback
                val response = LyricApi(apiClient).getLyrics(itemId = trackId)
                val fresh = response.content.lyrics.mapNotNull { it.toAfinityLyricLine() }
                fresh.ifEmpty { fallback }
            } catch (e: ApiClientException) {
                Timber.d("No lyrics found for track: $trackId")
                fallback
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching lyrics for track: $trackId")
                fallback
            }
        }

    override suspend fun searchMusic(query: String, libraryId: UUID?): MusicSearchResults =
        apiCall(
            MusicSearchResults(emptyList(), emptyList(), emptyList(), emptyList()),
            "Failed to search music: $query",
        ) { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        parentId = libraryId,
                        searchTerm = query,
                        includeItemTypes =
                            listOf(
                                BaseItemKind.AUDIO,
                                BaseItemKind.MUSIC_ALBUM,
                                BaseItemKind.MUSIC_ARTIST,
                                BaseItemKind.PLAYLIST,
                            ),
                        fields = FieldSets.MUSIC_SEARCH,
                        enableUserData = true,
                        recursive = true,
                        limit = 40,
                        enableTotalRecordCount = false,
                    )
            val items = response.content.items
            MusicSearchResults(
                tracks =
                    items
                        .filter { it.type == BaseItemKind.AUDIO }
                        .mapNotNull { runCatching { it.toAfinityTrack(baseUrl) }.getOrNull() },
                albums =
                    items
                        .filter { it.type == BaseItemKind.MUSIC_ALBUM }
                        .mapNotNull { runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull() },
                artists =
                    items
                        .filter { it.type == BaseItemKind.MUSIC_ARTIST }
                        .mapNotNull { runCatching { it.toAfinityArtist(baseUrl) }.getOrNull() },
                playlists =
                    items
                        .filter { it.type == BaseItemKind.PLAYLIST }
                        .mapNotNull { runCatching { it.toAfinityPlaylist(baseUrl) }.getOrNull() },
            )
        }

    override suspend fun setFavorite(itemId: UUID, favorite: Boolean) {
        apiCall(Unit, "Failed to set favorite ($favorite) for item: $itemId") { apiClient, userId ->
            val userDataApi = UserDataApi(apiClient)
            if (favorite) {
                userDataApi.markFavoriteItem(itemId = itemId, userId = userId)
            } else {
                userDataApi.unmarkFavoriteItem(itemId = itemId, userId = userId)
            }
        }
        mediaChangeManager.notifyItemChanged(itemId, patch = UserDataPatch(favorite = favorite))
    }

    override suspend fun getRecentlyPlayedTracks(limit: Int): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch recently played tracks") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        filters = listOf(ItemFilter.IS_PLAYED),
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        recursive = true,
                        limit = limit,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRecentlyAddedAlbums(limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch recently added albums") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getMusicGenres(limit: Int, parentId: UUID?): List<AfinityMusicGenre> =
        apiCall(emptyList(), "Failed to fetch music genres") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                GenreApi(apiClient)
                    .getGenres(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        enableImages = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                val name = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = dto.id ?: return@mapNotNull null
                val imageUrl =
                    dto.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.let { tag ->
                        baseUrl
                            .trimEnd('/')
                            .toUri()
                            .buildUpon()
                            .appendEncodedPath("Items/$id/Images/Primary")
                            .appendQueryParameter("tag", tag)
                            .build()
                            .toString()
                    }
                AfinityMusicGenre(id = id, name = name, imageUrl = imageUrl)
            }
        }

    override suspend fun getAllMusicGenres(
        libraryId: UUID?,
        startIndex: Int,
        limit: Int,
    ): List<AfinityMusicGenre> =
        apiCall(emptyList(), "Failed to fetch all music genres") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                GenreApi(apiClient)
                    .getGenres(
                        userId = userId,
                        parentId = libraryId,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableImages = true,
                        enableTotalRecordCount = false,
                        startIndex = startIndex,
                        limit = limit,
                    )
            response.content.items.mapNotNull { dto ->
                val name = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = dto.id ?: return@mapNotNull null
                val imageUrl =
                    dto.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.let { tag ->
                        baseUrl
                            .trimEnd('/')
                            .toUri()
                            .buildUpon()
                            .appendEncodedPath("Items/$id/Images/Primary")
                            .appendQueryParameter("tag", tag)
                            .build()
                            .toString()
                    }
                AfinityMusicGenre(id = id, name = name, imageUrl = imageUrl)
            }
        }

    override suspend fun getAlbumsByGenre(
        genreName: String,
        limit: Int,
        parentId: UUID?,
    ): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch albums for genre: $genreName") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        parentId = parentId,
                        genres = listOf(genreName),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getArtistsByGenre(
        genreName: String,
        limit: Int,
        parentId: UUID?,
    ): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch artists for genre: $genreName") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                ArtistApi(apiClient)
                    .getAlbumArtists(
                        userId = userId,
                        parentId = parentId,
                        genres = listOf(genreName),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ARTIST,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getFavoriteArtists(limit: Int, parentId: UUID?): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch favorite artists") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                ArtistApi(apiClient)
                    .getAlbumArtists(
                        userId = userId,
                        parentId = parentId,
                        filters = listOf(ItemFilter.IS_FAVORITE),
                        limit = limit,
                        fields = FieldSets.MUSIC_ARTIST,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                        imageTypeLimit = 1,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getTopArtists(limit: Int, parentId: UUID?): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch top artists") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()

            val response =
                ArtistApi(apiClient)
                    .getAlbumArtists(
                        userId = userId,
                        parentId = parentId,
                        sortBy = listOf(ItemSortBy.PLAY_COUNT),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ARTIST,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull { dto ->
                runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRecentlyPlayedAlbums(limit: Int): List<AfinityAlbum> =
        getRecentlyPlayedTracks(limit = limit * 3).toRecentlyPlayedAlbums(limit)

    override suspend fun getMostPlayedAlbums(limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch most played albums") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.PLAY_COUNT),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getFavoriteTracks(limit: Int): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch favorite tracks") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        filters = listOf(ItemFilter.IS_FAVORITE),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                        recursive = true,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getFavoriteAlbums(limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch favorite albums") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        filters = listOf(ItemFilter.IS_FAVORITE),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                        recursive = true,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRandomAlbums(limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch random albums") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRandomArtists(limit: Int, parentId: UUID?): List<AfinityArtist> =
        apiCall(emptyList(), "Failed to fetch random artists") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                ArtistApi(apiClient)
                    .getAlbumArtists(
                        userId = userId,
                        parentId = parentId,
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_ARTIST,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityArtist(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getTracksByGenre(
        genreName: String,
        limit: Int,
        parentId: UUID?,
    ): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch tracks by genre: $genreName") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        parentId = parentId,
                        genres = listOf(genreName),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRandomTracks(limit: Int): List<AfinityTrack> =
        apiCall(emptyList(), "Failed to fetch random tracks") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_TRACK,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getTopRatedAlbums(limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch top rated albums") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getRecentlyAddedAlbumsByGenre(
        genreName: String,
        limit: Int,
        parentId: UUID?,
    ): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch recently added albums for genre: $genreName") {
            apiClient,
            userId ->
            val baseUrl = getBaseUrlInternal()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        parentId = parentId,
                        genres = listOf(genreName),
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    override suspend fun getAlbumsByDecade(decade: Int, limit: Int): List<AfinityAlbum> =
        apiCall(emptyList(), "Failed to fetch albums by decade: $decade") { apiClient, userId ->
            val baseUrl = getBaseUrlInternal()
            val years = (decade until decade + 10).toList()
            val response =
                LibraryApi(apiClient)
                    .getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        years = years,
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                        fields = FieldSets.MUSIC_ALBUM,
                        enableUserData = true,
                        recursive = true,
                        enableTotalRecordCount = false,
                    )
            response.content.items.mapNotNull {
                runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDownloadedTracksFlow(): Flow<List<AfinityTrack>> =
        sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            databaseRepository.getAllMusicTracksFlow(session.serverId, session.userId.toString())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDownloadedAlbumsFlow(): Flow<List<AfinityAlbum>> =
        sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            databaseRepository.getAllMusicAlbumsFlow(session.serverId, session.userId.toString())
        }

    override suspend fun getCachedLyrics(trackId: UUID): List<AfinityLyricLine>? =
        readCachedLyrics(trackId)?.lines

    private suspend fun readCachedLyrics(trackId: UUID): CachedLyrics? {
        val session = sessionManager.currentSession.value ?: return null
        val json =
            databaseRepository.getMusicLyricsJson(
                trackId,
                session.serverId,
                session.userId.toString(),
            ) ?: return null
        return decodeLyricsJson(json)
            ?: run {
                Timber.w("Failed to parse cached lyrics for track $trackId")
                null
            }
    }

    private fun parseUuid(raw: String): UUID? {
        val s = raw.trim()
        runCatching {
            return UUID.fromString(s)
        }
        if (s.length == 32 && s.all { it.isLetterOrDigit() }) {
            runCatching {
                return UUID.fromString(
                    "${s.substring(0,8)}-${s.substring(8,12)}-${s.substring(12,16)}-${s.substring(16,20)}-${s.substring(20)}"
                )
            }
        }
        return null
    }
}
