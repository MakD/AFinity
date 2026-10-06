package com.makd.afinity.data.repository.admin

import android.util.Base64
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyStillRunningException
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.data.models.admin.ItemImage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemLookupApi
import org.jellyfin.sdk.api.operations.ItemUpdateApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.RemoteImageApi
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.BoxSetInfo
import org.jellyfin.sdk.model.api.BoxSetInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MetadataField
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.RatingType
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import timber.log.Timber

private const val SERVER_THUMB_WIDTH = 600
private const val SERVER_PREVIEW_WIDTH = 1600
private val APPLY_ACCEPTED_AFTER = 20.seconds

@Singleton
class JellyfinAdminRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
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

                dto.toEditableItem(availableRatings)
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
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                val api = ItemUpdateApi(apiClient)
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
                        overview = item.overview,
                        productionYear = item.productionYear,
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
                    )
                api.updateItem(itemId = UUID.fromString(itemId), data = updated)
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
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
                response.content.map {
                    ExternalIdProvider(name = it.name, key = it.key, type = it.type?.serialName)
                }
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
    ): Result<List<IdentifyResult>> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    ItemLookupApi(
                        getApiClient()
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
                    Result.failure(IdentifyStillRunningException(e))
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

    override suspend fun deleteItem(
        itemId: String,
        tmdbId: Int?,
        isMovie: Boolean?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    getApiClient()
                        ?: return@withContext Result.failure(IllegalStateException("No API client"))
                LibraryApi(apiClient).deleteItem(itemId = UUID.fromString(itemId))
                adminChangeBroadcaster.notifyItemDeleted(itemId, tmdbId, isMovie)
                Result.success(Unit)
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

    private fun BaseItemDto.toEditableItem(availableRatings: List<String>): EditableItem =
        EditableItem(
            id = id.toString(),
            name = name ?: "",
            originalTitle = originalTitle,
            overview = overview,
            productionYear = productionYear,
            premiereDate = premiereDate?.toString(),
            officialRating = officialRating,
            customRating = customRating,
            communityRating = communityRating?.toDouble(),
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
        )
}
