package com.makd.afinity.data.repository.userdata

import java.util.UUID
import org.jellyfin.sdk.model.api.UserItemDataDto

interface UserDataRepository {

    suspend fun markWatched(itemId: UUID): Boolean

    suspend fun markUnwatched(itemId: UUID): Boolean

    suspend fun updatePlaybackPosition(itemId: UUID, positionTicks: Long): Boolean

    suspend fun getUserData(itemId: UUID): UserItemDataDto?

    suspend fun addToFavorites(itemId: UUID): Boolean

    suspend fun removeFromFavorites(itemId: UUID): Boolean

    suspend fun getFavoriteItems(
        includeItemTypes: List<String> = emptyList(),
        limit: Int? = null,
        startIndex: Int = 0,
    ): List<UUID>

    suspend fun setRating(itemId: UUID, rating: Int): Boolean

    suspend fun removeRating(itemId: UUID): Boolean

    suspend fun setLike(itemId: UUID, isLiked: Boolean): Boolean

    suspend fun syncUserData(items: List<UserItemDataDto>): Boolean

    suspend fun getUserDataBatch(itemIds: List<UUID>): Map<UUID, UserItemDataDto>
}
