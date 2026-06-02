package com.makd.afinity.data.repository.admin

import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.ItemImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemLookupApi
import org.jellyfin.sdk.api.operations.ItemRefreshApi
import org.jellyfin.sdk.api.operations.ItemUpdateApi
import org.jellyfin.sdk.api.operations.RemoteImageApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MetadataField
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
                val userLibraryApi = UserLibraryApi(apiClient)
                val itemUpdateApi = ItemUpdateApi(apiClient)
                val itemUuid = UUID.fromString(itemId)

                val itemResponse = userLibraryApi.getItem(userId = userId, itemId = itemUuid)
                val dto = itemResponse.content

                val editorResponse = itemUpdateApi.getMetadataEditorInfo(itemId = itemUuid)
                val editorInfo = editorResponse.content
                val availableRatings = editorInfo.parentalRatingOptions.mapNotNull { it.name }

                dto.toEditableItem(availableRatings)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get editable item $itemId")
                null
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

                val userLibraryApi = UserLibraryApi(apiClient)
                val existing =
                    userLibraryApi
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
                    )
                api.updateItem(itemId = UUID.fromString(itemId), data = updated)
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to update item $itemId")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error updating item $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getExternalIdProviders(itemId: String): List<ExternalIdProvider> =
        withContext(Dispatchers.IO) {
            try {
                val api = ItemLookupApi(getApiClient() ?: return@withContext emptyList())
                val response = api.getExternalIdInfos(itemId = UUID.fromString(itemId))
                response.content.map {
                    ExternalIdProvider(
                        name = it.name,
                        key = it.key,
                        urlFormatString = it.urlFormatString,
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get external ID providers for $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting external ID providers for $itemId")
                emptyList()
            }
        }

    override suspend fun searchMovie(
        itemId: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
    ): List<IdentifyResult> =
        withContext(Dispatchers.IO) {
            try {
                val api = ItemLookupApi(getApiClient() ?: return@withContext emptyList())
                val query =
                    MovieInfoRemoteSearchQuery(
                        itemId = UUID.fromString(itemId),
                        searchInfo =
                            MovieInfo(
                                name = name,
                                year = year,
                                providerIds = providerIds,
                                isAutomated = false,
                            ),
                        includeDisabledProviders = false,
                    )
                api.getMovieRemoteSearchResults(data = query).content.map { it.toIdentifyResult() }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to search movie for $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error searching movie for $itemId")
                emptyList()
            }
        }

    override suspend fun searchSeries(
        itemId: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
    ): List<IdentifyResult> =
        withContext(Dispatchers.IO) {
            try {
                val api = ItemLookupApi(getApiClient() ?: return@withContext emptyList())
                val query =
                    SeriesInfoRemoteSearchQuery(
                        itemId = UUID.fromString(itemId),
                        searchInfo =
                            SeriesInfo(
                                name = name,
                                year = year,
                                providerIds = providerIds,
                                isAutomated = false,
                            ),
                        includeDisabledProviders = false,
                    )
                api.getSeriesRemoteSearchResults(data = query).content.map { it.toIdentifyResult() }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to search series for $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error searching series for $itemId")
                emptyList()
            }
        }

    override suspend fun applyIdentifyResult(
        itemId: String,
        result: IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api =
                    ItemLookupApi(
                        getApiClient()
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
                    )
                api.applySearchCriteria(
                    itemId = UUID.fromString(itemId),
                    replaceAllImages = replaceAllImages,
                    data = body,
                )
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to apply identify result to $itemId")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error applying identify result to $itemId")
                Result.failure(e)
            }
        }

    override suspend fun getItemImages(itemId: String): List<ItemImage> =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = getApiClient() ?: return@withContext emptyList()
                val api = ImageApi(apiClient)
                val baseUrl = sessionManager.currentSession.value?.serverUrl ?: ""
                val response = api.getItemImageInfos(itemId = UUID.fromString(itemId))
                response.content.map { info ->
                    val imageUrl =
                        if (baseUrl.isNotEmpty()) {
                            val base =
                                "$baseUrl/Items/$itemId/Images/${info.imageType.serialName}" +
                                    if (info.imageIndex != null) "/${info.imageIndex}" else ""
                            if (info.imageTag != null) "$base?tag=${info.imageTag}" else base
                        } else null
                    ItemImage(
                        imageType = info.imageType.serialName,
                        imageIndex = info.imageIndex,
                        url = imageUrl,
                        providerName = null,
                        width = info.width ?: 0,
                        height = info.height ?: 0,
                        communityRating = null,
                        voteCount = null,
                        language = null,
                        isServerImage = true,
                        remoteUrl = null,
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get images for $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting images for $itemId")
                emptyList()
            }
        }

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String,
        includeAllLanguages: Boolean,
    ): List<ItemImage> =
        withContext(Dispatchers.IO) {
            try {
                val api = RemoteImageApi(getApiClient() ?: return@withContext emptyList())
                val type = ImageType.fromNameOrNull(imageType)
                val response =
                    api.getRemoteImages(
                        itemId = UUID.fromString(itemId),
                        type = type,
                        includeAllLanguages = includeAllLanguages,
                        limit = 50,
                    )
                response.content.images?.map { info ->
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
                    )
                } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get remote images for $itemId")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting remote images for $itemId")
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
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to download remote image for $itemId")
                Result.failure(e)
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
                api.setItemImage(
                    itemId = UUID.fromString(itemId),
                    imageType = type,
                    data = FileInfo(content = imageData, mediaType = mimeType),
                )
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to upload image for $itemId")
                Result.failure(e)
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
                adminChangeBroadcaster.notifyItemChanged(itemId)
                Result.success(Unit)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to delete image for $itemId")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error deleting image for $itemId")
                Result.failure(e)
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
                    ItemRefreshApi(
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
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error refreshing item $itemId")
                Result.failure(e)
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
            providerIds =
                providerIds?.mapValues { it.value ?: "" }?.filterValues { it.isNotEmpty() }
                    ?: emptyMap(),
            overview = overview,
            premiereDate = premiereDate?.toString(),
        )
}
