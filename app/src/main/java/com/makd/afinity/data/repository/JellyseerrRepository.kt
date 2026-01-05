package com.makd.afinity.data.repository

import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.TvDetails
import kotlinx.coroutines.flow.StateFlow

interface JellyseerrRepository {

    suspend fun login(
        email: String,
        password: String,
        useJellyfinAuth: Boolean = false
    ): Result<JellyseerrUser>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): Result<JellyseerrUser>
    suspend fun isLoggedIn(): Boolean
    val isAuthenticated: StateFlow<Boolean>

    suspend fun setServerUrl(url: String)
    suspend fun getServerUrl(): String?
    suspend fun hasValidConfiguration(): Boolean

    suspend fun createRequest(
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>? = null
    ): Result<JellyseerrRequest>

    suspend fun getRequests(
        page: Int = 1,
        filter: String? = null
    ): Result<List<JellyseerrRequest>>

    suspend fun getRequestById(requestId: Int): Result<JellyseerrRequest>

    suspend fun deleteRequest(requestId: Int): Result<Unit>

    suspend fun approveRequest(requestId: Int): Result<JellyseerrRequest>

    suspend fun declineRequest(requestId: Int): Result<JellyseerrRequest>

    suspend fun searchMedia(query: String, page: Int = 1): Result<JellyseerrSearchResult>

    suspend fun getTvDetails(tvId: Int): Result<TvDetails>

    suspend fun findMediaByName(
        name: String,
        mediaType: MediaType? = null
    ): Result<List<SearchResultItem>>

    suspend fun getTrending(page: Int = 1): Result<JellyseerrSearchResult>
    suspend fun getDiscoverMovies(page: Int = 1, sortBy: String = "popularity.desc"): Result<JellyseerrSearchResult>
    suspend fun getDiscoverTv(page: Int = 1, sortBy: String = "popularity.desc"): Result<JellyseerrSearchResult>
    suspend fun getUpcomingMovies(page: Int = 1): Result<JellyseerrSearchResult>
    suspend fun getUpcomingTv(page: Int = 1): Result<JellyseerrSearchResult>
}
