package com.makd.afinity.data.repository.admin

import android.util.Base64
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.admin.AdminPermissionDeniedException
import com.makd.afinity.data.models.admin.AdminRequestStillRunningException
import com.makd.afinity.data.models.admin.CollectionChoice
import com.makd.afinity.data.models.admin.DeleteSummary
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.data.models.admin.ItemImage
import com.makd.afinity.data.models.admin.LyricsContent
import com.makd.afinity.data.models.admin.LyricsLineInfo
import com.makd.afinity.data.models.admin.LyricsNotSavedException
import com.makd.afinity.data.models.admin.LyricsTarget
import com.makd.afinity.data.models.admin.RemoteLyrics
import com.makd.afinity.data.models.admin.TrackLyrics
import com.makd.afinity.data.models.music.encodeLyricsJson
import com.makd.afinity.data.models.music.toAfinityLyricLines
import com.makd.afinity.data.network.toUtcString
import com.makd.afinity.data.repository.DatabaseRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.operations.CollectionApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemLookupApi
import org.jellyfin.sdk.api.operations.ItemUpdateApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.LyricApi
import org.jellyfin.sdk.api.operations.RemoteImageApi
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.AlbumInfo
import org.jellyfin.sdk.model.api.AlbumInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.BoxSetInfo
import org.jellyfin.sdk.model.api.BoxSetInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MetadataField
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.PersonLookupInfo
import org.jellyfin.sdk.model.api.PersonLookupInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.RatingType
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import timber.log.Timber

private const val SERVER_THUMB_WIDTH = 600
private const val COLLECTION_THUMB_WIDTH = 120
private const val PERSON_THUMB_WIDTH = 120
private const val SERVER_PREVIEW_WIDTH = 1600
private val APPLY_ACCEPTED_AFTER = 20.seconds
private const val ITEM_PATH = "/Items/{itemId}"
private const val ITEM_ID = "itemId"
private const val PREMIERE_DATE_KEY = "PremiereDate"
private const val END_DATE_KEY = "EndDate"
private const val DATE_CREATED_KEY = "DateCreated"
private const val MUSIC_ALBUM = "MusicAlbum"
private const val PERSON = "Person"
private const val TICKS_PER_MS = 10_000L
private const val NOT_FOUND = 404
private const val MAX_LYRIC_DELETES = 5
private const val LYRIC_POLL_ATTEMPTS = 12
private val LYRIC_POLL_INTERVAL = 300.milliseconds

private fun String.lastSeparator(): Int = maxOf(lastIndexOf('/'), lastIndexOf('\\'))

private fun String.parentDirectory(): String = substring(0, lastSeparator().coerceAtLeast(0))

private fun String.fileNamePart(): String = substring(lastSeparator() + 1)

private fun LyricDto.toLyricsContent(): LyricsContent =
    LyricsContent(
        lyrics
            .map { LyricsLineInfo(text = it.text, startMs = it.start?.div(TICKS_PER_MS)) }
            .dropLastWhile { it.text.isBlank() }
    )

