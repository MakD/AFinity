package com.makd.afinity.data.repository.userdata

import com.makd.afinity.data.manager.SessionManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber

@Singleton
class JellyfinUserDataRepository @Inject constructor(private val sessionManager: SessionManager) :
    UserDataRepository {

    private suspend fun getCurrentUserId(): UUID? {
        return sessionManager.currentSession.value?.userId
    }

    override suspend fun markWatched(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.markPlayedItem(
                    itemId = itemId,
                    userId = userId,
                    datePlayed = org.jellyfin.sdk.model.DateTime.now(),
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to mark item as watched: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error marking item as watched: $itemId")
                false
            }
        }
    }

    override suspend fun markUnwatched(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.markUnplayedItem(itemId = itemId, userId = userId)
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to mark item as unwatched: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error marking item as unwatched: $itemId")
                false
            }
        }
    }

    override suspend fun updatePlaybackPosition(itemId: UUID, positionTicks: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.onPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isPaused = true,
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to update playback position for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error updating playback position for item: $itemId")
                false
            }
        }
    }

    override suspend fun getUserData(itemId: UUID): UserItemDataDto? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val itemsApi = ItemsApi(apiClient)

                val response = itemsApi.getItemUserData(itemId = itemId, userId = userId)
                response.content
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get user data for item: $itemId")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting user data for item: $itemId")
                null
            }
        }
    }

    override suspend fun addToFavorites(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val userLibraryApi = UserLibraryApi(apiClient)

                userLibraryApi.markFavoriteItem(itemId = itemId, userId = userId)
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to add item to favorites: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error adding item to favorites: $itemId")
                false
            }
        }
    }

    override suspend fun removeFromFavorites(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val userLibraryApi = UserLibraryApi(apiClient)

                userLibraryApi.unmarkFavoriteItem(itemId = itemId, userId = userId)
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to remove item from favorites: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error removing item from favorites: $itemId")
                false
            }
        }
    }

    override suspend fun getFavoriteItems(
        includeItemTypes: List<String>,
        limit: Int?,
        startIndex: Int,
    ): List<UUID> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        isFavorite = true,
                        includeItemTypes =
                            includeItemTypes.mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            },
                        limit = limit,
                        startIndex = startIndex,
                        fields = listOf(ItemFields.OVERVIEW),
                        enableUserData = true,
                    )

                response.content?.items?.mapNotNull { it.id } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get favorite items")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting favorite items")
                emptyList()
            }
        }
    }

    override suspend fun setRating(itemId: UUID, rating: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val itemsApi = ItemsApi(apiClient)

                val jellyfinRating = rating.coerceIn(0, 10)

                itemsApi.updateItemUserData(
                    itemId = itemId,
                    userId = userId,
                    data = UpdateUserItemDataDto(rating = jellyfinRating.toDouble()),
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to set rating for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error setting rating for item: $itemId")
                false
            }
        }
    }

    override suspend fun removeRating(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val itemsApi = ItemsApi(apiClient)

                itemsApi.updateItemUserData(
                    itemId = itemId,
                    userId = userId,
                    data = UpdateUserItemDataDto(rating = null),
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to remove rating for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error removing rating for item: $itemId")
                false
            }
        }
    }

    override suspend fun setLike(itemId: UUID, isLiked: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val itemsApi = ItemsApi(apiClient)

                itemsApi.updateItemUserData(
                    itemId = itemId,
                    userId = userId,
                    data = UpdateUserItemDataDto(likes = isLiked),
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to set like status for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error setting like status for item: $itemId")
                false
            }
        }
    }

    override suspend fun syncUserData(items: List<UserItemDataDto>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext false
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                var successCount = 0

                items.forEach { userDataDto ->
                    try {
                        userDataDto.itemId?.let { itemId ->
                            val itemsApi = ItemsApi(apiClient)
                            itemsApi.updateItemUserData(
                                itemId = itemId,
                                userId = userId,
                                data =
                                    UpdateUserItemDataDto(
                                        rating = userDataDto.rating,
                                        played = userDataDto.played,
                                        playbackPositionTicks = userDataDto.playbackPositionTicks,
                                        playCount = userDataDto.playCount,
                                        isFavorite = userDataDto.isFavorite,
                                        likes = userDataDto.likes,
                                        lastPlayedDate = userDataDto.lastPlayedDate,
                                    ),
                            )
                            successCount++
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to sync user data for item: ${userDataDto.itemId}")
                    }
                }

                val successRate = successCount.toFloat() / items.size
                successRate > 0.5f
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync user data")
                false
            }
        }
    }

    override suspend fun getUserDataBatch(itemIds: List<UUID>): Map<UUID, UserItemDataDto> {
        return withContext(Dispatchers.IO) {
            try {
                val result = mutableMapOf<UUID, UserItemDataDto>()

                itemIds.forEach { itemId ->
                    getUserData(itemId)?.let { userData -> result[itemId] = userData }
                }

                result
            } catch (e: Exception) {
                Timber.e(e, "Failed to get user data batch")
                emptyMap()
            }
        }
    }
}
