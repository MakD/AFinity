package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.JellyseerrConfigEntity
import com.makd.afinity.data.database.entities.JellyseerrRequestEntity
import com.makd.afinity.data.models.jellyseerr.CreateRequestBody
import com.makd.afinity.data.models.jellyseerr.JellyfinLoginRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.LoginRequest
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.TvDetails
import com.makd.afinity.data.network.JellyseerrApiService
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyseerrRepositoryImpl @Inject constructor(
    private val apiService: Lazy<JellyseerrApiService>,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val database: AfinityDatabase,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor
) : JellyseerrRepository {

    private val jellyseerrDao = database.jellyseerrDao()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    companion object {
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            _isAuthenticated.value = hasValidAuthData()
        }
    }

    override suspend fun login(
        email: String,
        password: String,
        useJellyfinAuth: Boolean
    ): Result<JellyseerrUser> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = if (useJellyfinAuth) {
                    val jellyfinRequest = JellyfinLoginRequest(email, password)
                    apiService.get().loginJellyfin(jellyfinRequest)
                } else {
                    val localRequest = LoginRequest(email, password)
                    apiService.get().loginLocal(localRequest)
                }

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!

                    val cookies = response.headers()["Set-Cookie"]
                    cookies?.let {
                        securePreferencesRepository.saveJellyseerrCookie(it)
                    }

                    securePreferencesRepository.saveJellyseerrUsername(
                        loginResponse.username ?: loginResponse.email ?: "User"
                    )

                    jellyseerrDao.saveConfig(
                        JellyseerrConfigEntity(
                            id = 1,
                            serverUrl = securePreferencesRepository.getJellyseerrServerUrl() ?: "",
                            isLoggedIn = true,
                            username = loginResponse.username,
                            userId = loginResponse.id,
                            permissions = loginResponse.permissions
                        )
                    )

                    _isAuthenticated.value = true

                    val user = JellyseerrUser(
                        id = loginResponse.id,
                        email = loginResponse.email,
                        username = loginResponse.username,
                        displayName = loginResponse.displayName,
                        permissions = loginResponse.permissions,
                        avatar = loginResponse.avatar,
                        requestCount = loginResponse.requestCount,
                        movieQuotaLimit = loginResponse.movieQuotaLimit,
                        movieQuotaDays = loginResponse.movieQuotaDays,
                        tvQuotaLimit = loginResponse.tvQuotaLimit,
                        tvQuotaDays = loginResponse.tvQuotaDays
                    )

                    Timber.d("Jellyseerr login successful for user: ${user.username}")
                    Result.success(user)
                } else {
                    val errorMsg = "Login failed: ${response.code()} - ${response.message()}"
                    Timber.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Jellyseerr login failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (networkConnectivityMonitor.isCurrentlyConnected()) {
                    try {
                        apiService.get().logout()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to logout from server, continuing with local cleanup")
                    }
                }

                securePreferencesRepository.clearJellyseerrAuthData()
                jellyseerrDao.clearConfig()
                jellyseerrDao.clearAllRequests()

                _isAuthenticated.value = false
                Timber.d("Jellyseerr logout successful")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Jellyseerr logout failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun getCurrentUser(): Result<JellyseerrUser> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getCurrentUser()

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get current user: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user")
                Result.failure(e)
            }
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return hasValidAuthData()
    }

    override suspend fun setServerUrl(url: String) {
        withContext(Dispatchers.IO) {
            securePreferencesRepository.saveJellyseerrServerUrl(url)
        }
    }

    override suspend fun getServerUrl(): String? {
        return withContext(Dispatchers.IO) {
            securePreferencesRepository.getJellyseerrServerUrl()
        }
    }

    override suspend fun hasValidConfiguration(): Boolean {
        return withContext(Dispatchers.IO) {
            val serverUrl = securePreferencesRepository.getJellyseerrServerUrl()
            !serverUrl.isNullOrBlank()
        }
    }

    override suspend fun createRequest(
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>?
    ): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val requestBody = CreateRequestBody(
                    tmdbId = mediaId,
                    mediaType = mediaType.toApiString(),
                    seasons = seasons
                )

                val response = apiService.get().createRequest(requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val request = response.body()!!

                    cacheRequest(request)

                    Timber.d("Request created successfully: ${request.id}")
                    Result.success(request)
                } else {
                    Timber.e("Request body: tmdbId=${requestBody.tmdbId}, mediaType=${requestBody.mediaType}, seasons=${requestBody.seasons}, is4k=${requestBody.is4k}")

                    val errorBody = try {
                        response.errorBody()?.string()
                    } catch (e: Exception) {
                        null
                    }

                    val errorMsg = "Failed to create request: ${response.code()} - ${response.message()}" +
                        if (errorBody != null) "\nError details: $errorBody" else ""
                    Timber.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Jellyseerr request")
                Result.failure(e)
            }
        }
    }

    override suspend fun getRequests(page: Int, filter: String?): Result<List<JellyseerrRequest>> {
        return withContext(Dispatchers.IO) {
            try {
                if (networkConnectivityMonitor.isCurrentlyConnected()) {
                    try {
                        val skip = (page - 1) * 20
                        val response = apiService.get().getRequests(
                            take = 20,
                            skip = skip,
                            filter = filter
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val requests = response.body()!!.results
                            val enrichedRequests = coroutineScope {
                                requests.map { request ->
                                    async {
                                        try {
                                            val tmdbId = request.media.tmdbId
                                            if (tmdbId != null) {
                                                val detailsResponse = when (request.media.mediaType.lowercase()) {
                                                    "movie" -> apiService.get().getMovieDetails(tmdbId)
                                                    "tv" -> apiService.get().getTvDetails(tmdbId)
                                                    else -> null
                                                }

                                                if (detailsResponse?.isSuccessful == true && detailsResponse.body() != null) {
                                                    val details = detailsResponse.body()!!
                                                    request.copy(
                                                        media = request.media.copy(
                                                            title = details.title,
                                                            name = details.name,
                                                            posterPath = details.posterPath,
                                                            backdropPath = details.backdropPath,
                                                            releaseDate = details.releaseDate,
                                                            firstAirDate = details.firstAirDate
                                                        )
                                                    )
                                                } else {
                                                    request
                                                }
                                            } else {
                                                request
                                            }
                                        } catch (e: Exception) {
                                            Timber.w(e, "Failed to enrich request ${request.id} with media details")
                                            request
                                        }
                                    }
                                }.awaitAll()
                            }

                            if (page == 1) {
                                val expiryTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
                                jellyseerrDao.deleteExpiredRequests(expiryTime)
                            }

                            enrichedRequests.forEach { cacheRequest(it) }

                            return@withContext Result.success(enrichedRequests)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch from network, falling back to cache")
                    }
                }

                val cachedRequests = jellyseerrDao.getAllRequests()
                var cachedList: List<JellyseerrRequestEntity> = emptyList()
                cachedRequests.collect { cachedList = it }

                if (cachedList.isNotEmpty()) {
                    val requests = cachedList.map { it.toJellyseerrRequest() }
                    Timber.d("Returned ${requests.size} cached requests")
                    Result.success(requests)
                } else {
                    Result.failure(Exception("No cached data available"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Jellyseerr requests")
                Result.failure(e)
            }
        }
    }

    override suspend fun getRequestById(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getRequestById(requestId)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get request: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get request by ID")
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteRequest(requestId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().deleteRequest(requestId)

                if (response.isSuccessful) {
                    jellyseerrDao.deleteRequest(requestId)
                    Timber.d("Request deleted successfully: $requestId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete request: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete request")
                Result.failure(e)
            }
        }
    }

    override suspend fun approveRequest(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().approveRequest(requestId)

                if (response.isSuccessful && response.body() != null) {
                    val request = response.body()!!
                    cacheRequest(request)
                    Result.success(request)
                } else {
                    Result.failure(Exception("Failed to approve request: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to approve request")
                Result.failure(e)
            }
        }
    }

    override suspend fun declineRequest(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().declineRequest(requestId)

                if (response.isSuccessful && response.body() != null) {
                    val request = response.body()!!
                    cacheRequest(request)
                    Result.success(request)
                } else {
                    Result.failure(Exception("Failed to decline request: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to decline request")
                Result.failure(e)
            }
        }
    }

    override suspend fun searchMedia(query: String, page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().search(query, page)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Search failed: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Jellyseerr search failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun findMediaByName(
        name: String,
        mediaType: MediaType?
    ): Result<List<SearchResultItem>> {
        return searchMedia(name, 1).map { searchResult ->
            if (mediaType != null) {
                searchResult.results.filter {
                    it.getMediaType() == mediaType
                }
            } else {
                searchResult.results
            }
        }
    }

    override suspend fun getTvDetails(tvId: Int): Result<TvDetails> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getTvDetails(tvId)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get TV details: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get TV show details for ID: $tvId")
                Result.failure(e)
            }
        }
    }

    override suspend fun getTrending(page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getTrending(page)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get trending: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get trending content")
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverMovies(page: Int, sortBy: String): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getDiscoverMovies(page, sortBy = sortBy)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get discover movies: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get discover movies")
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverTv(page: Int, sortBy: String): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getDiscoverTv(page, sortBy = sortBy)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get discover TV: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get discover TV")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUpcomingMovies(page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getUpcomingMovies(page)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get upcoming movies: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get upcoming movies")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUpcomingTv(page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getUpcomingTv(page)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get upcoming TV: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get upcoming TV")
                Result.failure(e)
            }
        }
    }

    private suspend fun hasValidAuthData(): Boolean {
        return securePreferencesRepository.hasValidJellyseerrAuth()
    }

    private suspend fun cacheRequest(request: JellyseerrRequest) {
        try {
            val entity = request.toEntity()
            jellyseerrDao.insertRequest(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache request")
        }
    }

    private fun JellyseerrRequest.toEntity(): JellyseerrRequestEntity {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

        return JellyseerrRequestEntity(
            id = id,
            status = status,
            mediaType = media.mediaType,
            tmdbId = media.tmdbId,
            tvdbId = media.tvdbId,
            title = "Unknown",
            posterPath = null,
            requestedAt = try {
                dateFormat.parse(createdAt)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            },
            updatedAt = try {
                dateFormat.parse(updatedAt)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            },
            requestedByName = requestedBy.displayName,
            requestedByAvatar = requestedBy.avatar
        )
    }

    private fun JellyseerrRequestEntity.toJellyseerrRequest(): JellyseerrRequest {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

        return JellyseerrRequest(
            id = id,
            status = status,
            media = com.makd.afinity.data.models.jellyseerr.MediaInfo(
                id = id,
                mediaType = mediaType,
                tmdbId = tmdbId,
                tvdbId = tvdbId,
                status = status,
                mediaAddedAt = null
            ),
            requestedBy = com.makd.afinity.data.models.jellyseerr.RequestUser(
                id = 0,
                displayName = requestedByName,
                avatar = requestedByAvatar
            ),
            modifiedBy = null,
            createdAt = dateFormat.format(Date(requestedAt)),
            updatedAt = dateFormat.format(Date(updatedAt)),
            seasons = null
        )
    }
}
