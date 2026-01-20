package com.makd.afinity.data.repository

import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.MediaDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class RequestEvent(
    val request: JellyseerrRequest
)

interface JellyseerrRepository {

    suspend fun setActiveJellyfinSession(serverId: String, userId: UUID)
    fun clearActiveSession()

    val currentSessionId: StateFlow<String?>

    suspend fun login(
        email: String,
        password: String,
        useJellyfinAuth: Boolean = false
    ): Result<JellyseerrUser>

    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): Result<JellyseerrUser>
    suspend fun isLoggedIn(): Boolean
    val isAuthenticated: StateFlow<Boolean>
    val requestEvents: SharedFlow<RequestEvent>

    suspend fun setServerUrl(url: String)
    suspend fun getServerUrl(): String?
    suspend fun hasValidConfiguration(): Boolean

    suspend fun createRequest(
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>? = null
    ): Result<JellyseerrRequest>

    suspend fun getRequests(
        take: Int = 20,
        skip: Int = 0,
        filter: String? = null
    ): Result<List<JellyseerrRequest>>

    fun observeRequests(): Flow<List<JellyseerrRequest>>

    suspend fun getRequestById(requestId: Int): Result<JellyseerrRequest>

    suspend fun deleteRequest(requestId: Int): Result<Unit>

    suspend fun approveRequest(requestId: Int): Result<JellyseerrRequest>

    suspend fun declineRequest(requestId: Int): Result<JellyseerrRequest>

    suspend fun searchMedia(query: String, page: Int = 1): Result<JellyseerrSearchResult>

    suspend fun getMovieDetails(movieId: Int): Result<MediaDetails>

    suspend fun getTvDetails(tvId: Int): Result<MediaDetails>

    suspend fun findMediaByName(
        name: String,
        mediaType: MediaType? = null
    ): Result<List<SearchResultItem>>

    suspend fun getTrending(page: Int = 1, limit: Int? = null): Result<JellyseerrSearchResult>
    suspend fun getDiscoverMovies(
        page: Int = 1,
        sortBy: String = "popularity.desc",
        studio: Int? = null,
        limit: Int? = null
    ): Result<JellyseerrSearchResult>

    suspend fun getDiscoverTv(
        page: Int = 1,
        sortBy: String = "popularity.desc",
        network: Int? = null,
        limit: Int? = null
    ): Result<JellyseerrSearchResult>

    suspend fun getUpcomingMovies(page: Int = 1, limit: Int? = null): Result<JellyseerrSearchResult>
    suspend fun getUpcomingTv(page: Int = 1, limit: Int? = null): Result<JellyseerrSearchResult>

    suspend fun getMoviesByStudio(studioId: Int, page: Int = 1): Result<JellyseerrSearchResult>
    suspend fun getTvByNetwork(networkId: Int, page: Int = 1): Result<JellyseerrSearchResult>
    suspend fun getMovieGenreSlider(): Result<List<GenreSliderItem>>
    suspend fun getTvGenreSlider(): Result<List<GenreSliderItem>>
    suspend fun getMoviesByGenre(genreId: Int, page: Int = 1): Result<JellyseerrSearchResult>
    suspend fun getTvByGenre(genreId: Int, page: Int = 1): Result<JellyseerrSearchResult>
}