package com.makd.afinity.data.repository.music

import androidx.core.net.toUri
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityAlbum
import com.makd.afinity.data.models.extensions.toAfinityArtist
import com.makd.afinity.data.models.extensions.toAfinityPlaylist
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.models.music.MusicSearchResults
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ArtistsApi
import org.jellyfin.sdk.api.operations.FilterApi
import org.jellyfin.sdk.api.operations.GenresApi
import org.jellyfin.sdk.api.operations.InstantMixApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.LyricsApi
import org.jellyfin.sdk.api.operations.PlaylistsApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
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

    private suspend fun getCurrentUserId(): UUID? =
        withContext(Dispatchers.IO) {
            sessionManager.currentSession.value?.userId
        }

    override fun getBaseUrl(): String = getBaseUrlInternal()

    override fun getStreamUrl(trackId: UUID): String {
        val baseUrl = getBaseUrlInternal()
        return "$baseUrl/Audio/$trackId/universal?audioCodec=flac,mp3,aac,opus,ogg&container=flac,mp3,aac,ogg,opus"
    }

    override suspend fun getTracks(
        libraryId: UUID,
        sortBy: ItemSortBy,
        sortOrder: SortOrder,
        filters: MusicFilters,
        startIndex: Int,
        limit: Int,
        nameStartsWith: String?,
    ): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val itemFilters = buildList {
                    if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                    if (filters.unplayedOnly) add(ItemFilter.IS_UNPLAYED)
                    if (filters.playedOnly) add(ItemFilter.IS_PLAYED)
                }

                val response =
                    ItemsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch tracks for library: $libraryId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching tracks for library: $libraryId")
                emptyList()
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
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val itemFilters = buildList {
                    if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                    if (filters.unplayedOnly) add(ItemFilter.IS_UNPLAYED)
                    if (filters.playedOnly) add(ItemFilter.IS_PLAYED)
                }

                val response =
                    ItemsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch albums for library: $libraryId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching albums for library: $libraryId")
                emptyList()
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
    ): List<AfinityArtist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val itemFilters = buildList {
                    if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                }

                val response =
                    ArtistsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch artists for library: $libraryId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching artists for library: $libraryId")
                emptyList()
            }
        }

    override suspend fun getMusicFilterOptions(
        libraryId: UUID,
        itemType: BaseItemKind,
    ): MusicFilterOptions =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient()
                        ?: return@withContext MusicFilterOptions()
                val userId = getCurrentUserId() ?: return@withContext MusicFilterOptions()

                val response =
                    FilterApi(apiClient)
                        .getQueryFiltersLegacy(
                            userId = userId,
                            parentId = libraryId,
                            includeItemTypes = listOf(itemType),
                        )
                val content = response.content
                MusicFilterOptions(
                    genres = content.genres.orEmpty(),
                    years = content.years.orEmpty().sortedDescending(),
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch music filter options for library: $libraryId")
                MusicFilterOptions()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching music filter options: $libraryId")
                MusicFilterOptions()
            }
        }

    override suspend fun getAlbumById(albumId: UUID): AfinityAlbum? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            ids = listOf(albumId),
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                        )
                response.content.items.firstOrNull()?.let {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch album: $albumId")
                null
            }
        }

    override suspend fun getAlbumTracks(albumId: UUID): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            val session = sessionManager.currentSession.value
            val serverId = session?.serverId
            val userId = session?.userId

            fun toFileUri(rawPath: String): String =
                if (rawPath.startsWith("/"))
                    android.net.Uri.fromFile(java.io.File(rawPath)).toString()
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
                        AfinityTrack(
                            id = dl.itemId,
                            name = dl.itemName,
                            albumId =
                                dl.seriesId?.let {
                                    runCatching { UUID.fromString(it) }.getOrNull()
                                },
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
                            normalizationGain = null,
                            images = AfinityImages(primary = dl.imageUrl?.toUri()),
                            localFilePath = dl.filePath?.let { toFileUri(it) },
                        )
                    }
            }

            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val apiUserId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
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
                        )
                val tracks =
                    response.content.items.mapNotNull { dto ->
                        runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                    }
                patchLocalPaths(tracks)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch tracks for album: $albumId — trying DB cache")
                if (serverId != null && userId != null) {
                    val dbTracks =
                        databaseRepository.getMusicAlbumTracks(albumId, serverId, userId.toString())
                    val patched = patchLocalPaths(dbTracks)
                    if (patched.isNotEmpty()) patched else tracksFromDownloads()
                } else {
                    emptyList()
                }
            }
        }

    override suspend fun getArtistById(artistId: UUID): AfinityArtist? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            ids = listOf(artistId),
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                        )
                response.content.items.firstOrNull()?.let {
                    runCatching { it.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch artist: $artistId")
                null
            }
        }

    override suspend fun getArtistAlbums(artistId: UUID, libraryId: UUID?): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            parentId = libraryId,
                            albumArtistIds = listOf(artistId),
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch albums for artist: $artistId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching albums for artist: $artistId")
                emptyList()
            }
        }

    override suspend fun getArtistTopTracks(
        artistId: UUID,
        libraryId: UUID?,
        limit: Int,
    ): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch top tracks for artist: $artistId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching top tracks for artist: $artistId")
                emptyList()
            }
        }

    override suspend fun getArtistAppearsOn(artistId: UUID, libraryId: UUID?): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch 'appears on' albums for artist: $artistId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching 'appears on' albums for artist: $artistId")
                emptyList()
            }
        }

    override suspend fun getPlaylists(libraryId: UUID?): List<AfinityPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            parentId = libraryId,
                            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = FieldSets.MUSIC_PLAYLIST,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityPlaylist(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch playlists")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching playlists")
                emptyList()
            }
        }

    override suspend fun getFavoritePlaylists(): List<AfinityPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                            filters = listOf(ItemFilter.IS_FAVORITE),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = FieldSets.MUSIC_PLAYLIST,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityPlaylist(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch favorite playlists")
                emptyList()
            }
        }

    override suspend fun getPlaylistById(playlistId: UUID): AfinityPlaylist? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            ids = listOf(playlistId),
                            fields = FieldSets.MUSIC_PLAYLIST,
                            enableUserData = true,
                        )
                response.content.items.firstOrNull()?.let {
                    runCatching { it.toAfinityPlaylist(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch playlist: $playlistId")
                null
            }
        }

    override suspend fun getPlaylistTracks(playlistId: UUID): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    PlaylistsApi(apiClient)
                        .getPlaylistItems(
                            playlistId = playlistId,
                            userId = userId,
                            fields = FieldSets.MUSIC_TRACK,
                            enableUserData = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch tracks for playlist: $playlistId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching tracks for playlist: $playlistId")
                emptyList()
            }
        }

    override suspend fun createPlaylist(
        name: String,
        trackIds: List<UUID>,
        isPublic: Boolean,
    ): AfinityPlaylist? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val result =
                    PlaylistsApi(apiClient)
                        .createPlaylist(
                            org.jellyfin.sdk.model.api.CreatePlaylistDto(
                                name = name,
                                ids = emptyList(),
                                userId = userId,
                                mediaType = org.jellyfin.sdk.model.api.MediaType.AUDIO,
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
                            return@withContext null
                        }

                if (trackIds.isNotEmpty()) {
                    runCatching {
                        PlaylistsApi(apiClient)
                            .addItemToPlaylist(
                                playlistId = playlistId,
                                ids = trackIds,
                                userId = userId,
                            )
                    }
                        .onFailure { Timber.e(it, "createPlaylist: addItemToPlaylist failed") }
                }

                invalidatePlaylistsCache()

                AfinityPlaylist(
                    id = playlistId,
                    name = name,
                    overview = null,
                    songCount = trackIds.size.takeIf { it > 0 },
                    runtimeTicks = 0L,
                    favorite = false,
                    images = AfinityImages(primary = null, primaryImageBlurHash = null),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to create playlist: $name")
                null
            }
        }

    override suspend fun addTracksToPlaylist(playlistId: UUID, trackIds: List<UUID>) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId()
                PlaylistsApi(apiClient)
                    .addItemToPlaylist(
                        playlistId = playlistId,
                        ids = trackIds,
                        userId = userId,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to add tracks to playlist $playlistId")
                throw e
            }
        }

    override suspend fun removeTracksFromPlaylist(playlistId: UUID, entryIds: List<String>) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                PlaylistsApi(apiClient)
                    .removeItemFromPlaylist(
                        playlistId = playlistId.toString(),
                        entryIds = entryIds,
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove tracks from playlist $playlistId")
                throw e
            }
        }

    override suspend fun deletePlaylist(playlistId: UUID) =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                org.jellyfin.sdk.api.operations
                    .LibraryApi(apiClient)
                    .deleteItem(itemId = playlistId)
                invalidatePlaylistsCache()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete playlist $playlistId")
                throw e
            }
        }

    override suspend fun getInstantMix(itemId: UUID, limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
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
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch instant mix for item: $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching instant mix for item: $itemId")
                emptyList()
            }
        }

    override suspend fun getArtistRadio(artistId: UUID, limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
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
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch artist radio for artist: $artistId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching artist radio for artist: $artistId")
                emptyList()
            }
        }

    override suspend fun getSimilarAlbums(itemId: UUID, limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    org.jellyfin.sdk.api.operations
                        .LibraryApi(apiClient)
                        .getSimilarItems(
                            itemId = itemId,
                            userId = userId,
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                        )
                response.content.items
                    .filter { it.type == BaseItemKind.MUSIC_ALBUM }
                    .mapNotNull { runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull() }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get similar albums for item: $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting similar albums for item: $itemId")
                emptyList()
            }
        }

    override suspend fun getLyrics(trackId: UUID): List<AfinityLyricLine> =
        withContext(Dispatchers.IO) {
            val cached = getCachedLyrics(trackId)
            if (cached != null) return@withContext cached

            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val response = LyricsApi(apiClient).getLyrics(itemId = trackId)
                response.content.lyrics.mapNotNull { line ->
                    val start = line.start ?: return@mapNotNull null
                    AfinityLyricLine(
                        text = line.text,
                        startSeconds = start / 10_000_000.0,
                    )
                }
            } catch (e: ApiClientException) {
                Timber.d("No lyrics found for track: $trackId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching lyrics for track: $trackId")
                emptyList()
            }
        }

    override suspend fun searchMusic(query: String, libraryId: UUID?): MusicSearchResults =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient()
                        ?: return@withContext MusicSearchResults(
                            emptyList(),
                            emptyList(),
                            emptyList(),
                            emptyList(),
                        )
                val userId =
                    getCurrentUserId()
                        ?: return@withContext MusicSearchResults(
                            emptyList(),
                            emptyList(),
                            emptyList(),
                            emptyList(),
                        )
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
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
                            .mapNotNull {
                                runCatching { it.toAfinityPlaylist(baseUrl) }.getOrNull()
                            },
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to search music: $query")
                MusicSearchResults(emptyList(), emptyList(), emptyList(), emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error searching music: $query")
                MusicSearchResults(emptyList(), emptyList(), emptyList(), emptyList())
            }
        }

    override suspend fun setFavorite(itemId: UUID, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val userLibraryApi = UserLibraryApi(apiClient)
                if (favorite) {
                    userLibraryApi.markFavoriteItem(itemId = itemId, userId = userId)
                } else {
                    userLibraryApi.unmarkFavoriteItem(itemId = itemId, userId = userId)
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to set favorite ($favorite) for item: $itemId")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error setting favorite for item: $itemId")
            }
        }
    }

    override suspend fun getRecentlyPlayedTracks(limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
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
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch recently played tracks")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching continue listening tracks")
                emptyList()
            }
        }

    override suspend fun getRecentlyAddedAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch recently added albums")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching recently added albums")
                emptyList()
            }
        }

    override suspend fun getMusicGenres(limit: Int): List<AfinityMusicGenre> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    GenresApi(apiClient)
                        .getGenres(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            limit = limit,
                            enableImages = true,
                            enableTotalRecordCount = false,
                        )
                response.content.items.mapNotNull { dto ->
                    val name = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val id = dto.id ?: return@mapNotNull null
                    val imageUrl =
                        dto.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.let { tag
                            ->
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
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch music genres")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching music genres")
                emptyList()
            }
        }

    override suspend fun getAllMusicGenres(
        libraryId: UUID?,
        startIndex: Int,
        limit: Int,
    ): List<AfinityMusicGenre> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    GenresApi(apiClient)
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
                        dto.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.let { tag
                            ->
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
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch all music genres")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching all music genres")
                emptyList()
            }
        }

    override suspend fun getAlbumsByGenre(genreName: String, limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            genres = listOf(genreName),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch albums for genre: $genreName")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching albums for genre: $genreName")
                emptyList()
            }
        }

    override suspend fun getArtistsByGenre(genreName: String, limit: Int): List<AfinityArtist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ArtistsApi(apiClient)
                        .getAlbumArtists(
                            userId = userId,
                            genres = listOf(genreName),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch artists for genre: $genreName")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching artists for genre: $genreName")
                emptyList()
            }
        }

    override suspend fun getFavoriteArtists(limit: Int): List<AfinityArtist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ArtistsApi(apiClient)
                        .getAlbumArtists(
                            userId = userId,
                            filters = listOf(ItemFilter.IS_FAVORITE),
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch favorite artists")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching favorite artists")
                emptyList()
            }
        }

    override suspend fun getTopArtists(limit: Int): List<AfinityArtist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()

                val response =
                    ArtistsApi(apiClient)
                        .getAlbumArtists(
                            userId = userId,
                            sortBy = listOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                        )
                response.content.items.mapNotNull { dto ->
                    runCatching { dto.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to fetch top artists")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching top artists")
                emptyList()
            }
        }

    override suspend fun getRecentlyPlayedAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            filters = listOf(ItemFilter.IS_PLAYED),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch recently played albums")
                emptyList()
            }
        }

    override suspend fun getMostPlayedAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch most played albums")
                emptyList()
            }
        }

    override suspend fun getFavoriteTracks(limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            filters = listOf(ItemFilter.IS_FAVORITE),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            limit = limit,
                            fields = FieldSets.MUSIC_TRACK,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch favorite tracks")
                emptyList()
            }
        }

    override suspend fun getFavoriteAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            filters = listOf(ItemFilter.IS_FAVORITE),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch favorite albums")
                emptyList()
            }
        }

    override suspend fun getRandomAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch random albums")
                emptyList()
            }
        }

    override suspend fun getRandomArtists(limit: Int): List<AfinityArtist> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ArtistsApi(apiClient)
                        .getAlbumArtists(
                            userId = userId,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_ARTIST,
                            enableUserData = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityArtist(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch random artists")
                emptyList()
            }
        }

    override suspend fun getTracksByGenre(genreName: String, limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            genres = listOf(genreName),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_TRACK,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch tracks by genre: $genreName")
                emptyList()
            }
        }

    override suspend fun getRandomTracks(limit: Int): List<AfinityTrack> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_TRACK,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityTrack(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch random tracks")
                emptyList()
            }
        }

    override suspend fun getTopRatedAlbums(limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch top rated albums")
                emptyList()
            }
        }

    override suspend fun getRecentlyAddedAlbumsByGenre(
        genreName: String,
        limit: Int,
    ): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            genres = listOf(genreName),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch recently added albums for genre: $genreName")
                emptyList()
            }
        }

    override suspend fun getAlbumsByDecade(decade: Int, limit: Int): List<AfinityAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val baseUrl = getBaseUrlInternal()
                val years = (decade until decade + 10).toList()
                val response =
                    ItemsApi(apiClient)
                        .getItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            years = years,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = limit,
                            fields = FieldSets.MUSIC_ALBUM,
                            enableUserData = true,
                            recursive = true,
                        )
                response.content.items.mapNotNull {
                    runCatching { it.toAfinityAlbum(baseUrl) }.getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch albums by decade: $decade")
                emptyList()
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

    override suspend fun getCachedLyrics(trackId: UUID): List<AfinityLyricLine>? {
        val session = sessionManager.currentSession.value ?: return null
        val json =
            databaseRepository.getMusicLyricsJson(
                trackId,
                session.serverId,
                session.userId.toString(),
            ) ?: return null
        return try {
            val parsed = Json.parseToJsonElement(json).jsonArray
            parsed.mapNotNull { element ->
                val pair = element.jsonArray
                val text = pair[0].jsonPrimitive.content
                val startSeconds =
                    pair[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                AfinityLyricLine(text = text, startSeconds = startSeconds)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse cached lyrics for track $trackId")
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