@Singleton
class JellyfinAdminRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val databaseRepository: DatabaseRepository,
) : AdminRepository {

    private fun getApiClient() = sessionManager.getCurrentApiClient()

    private fun getUserId(): UUID? = sessionManager.currentSession.value?.userId

    override suspend fun getEditableItem(itemId: String): EditableItem? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = getApiClient() ?: return@withContext null
                val userId = getUserId() ?: return@withContext null
                val libraryApi = LibraryApi(apiClient)
                val itemUpdateApi = ItemUpdateApi(apiClient)
                val itemUuid = UUID.fromString(itemId)

                val itemResponse = libraryApi.getItem(userId = userId, itemId = itemUuid)
                val dto = itemResponse.content

                val editorResponse = itemUpdateApi.getMetadataEditorInfo(itemId = itemUuid)
                val editorInfo = editorResponse.content
                val availableRatings = editorInfo.parentalRatingOptions.mapNotNull { it.name }
                val providers =
                    editorInfo.externalIdInfos
                        .map {
                            ExternalIdProvider(
                                name = it.name,
                                key = it.key,
                                type = it.type?.serialName,
                            )
                        }
                        .distinctBy { it.key }

                dto.toEditableItem(availableRatings, providers)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get editable item $itemId")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting editable item $itemId")
                null
            }
        }

    override suspend fun updateItemMetadata(itemId: String, item: EditableItem): Result<Unit> =
        withContext(Dispatchers.IO) {
            var posted: TimeMark? = null
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val updateClient = sessionManager.getBackgroundApiClient() ?: apiClient
                val userId =
                    getUserId()
                        ?: return@withContext Result.failure(IllegalStateException("No user"))

                val libraryApi = LibraryApi(apiClient)
                val existing =
                    libraryApi
                        .getItem(
                            userId = userId,
                            itemId = UUID.fromString(itemId),
                        )
                        .content

                val updated =
                    existing.copy(
                        name = item.name,
                        originalTitle = item.originalTitle,
                        forcedSortName = item.forcedSortName,
                        album = item.album,
                        albumArtists =
                            if (item.albumArtists == existing.albumArtistNames()) null
                            else existing.albumArtists.withNames(item.albumArtists),
                        artistItems =
                            if (item.artists == existing.artists.orEmpty()) null
                            else existing.artistItems.withNames(item.artists),
                        overview = item.overview,
                        productionYear = item.productionYear,
                        productionLocations = item.productionLocations,
                        providerIds =
                            item.providerIds
                                .mapValues { it.value.trim() }
                                .filterValues { it.isNotEmpty() },
                        officialRating = item.officialRating,
                        customRating = item.customRating,
                        communityRating = item.communityRating?.toFloat(),
                        genres = item.genres,
                        tags = item.tags,
                        studios =
                            item.studios.map { NameGuidPair(name = it, id = UUID.randomUUID()) },
                        people = item.people.map { it.toBaseItemPerson() },
                        indexNumber = item.indexNumber,
                        parentIndexNumber = item.parentIndexNumber,
                        status = item.status,
                        displayOrder = item.displayOrder,
                        lockData = item.lockData,
                        lockedFields =
                            item.lockedFields.mapNotNull { MetadataField.fromNameOrNull(it) },
                        trickplay = null,
                        mediaSources = null,
                        mediaStreams = null,
                        chapters = null,
                        userData = null,
                        imageBlurHashes = null,
                        dateCreated = null,
                        dateLastMediaAdded = null,
                        startDate = null,
                    )
                posted = TimeSource.Monotonic.markNow()
                updateClient.request(
                    method = HttpMethod.POST,
                    pathTemplate = ITEM_PATH,
                    pathParameters = mapOf(ITEM_ID to UUID.fromString(itemId)),
                    requestBody = updated.toUpdateBody(existing.editedDates(item)),
                )
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: TimeoutException) {
                Timber.e(e, "Timed out updating item $itemId")
                val elapsed = posted?.elapsedNow()
                if (elapsed != null && elapsed >= APPLY_ACCEPTED_AFTER) {
                    adminChangeBroadcaster.notifyItemChanged(itemId)
                    Result.failure(AdminRequestStillRunningException(e))
                } else {
                    Result.failure(e)
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to update item $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error updating item $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getIdentifyTarget(itemId: String): IdentifyTarget? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = getApiClient() ?: return@withContext null
                val userId = getUserId() ?: return@withContext null
                val dto =
                    LibraryApi(apiClient)
                        .getItem(userId = userId, itemId = UUID.fromString(itemId))
                        .content
                val baseUrl = sessionManager.currentSession.value?.serverUrl ?: ""
                val primaryTag = dto.imageTags?.get(ImageType.PRIMARY)
                IdentifyTarget(
                    name = dto.name ?: "",
                    year = dto.productionYear,
                    type = dto.type.serialName,
                    path = dto.path,
                    imageUrl =
                        if (baseUrl.isNotEmpty() && primaryTag != null) {
                            "${baseUrl.trimEnd('/')}/Items/$itemId/Images/Primary" +
                                "?maxWidth=$SERVER_THUMB_WIDTH&quality=90&format=webp&tag=$primaryTag"
                        } else null,
                    albumArtist = dto.albumArtistNames().firstOrNull(),
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get identify target $itemId")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting identify target $itemId")
                null
            }
        }

    override suspend fun getExternalIdProviders(itemId: String): List<ExternalIdProvider> =
        withContext(Dispatchers.IO) {
            try {
                val api = ItemLookupApi(getApiClient() ?: return@withContext emptyList())
                val response = api.getExternalIdInfos(itemId = UUID.fromString(itemId))
                response.content
                    .map {
                        ExternalIdProvider(name = it.name, key = it.key, type = it.type?.serialName)
                    }
                    .distinctBy { it.key }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get external ID providers for $itemId")
                emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting external ID providers for $itemId")
                emptyList()
            }
        }

    override suspend fun searchRemoteResult(
        itemId: String,
        itemType: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
        albumArtist: String?,
    ): Result<List<IdentifyResult>> =
        withContext(Dispatchers.IO) {
            try {
                val client =
                    if (itemType == MUSIC_ALBUM) {
                        sessionManager.getBackgroundApiClient() ?: getApiClient()
                    } else {
                        getApiClient()
                    }
                val api =
                    ItemLookupApi(
                        client
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val itemUuid = UUID.fromString(itemId)
                val response =
                    when (itemType) {
                        "Movie" ->
                            api.getMovieRemoteSearchResults(
                                data =
                                    MovieInfoRemoteSearchQuery(
                                        itemId = itemUuid,
                                        searchInfo =
                                            MovieInfo(
                                                name = name,
                                                year = year,
                                                providerIds = providerIds,
                                                isAutomated = false,
                                            ),
                                        includeDisabledProviders = false,
                                    )
                            )

                        "Series" ->
                            api.getSeriesRemoteSearchResults(
                                data =
                                    SeriesInfoRemoteSearchQuery(
                                        itemId = itemUuid,
                                        searchInfo =
                                            SeriesInfo(
                                                name = name,
                                                year = year,
                                                providerIds = providerIds,
                                                isAutomated = false,
                                            ),
                                        includeDisabledProviders = false,
                                    )
                            )

                        "BoxSet" ->
                            api.getBoxSetRemoteSearchResults(
                                data =
                                    BoxSetInfoRemoteSearchQuery(
                                        itemId = itemUuid,
                                        searchInfo =
                                            BoxSetInfo(
                                                name = name,
                                                providerIds = providerIds,
                                                isAutomated = false,
                                            ),
                                        includeDisabledProviders = false,
                                    )
                            )

                        MUSIC_ALBUM ->
                            api.getMusicAlbumRemoteSearchResults(
                                data =
                                    AlbumInfoRemoteSearchQuery(
                                        itemId = itemUuid,
                                        searchInfo =
                                            AlbumInfo(
                                                name = name,
                                                providerIds = providerIds,
                                                isAutomated = false,
                                                albumArtists = listOfNotNull(albumArtist),
                                                artistProviderIds = emptyMap(),
                                                songInfos = emptyList(),
                                            ),
                                        includeDisabledProviders = false,
                                    )
                            )

                        PERSON ->
                            api.getPersonRemoteSearchResults(
                                data =
                                    PersonLookupInfoRemoteSearchQuery(
                                        itemId = itemUuid,
                                        searchInfo =
                                            PersonLookupInfo(
                                                name = name,
                                                providerIds = providerIds,
                                                isAutomated = false,
                                            ),
                                        includeDisabledProviders = false,
                                    )
                            )

                        else ->
                            return@withContext Result.failure(
                                IllegalArgumentException("Identify not supported for $itemType")
                            )
                    }
                Result.success(response.content.map { it.toIdentifyResult() })
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to search $itemType matches for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error searching $itemType matches for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun applyIdentifyResult(
        itemId: String,
        result: IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val started = TimeSource.Monotonic.markNow()
            try {
                val api =
                    ItemLookupApi(
                        sessionManager.getBackgroundApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val body =
                    RemoteSearchResult(
                        name = result.name,
                        productionYear = result.year,
                        imageUrl = result.imageUrl,
                        searchProviderName = result.searchProviderName,
                        providerIds = result.providerIds,
                        overview = result.overview,
                        artists = emptyList(),
                    )
                api.applySearchCriteria(
                    itemId = UUID.fromString(itemId),
                    replaceAllImages = replaceAllImages,
                    data = body,
                )
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: TimeoutException) {
                Timber.e(e, "Timed out applying identify result to $itemId")
                if (started.elapsedNow() >= APPLY_ACCEPTED_AFTER) {
                    adminChangeBroadcaster.notifyItemChanged(itemId)
                    Result.failure(AdminRequestStillRunningException(e))
                } else {
                    Result.failure(e)
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to apply identify result to $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error applying identify result to $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getLyricsTarget(trackId: String): Result<LyricsTarget> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val userId =
                    getUserId()
                        ?: return@withContext Result.failure(IllegalStateException("No user"))
                val uuid = UUID.fromString(trackId)
                val dto = LibraryApi(apiClient).getItem(userId = userId, itemId = uuid).content
                val lyrics =
                    fetchServedLyrics(apiClient, uuid)?.let { content ->
                        TrackLyrics(content = content, sidecarFileName = dto.sidecarLyricFileName())
                    }
                Result.success(
                    LyricsTarget(
                        trackName = dto.name.orEmpty(),
                        artist = dto.artists?.firstOrNull() ?: dto.albumArtistNames().firstOrNull(),
                        lyrics = lyrics,
                    )
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load lyrics for $trackId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error loading lyrics for $trackId")
                Result.failure(e)
            }
        }

    override suspend fun searchRemoteLyrics(trackId: String): Result<List<RemoteLyrics>> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getBackgroundApiClient()
                        ?: getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val results =
                    LyricApi(apiClient)
                        .searchRemoteLyrics(itemId = UUID.fromString(trackId))
                        .content
                Result.success(
                    results.map { info ->
                        val content = info.lyrics.toLyricsContent()
                        val metadata = info.lyrics.metadata
                        RemoteLyrics(
                            id = info.id,
                            providerName = info.providerName,
                            title = metadata.title,
                            artist = metadata.artist,
                            album = metadata.album,
                            lengthMs = metadata.length?.div(TICKS_PER_MS)?.takeIf { it > 0 },
                            synced = metadata.isSynced ?: content.synced,
                            content = content,
                        )
                    }
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to search lyrics for $trackId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error searching lyrics for $trackId")
                Result.failure(e)
            }
        }

    override suspend fun replaceWithRemoteLyrics(trackId: String, lyricId: String): Result<Unit> =
        writeLyrics(trackId) { apiClient, uuid ->
            deleteAllServedLyrics(apiClient, uuid)
            LyricApi(apiClient).downloadRemoteLyrics(itemId = uuid, lyricId = lyricId)
            if (!awaitServedLyrics(apiClient, uuid)) throw LyricsNotSavedException()
        }

    override suspend fun replaceWithUploadedLyrics(
        trackId: String,
        fileName: String,
        text: String,
    ): Result<Unit> =
        writeLyrics(trackId) { apiClient, uuid ->
            deleteAllServedLyrics(apiClient, uuid)
            LyricApi(apiClient).uploadLyrics(itemId = uuid, fileName = fileName, data = text)
            if (!awaitServedLyrics(apiClient, uuid)) throw LyricsNotSavedException()
        }

    override suspend fun deleteLyrics(trackId: String): Result<Unit> =
        writeLyrics(trackId) { apiClient, uuid -> deleteAllServedLyrics(apiClient, uuid) }

    private suspend fun writeLyrics(
        trackId: String,
        block: suspend (ApiClient, UUID) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getBackgroundApiClient()
                        ?: getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val uuid = UUID.fromString(trackId)
                try {
                    block(apiClient, uuid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    syncLyricsCache(apiClient, uuid)
                    adminChangeBroadcaster.notifyItemChanged(trackId)
                    throw e
                }
                syncLyricsCache(apiClient, uuid)
                adminChangeBroadcaster.notifyItemChanged(trackId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to change lyrics for $trackId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error changing lyrics for $trackId")
                Result.failure(e)
            }
        }

    private suspend fun fetchServedLyrics(apiClient: ApiClient, trackId: UUID): LyricsContent? =
        try {
            LyricApi(apiClient).getLyrics(itemId = trackId).content.toLyricsContent()
        } catch (e: InvalidStatusException) {
            if (e.status == NOT_FOUND) null else throw e
        }

    private suspend fun deleteAllServedLyrics(apiClient: ApiClient, trackId: UUID) {
        repeat(MAX_LYRIC_DELETES) {
            if (fetchServedLyrics(apiClient, trackId) == null) return
            LyricApi(apiClient).deleteLyrics(itemId = trackId)
        }
    }

    private suspend fun awaitServedLyrics(apiClient: ApiClient, trackId: UUID): Boolean {
        repeat(LYRIC_POLL_ATTEMPTS) {
            if (fetchServedLyrics(apiClient, trackId) != null) return true
            delay(LYRIC_POLL_INTERVAL)
        }
        return false
    }

    private suspend fun syncLyricsCache(apiClient: ApiClient, trackId: UUID) {
        val session = sessionManager.currentSession.value ?: return
        val userId = session.userId.toString()
        try {
            if (databaseRepository.getMusicLyricsJson(trackId, session.serverId, userId) == null) {
                return
            }
            val lines =
                try {
                    LyricApi(apiClient)
                        .getLyrics(itemId = trackId)
                        .content
                        .lyrics
                        .toAfinityLyricLines()
                } catch (e: InvalidStatusException) {
                    if (e.status == NOT_FOUND) emptyList() else throw e
                }
            if (lines.isEmpty()) {
                databaseRepository.deleteMusicLyrics(trackId, session.serverId, userId)
            } else {
                databaseRepository.insertMusicLyrics(
                    trackId,
                    session.serverId,
                    userId,
                    encodeLyricsJson(lines),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh cached lyrics for $trackId")
        }
    }

    private fun BaseItemDto.sidecarLyricFileName(): String? {
        val audioDirectory = path?.parentDirectory() ?: return null
        val lyricPath =
            mediaStreams?.firstOrNull { it.type == MediaStreamType.LYRIC }?.path ?: return null
        return if (lyricPath.parentDirectory().equals(audioDirectory, ignoreCase = true)) {
            lyricPath.fileNamePart()
        } else null
    }

    override suspend fun getItemImagesResult(itemId: String): Result<List<ItemImage>> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val baseUrl = sessionManager.currentSession.value?.serverUrl ?: ""
                val response =
                    ImageApi(apiClient).getItemImageInfos(itemId = UUID.fromString(itemId))
                Result.success(
                    response.content.map { info ->
                        ItemImage(
                            imageType = info.imageType.serialName,
                            imageIndex = info.imageIndex,
                            url = serverImageUrl(baseUrl, itemId, info, SERVER_THUMB_WIDTH),
                            providerName = null,
                            width = info.width ?: 0,
                            height = info.height ?: 0,
                            communityRating = null,
                            voteCount = null,
                            language = null,
                            isServerImage = true,
                            remoteUrl = null,
                            previewUrl =
                                serverImageUrl(baseUrl, itemId, info, SERVER_PREVIEW_WIDTH),
                            fileSize = info.size,
                            isLocalFile = info.path?.startsWith("http", ignoreCase = true) != true,
                        )
                    }
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get images for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting images for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getRemoteImagesResult(
        itemId: String,
        includeAllLanguages: Boolean,
    ): Result<List<ItemImage>> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    RemoteImageApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val response =
                    api.getRemoteImages(
                        itemId = UUID.fromString(itemId),
                        includeAllLanguages = includeAllLanguages,
                    )
                Result.success(
                    response.content.images.orEmpty().map { info ->
                        ItemImage(
                            imageType = info.type.serialName,
                            imageIndex = null,
                            url = info.thumbnailUrl ?: info.url,
                            providerName = info.providerName,
                            width = info.width ?: 0,
                            height = info.height ?: 0,
                            communityRating = info.communityRating,
                            voteCount = info.voteCount,
                            language = info.language,
                            isServerImage = false,
                            remoteUrl = info.url,
                            previewUrl = info.url,
                            ratingIsLikes = info.ratingType == RatingType.LIKES,
                        )
                    }
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get remote images for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting remote images for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getSupportedImageTypes(itemId: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val api = RemoteImageApi(getApiClient() ?: return@withContext emptyList())
                api.getRemoteImageProviders(itemId = UUID.fromString(itemId))
                    .content
                    .flatMap { it.supportedImages }
                    .map { it.serialName }
                    .distinct()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get image providers for $itemId")
                emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting image providers for $itemId")
                emptyList()
            }
        }

    override suspend fun downloadRemoteImage(
        itemId: String,
        imageType: String,
        imageUrl: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    RemoteImageApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val type =
                    ImageType.fromNameOrNull(imageType)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Unknown image type: $imageType")
                        )
                api.downloadRemoteImage(
                    itemId = UUID.fromString(itemId),
                    type = type,
                    imageUrl = imageUrl,
                )
                adminChangeBroadcaster.notifyImagesChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to download remote image for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error downloading remote image for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun uploadImage(
        itemId: String,
        imageType: String,
        imageData: ByteArray,
        mimeType: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    ImageApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val type =
                    ImageType.fromNameOrNull(imageType)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Unknown image type: $imageType")
                        )
                val base64Data =
                    Base64.encodeToString(imageData, Base64.NO_WRAP).toByteArray(Charsets.US_ASCII)
                val normalizedMime =
                    when (val cleaned = mimeType.substringBefore(';').trim().lowercase()) {
                        "image/jpg" -> "image/jpeg"
                        else -> cleaned
                    }
                api.setItemImage(
                    itemId = UUID.fromString(itemId),
                    imageType = type,
                    data = FileInfo(content = base64Data, mediaType = normalizedMime),
                )
                adminChangeBroadcaster.notifyImagesChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to upload image for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error uploading image for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun deleteImage(
        itemId: String,
        imageType: String,
        imageIndex: Int?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    ImageApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val type =
                    ImageType.fromNameOrNull(imageType)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Unknown image type: $imageType")
                        )
                api.deleteItemImage(
                    itemId = UUID.fromString(itemId),
                    imageType = type,
                    imageIndex = imageIndex,
                )
                adminChangeBroadcaster.notifyImagesChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to delete image for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error deleting image for $itemId")
                Result.failure(e)
            }
        }

    override suspend fun moveImage(
        itemId: String,
        imageType: String,
        fromIndex: Int,
        toIndex: Int,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            var moved = false
            try {
                val api =
                    ImageApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                val type =
                    ImageType.fromNameOrNull(imageType)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Unknown image type: $imageType")
                        )
                val itemUuid = UUID.fromString(itemId)
                val step = if (toIndex > fromIndex) 1 else -1
                var index = fromIndex
                while (index != toIndex) {
                    api.updateItemImageIndex(
                        itemId = itemUuid,
                        imageType = type,
                        imageIndex = index,
                        newIndex = index + step,
                    )
                    moved = true
                    index += step
                }
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to move image for $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error moving image for $itemId")
                Result.failure(e)
            } finally {
                if (moved) adminChangeBroadcaster.notifyImagesChanged(itemId)
            }
        }

    override suspend fun refreshItem(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
        regenerateTrickplay: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    LibraryApi(
                        getApiClient()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No API client")
                            )
                    )
                api.refreshItem(
                    itemId = UUID.fromString(itemId),
                    metadataRefreshMode =
                        MetadataRefreshMode.fromNameOrNull(metadataRefreshMode)
                            ?: MetadataRefreshMode.DEFAULT,
                    imageRefreshMode =
                        MetadataRefreshMode.fromNameOrNull(imageRefreshMode)
                            ?: MetadataRefreshMode.DEFAULT,
                    replaceAllMetadata = replaceAllMetadata,
                    replaceAllImages = replaceAllImages,
                    regenerateTrickplay = regenerateTrickplay,
                )
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to refresh item $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error refreshing item $itemId")
                Result.failure(e)
            }
        }

    override suspend fun canDeleteItem(itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = getApiClient() ?: return@withContext false
                val userId = getUserId() ?: return@withContext false
                LibraryApi(apiClient)
                    .getItem(userId = userId, itemId = UUID.fromString(itemId))
                    .content
                    .canDelete == true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to check delete permission for $itemId")
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error checking delete permission for $itemId")
                false
            }
        }

    override suspend fun getDeleteSummary(itemId: String): DeleteSummary? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = getApiClient() ?: return@withContext null
                val userId = getUserId() ?: return@withContext null
                val dto =
                    LibraryApi(apiClient)
                        .getItems(
                            userId = userId,
                            ids = listOf(UUID.fromString(itemId)),
                            fields =
                                listOf(
                                    ItemFields.PATH,
                                    ItemFields.CHILD_COUNT,
                                    ItemFields.RECURSIVE_ITEM_COUNT,
                                ),
                        )
                        .content
                        .items
                        .firstOrNull() ?: return@withContext null
                DeleteSummary(
                    path = dto.path,
                    childCount = dto.childCount ?: 0,
                    recursiveItemCount = dto.recursiveItemCount ?: 0,
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load delete summary for $itemId")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error loading delete summary for $itemId")
                null
            }
        }

    override suspend fun deleteItem(
        itemId: String,
        tmdbId: Int?,
        isMovie: Boolean?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val started = TimeSource.Monotonic.markNow()
            try {
                val apiClient =
                    sessionManager.getBackgroundApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                LibraryApi(apiClient).deleteItem(itemId = UUID.fromString(itemId))
                adminChangeBroadcaster.notifyItemDeleted(itemId, tmdbId, isMovie)
                Result.success(Unit)
            } catch (e: TimeoutException) {
                Timber.e(e, "Timed out deleting item $itemId")
                if (started.elapsedNow() >= APPLY_ACCEPTED_AFTER) {
                    Result.failure(AdminRequestStillRunningException(e))
                } else {
                    Result.failure(e)
                }
            } catch (e: InvalidStatusException) {
                Timber.e(e, "Failed to delete item $itemId")
                if (e.status == 401 || e.status == 403) {
                    Result.failure(AdminPermissionDeniedException(e))
                } else {
                    Result.failure(e)
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to delete item $itemId")
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error deleting item $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getCollections(): Result<List<CollectionChoice>> =
        collectionResult("Failed to load collections") { apiClient ->
            val baseUrl = sessionManager.currentSession.value?.serverUrl.orEmpty().trimEnd('/')
            LibraryApi(apiClient)
                .getItems(
                    userId = getUserId(),
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    fields = listOf(ItemFields.CHILD_COUNT),
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .map { dto ->
                    val tag = dto.imageTags?.get(ImageType.PRIMARY)
                    CollectionChoice(
                        id = dto.id.toString(),
                        name = dto.name.orEmpty(),
                        itemCount = dto.childCount,
                        imageUrl =
                            if (baseUrl.isNotEmpty() && tag != null) {
                                "$baseUrl/Items/${dto.id}/Images/Primary" +
                                    "?maxWidth=$COLLECTION_THUMB_WIDTH&quality=90&format=webp&tag=$tag"
                            } else null,
                    )
                }
        }

    override suspend fun createCollection(
        name: String,
        itemIds: List<String>,
        searchMetadata: Boolean,
    ): Result<String> =
        collectionResult("Failed to create collection") { apiClient ->
            val created =
                CollectionApi(apiClient)
                    .createCollection(name = name, ids = itemIds, isLocked = !searchMetadata)
                    .content
            itemIds.forEach(adminChangeBroadcaster::notifyItemChanged)
            created.id.toString()
        }

    override suspend fun addToCollection(
        collectionId: String,
        itemIds: List<String>,
    ): Result<Unit> =
        collectionResult("Failed to add to collection $collectionId") { apiClient ->
            CollectionApi(apiClient)
                .addToCollection(
                    collectionId = UUID.fromString(collectionId),
                    ids = itemIds.map(UUID::fromString),
                )
            (itemIds + collectionId).forEach(adminChangeBroadcaster::notifyItemChanged)
        }

    override suspend fun removeFromCollection(
        collectionId: String,
        itemIds: List<String>,
    ): Result<Unit> =
        collectionResult("Failed to remove from collection $collectionId") { apiClient ->
            CollectionApi(apiClient)
                .removeFromCollection(
                    collectionId = UUID.fromString(collectionId),
                    ids = itemIds.map(UUID::fromString),
                )
            (itemIds + collectionId).forEach(adminChangeBroadcaster::notifyItemChanged)
        }

    private suspend fun <T> collectionResult(
        failure: String,
        block: suspend (ApiClient) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                Result.success(block(apiClient))
            } catch (e: InvalidStatusException) {
                Timber.e(e, failure)
                if (e.status == 401 || e.status == 403) {
                    Result.failure(AdminPermissionDeniedException(e))
                } else {
                    Result.failure(e)
                }
            } catch (e: ApiClientException) {
                Timber.e(e, failure)
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, failure)
                Result.failure(e)
            }
        }

    private fun serverImageUrl(
        baseUrl: String,
        itemId: String,
        info: ImageInfo,
        maxWidth: Int,
    ): String? {
        if (baseUrl.isEmpty()) return null
        return buildString {
            append(baseUrl.trimEnd('/'))
            append("/Items/$itemId/Images/${info.imageType.serialName}")
            info.imageIndex?.let { append("/$it") }
            append("?maxWidth=$maxWidth&quality=90&format=webp")
            info.imageTag?.let { append("&tag=$it") }
        }
    }

    private fun BaseItemDto.toEditableItem(
        availableRatings: List<String>,
        providers: List<ExternalIdProvider>,
    ): EditableItem =
        EditableItem(
            id = id.toString(),
            name = name ?: "",
            originalTitle = originalTitle,
            forcedSortName = forcedSortName,
            album = album,
            albumArtists = albumArtistNames(),
            artists = artists.orEmpty(),
            overview = overview,
            productionYear = productionYear,
            premiereDate = premiereDate.editableDate(),
            endDate = endDate.editableDate(),
            dateCreated = dateCreated.editableDate(),
            productionLocations = productionLocations.orEmpty(),
            providerIds =
                providerIds
                    .orEmpty()
                    .mapNotNull { (key, value) -> value?.let { key to it } }
                    .toMap(),
            officialRating = officialRating,
            customRating = customRating,
            communityRating = communityRating?.toString()?.toDoubleOrNull(),
            genres = genres ?: emptyList(),
            tags = tags ?: emptyList(),
            studios = studios?.mapNotNull { it.name } ?: emptyList(),
            people =
                people?.map { person ->
                    EditablePerson(
                        id = person.id.toString(),
                        name = person.name ?: "",
                        type = person.type.serialName,
                        role = person.role,
                        imageUrl = personImageUrl(person.id.toString(), person.primaryImageTag),
                    )
                } ?: emptyList(),
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            status = status,
            displayOrder = displayOrder,
            lockData = lockData ?: false,
            lockedFields = lockedFields?.map { it.serialName } ?: emptyList(),
            type = type.serialName,
            path = path,
            availableParentalRatings = availableRatings,
            externalIdProviders = providers,
        )

    private fun personImageUrl(personId: String, tag: String?): String? {
        val baseUrl = sessionManager.currentSession.value?.serverUrl.orEmpty().trimEnd('/')
        if (baseUrl.isEmpty() || tag == null) return null
        return "$baseUrl/Items/$personId/Images/Primary" +
            "?maxWidth=$PERSON_THUMB_WIDTH&quality=90&format=webp&tag=$tag"
    }

    private fun LocalDateTime?.editableDate(): String? =
        this?.atZone(ZoneId.systemDefault())
            ?.withZoneSameInstant(ZoneOffset.UTC)
            ?.toLocalDate()
            ?.takeIf { it.year > 1 }
            ?.toString()

    private fun LocalDateTime?.dateBodyValue(edited: String?): JsonElement =
        when {
            edited == editableDate() -> this?.let { JsonPrimitive(it.toUtcString()) } ?: JsonNull
            edited == null -> JsonNull
            else -> JsonPrimitive(edited)
        }

    private fun BaseItemDto.editedDates(item: EditableItem): Map<String, JsonElement> = buildMap {
        put(PREMIERE_DATE_KEY, premiereDate.dateBodyValue(item.premiereDate))
        put(END_DATE_KEY, endDate.dateBodyValue(item.endDate))
        val created = item.dateCreated
        if (created != null && created != dateCreated.editableDate()) {
            put(DATE_CREATED_KEY, JsonPrimitive(created))
        }
    }

    private fun BaseItemDto.albumArtistNames(): List<String> =
        albumArtists?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(albumArtist?.takeIf { it.isNotBlank() })

    private fun List<NameGuidPair>?.withNames(names: List<String>): List<NameGuidPair> =
        names.map { name ->
            this?.firstOrNull { it.name == name }
                ?: NameGuidPair(name = name, id = UUID.randomUUID())
        }

    private fun BaseItemDto.toUpdateBody(dates: Map<String, JsonElement>): JsonObject =
        JsonObject(
            ApiSerializer.json.encodeToJsonElement(BaseItemDto.serializer(), this).jsonObject +
                dates
        )

    private fun EditablePerson.toBaseItemPerson(): BaseItemPerson =
        BaseItemPerson(
            id = if (id != null) UUID.fromString(id) else UUID.randomUUID(),
            name = name,
            role = role,
            type = PersonKind.fromNameOrNull(type) ?: PersonKind.UNKNOWN,
        )

    private fun RemoteSearchResult.toIdentifyResult(): IdentifyResult =
        IdentifyResult(
            name = name ?: "",
            year = productionYear,
            imageUrl = imageUrl,
            searchProviderName = searchProviderName,
            providerIds = providerIds.filterValues { it.isNotEmpty() },
            overview = overview,
            premiereDate = premiereDate?.toString(),
            albumArtist = albumArtist?.name ?: artists.firstOrNull()?.name,
        )
}
