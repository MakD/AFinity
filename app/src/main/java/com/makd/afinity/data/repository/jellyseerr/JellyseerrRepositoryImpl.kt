package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.JellyseerrConfigEntity
import com.makd.afinity.data.database.entities.JellyseerrRequestEntity
import com.makd.afinity.data.models.jellyseerr.CreateRequestBody
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyfinLoginRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.LoginRequest
import com.makd.afinity.data.models.jellyseerr.MediaDetails
import com.makd.afinity.data.models.jellyseerr.MediaInfo
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.data.models.jellyseerr.RequestUser
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceDetailsResponse
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.network.JellyseerrApiService
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.RequestEvent
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyseerrRepositoryImpl
@Inject
constructor(
    private val apiService: Lazy<JellyseerrApiService>,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val database: AfinityDatabase,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) : JellyseerrRepository {

    private val jellyseerrDao = database.jellyseerrDao()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    override val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _requestEvents = MutableSharedFlow<RequestEvent>()
    override val requestEvents: SharedFlow<RequestEvent> = _requestEvents.asSharedFlow()

    private var activeContext: Pair<String, UUID>? = null

    companion object {
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    }

    override suspend fun setActiveJellyfinSession(serverId: String, userId: UUID) {
        Timber.d("Switching Jellyseerr context to Server: $serverId, User: $userId")
        _isAuthenticated.value = false
        activeContext = serverId to userId
        _currentSessionId.value = "${serverId}_$userId"

        val hasAuth = securePreferencesRepository.switchJellyseerrContext(serverId, userId)
        val config = jellyseerrDao.getConfig(serverId, userId.toString())

        _isAuthenticated.value = hasAuth && config?.isLoggedIn == true
        Timber.d("Jellyseerr Context Switched. Authenticated: ${_isAuthenticated.value}")
    }

    override fun clearActiveSession() {
        activeContext = null
        _currentSessionId.value = null
        securePreferencesRepository.clearActiveJellyseerrCache()
        _isAuthenticated.value = false
        Timber.d("Jellyseerr active session cleared")
    }

    override suspend fun login(
        email: String,
        password: String,
        useJellyfinAuth: Boolean,
    ): Result<JellyseerrUser> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) =
                activeContext
                    ?: return@withContext Result.failure(Exception("No active Jellyfin session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response =
                    if (useJellyfinAuth) {
                        val jellyfinRequest = JellyfinLoginRequest(email, password)
                        apiService.get().loginJellyfin(jellyfinRequest)
                    } else {
                        val localRequest = LoginRequest(email, password)
                        apiService.get().loginLocal(localRequest)
                    }

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val cookies = response.headers()["Set-Cookie"]
                    val serverUrl = securePreferencesRepository.getJellyseerrServerUrl() ?: ""

                    if (cookies != null) {
                        securePreferencesRepository.saveJellyseerrAuthForUser(
                            jellyfinServerId = currentServerId,
                            jellyfinUserId = currentUserId,
                            url = serverUrl,
                            cookie = cookies,
                            username = loginResponse.username ?: loginResponse.email ?: "User",
                        )
                    }

                    jellyseerrDao.saveConfig(
                        JellyseerrConfigEntity(
                            jellyfinServerId = currentServerId,
                            jellyfinUserId = currentUserId.toString(),
                            serverUrl = serverUrl,
                            isLoggedIn = true,
                            username = loginResponse.username,
                            userId = loginResponse.id,
                            permissions = loginResponse.permissions,
                        )
                    )

                    _isAuthenticated.value = true

                    val user =
                        JellyseerrUser(
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
                            tvQuotaDays = loginResponse.tvQuotaDays,
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
            val (currentServerId, currentUserId) =
                activeContext ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (hasValidConfiguration() && networkConnectivityMonitor.isCurrentlyConnected()) {
                    try {
                        apiService.get().logout()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to logout from server, continuing with local cleanup")
                    }
                }

                securePreferencesRepository.clearJellyseerrAuthForUser(
                    currentServerId,
                    currentUserId,
                )
                jellyseerrDao.clearConfig(currentServerId, currentUserId.toString())

                jellyseerrDao.clearAllRequests(currentServerId, currentUserId.toString())

                securePreferencesRepository.clearActiveJellyseerrCache()

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
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed to get current user: ${response.message()}"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user")
                Result.failure(e)
            }
        }
    }

    override suspend fun isLoggedIn(): Boolean = _isAuthenticated.value

    override suspend fun setServerUrl(url: String) {
        withContext(Dispatchers.IO) {
            securePreferencesRepository.saveJellyseerrServerUrl(url)
            activeContext?.let { (serverId, userId) ->
                val (_, cookie, username) =
                    securePreferencesRepository.getJellyseerrAuthForUser(serverId, userId)
                securePreferencesRepository.saveJellyseerrAuthForUser(
                    serverId,
                    userId,
                    url,
                    cookie ?: "",
                    username ?: "",
                )
            }
        }
    }

    override suspend fun getServerUrl(): String? {
        return withContext(Dispatchers.IO) { securePreferencesRepository.getJellyseerrServerUrl() }
    }

    override suspend fun hasValidConfiguration(): Boolean {
        return withContext(Dispatchers.IO) {
            !securePreferencesRepository.getJellyseerrServerUrl().isNullOrBlank()
        }
    }

    override suspend fun createRequest(
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>?,
        is4k: Boolean,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
    ): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val requestBody =
                    CreateRequestBody(
                        tmdbId = mediaId,
                        mediaType = mediaType.toApiString(),
                        seasons = seasons,
                        is4k = is4k,
                        serverId = serverId,
                        profileId = profileId,
                        rootFolder = rootFolder,
                    )
                val response = apiService.get().createRequest(requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val request = response.body()!!

                    val latestRequest =
                        try {
                            val fetchResponse = apiService.get().getRequestById(request.id)
                            if (fetchResponse.isSuccessful && fetchResponse.body() != null)
                                fetchResponse.body()!!
                            else request
                        } catch (e: Exception) {
                            request
                        }

                    cacheRequest(latestRequest)
                    _requestEvents.emit(RequestEvent(latestRequest))
                    Result.success(latestRequest)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg =
                        "Failed to create request: ${response.code()} ${if (errorBody != null) " - $errorBody" else ""}"
                    Timber.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Jellyseerr request")
                Result.failure(e)
            }
        }
    }

    override suspend fun getRequests(
        take: Int,
        skip: Int,
        filter: String?,
    ): Result<List<JellyseerrRequest>> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) =
                activeContext ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (networkConnectivityMonitor.isCurrentlyConnected()) {
                    try {
                        val response = apiService.get().getRequests(take, skip, filter)
                        if (response.isSuccessful && response.body() != null) {
                            val requests = response.body()!!.results

                            val enrichedRequests = coroutineScope {
                                requests
                                    .map { request ->
                                        async {
                                            try {
                                                val tmdbId = request.media.tmdbId
                                                if (tmdbId != null) {
                                                    val detailsResponse =
                                                        when (request.media.mediaType.lowercase()) {
                                                            "movie" ->
                                                                apiService
                                                                    .get()
                                                                    .getMovieDetails(tmdbId)

                                                            "tv" ->
                                                                apiService
                                                                    .get()
                                                                    .getTvDetails(tmdbId)

                                                            else -> null
                                                        }

                                                    if (
                                                        detailsResponse?.isSuccessful == true &&
                                                            detailsResponse.body() != null
                                                    ) {
                                                        val details = detailsResponse.body()!!
                                                        request.copy(
                                                            media =
                                                                request.media.copy(
                                                                    title = details.title,
                                                                    name = details.name,
                                                                    posterPath = details.posterPath,
                                                                    backdropPath =
                                                                        details.backdropPath,
                                                                    releaseDate =
                                                                        details.releaseDate,
                                                                    firstAirDate =
                                                                        details.firstAirDate,
                                                                )
                                                        )
                                                    } else request
                                                } else request
                                            } catch (e: Exception) {
                                                Timber.w(
                                                    e,
                                                    "Failed to enrich request ${request.id}",
                                                )
                                                request
                                            }
                                        }
                                    }
                                    .awaitAll()
                            }

                            if (skip == 0 && take >= 20) {
                                val expiryTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
                                jellyseerrDao.deleteExpiredRequests(
                                    expiryTime,
                                    currentServerId,
                                    currentUserId.toString(),
                                )
                            }

                            enrichedRequests.forEach { cacheRequest(it) }
                            return@withContext Result.success(enrichedRequests)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch from network, falling back to cache")
                    }
                }

                val cachedList =
                    jellyseerrDao.getAllRequests(currentServerId, currentUserId.toString()).first()
                if (cachedList.isNotEmpty()) {
                    Result.success(cachedList.map { it.toJellyseerrRequest() })
                } else {
                    Result.failure(Exception("No cached data available"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Jellyseerr requests")
                Result.failure(e)
            }
        }
    }

    override fun observeRequests(): Flow<List<JellyseerrRequest>> {
        val (serverId, userId) = activeContext ?: return flowOf(emptyList())
        return jellyseerrDao.getAllRequests(serverId, userId.toString()).map { entities ->
            entities.map { it.toJellyseerrRequest() }
        }
    }

    override suspend fun getRequestById(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getRequestById(requestId)
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteRequest(requestId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) =
                activeContext ?: return@withContext Result.failure(Exception("No active session"))
            try {
                val response = apiService.get().deleteRequest(requestId)
                if (response.isSuccessful) {
                    jellyseerrDao.deleteRequest(
                        requestId,
                        currentServerId,
                        currentUserId.toString(),
                    )
                    Result.success(Unit)
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun approveRequest(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().approveRequest(requestId)
                if (response.isSuccessful && response.body() != null) {
                    var req = response.body()!!
                    val (serverId, userId) =
                        activeContext ?: return@withContext Result.failure(Exception("No session"))
                    val existingEntity =
                        jellyseerrDao.getAllRequests(serverId, userId.toString()).first().find {
                            it.id == requestId
                        }
                    req =
                        req.copy(
                            status = RequestStatus.APPROVED.value,
                            media =
                                req.media.copy(
                                    status = MediaStatus.PROCESSING.value,
                                    title =
                                        if (req.media.title.isNullOrBlank())
                                            existingEntity?.mediaTitle
                                        else req.media.title,
                                    name =
                                        if (req.media.name.isNullOrBlank())
                                            existingEntity?.mediaName
                                        else req.media.name,
                                    posterPath = req.media.posterPath ?: existingEntity?.posterPath,
                                    backdropPath =
                                        req.media.backdropPath ?: existingEntity?.mediaBackdropPath,
                                    releaseDate =
                                        req.media.releaseDate ?: existingEntity?.mediaReleaseDate,
                                    firstAirDate =
                                        req.media.firstAirDate ?: existingEntity?.mediaFirstAirDate,
                                ),
                        )

                    cacheRequest(req)
                    Result.success(req)
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun declineRequest(requestId: Int): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().declineRequest(requestId)
                if (response.isSuccessful && response.body() != null) {
                    var req = response.body()!!
                    val (serverId, userId) =
                        activeContext ?: return@withContext Result.failure(Exception("No session"))
                    val existingEntity =
                        jellyseerrDao.getAllRequests(serverId, userId.toString()).first().find {
                            it.id == requestId
                        }
                    req =
                        req.copy(
                            status = RequestStatus.DECLINED.value,
                            media =
                                req.media.copy(
                                    title =
                                        if (req.media.title.isNullOrBlank())
                                            existingEntity?.mediaTitle
                                        else req.media.title,
                                    name =
                                        if (req.media.name.isNullOrBlank())
                                            existingEntity?.mediaName
                                        else req.media.name,
                                    posterPath = req.media.posterPath ?: existingEntity?.posterPath,
                                    backdropPath =
                                        req.media.backdropPath ?: existingEntity?.mediaBackdropPath,
                                    releaseDate =
                                        req.media.releaseDate ?: existingEntity?.mediaReleaseDate,
                                    firstAirDate =
                                        req.media.firstAirDate ?: existingEntity?.mediaFirstAirDate,
                                ),
                        )

                    cacheRequest(req)
                    Result.success(req)
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun searchMedia(query: String, page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().search(query, page)
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMovieDetails(movieId: Int): Result<MediaDetails> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getMovieDetails(movieId)

                if (response.isSuccessful && response.body() != null) {
                    val details = response.body()!!

                    val ratingsResponse =
                        try {
                            apiService.get().getMovieRatingsCombined(movieId)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to fetch ratings for movie $movieId")
                            null
                        }

                    val ratings =
                        if (ratingsResponse?.isSuccessful == true) {
                            ratingsResponse.body()
                        } else null

                    val enrichedDetails = details.copy(ratingsCombined = ratings)
                    Result.success(enrichedDetails)
                } else {
                    Result.failure(Exception("Failed to get movie details: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get movie details for ID: $movieId")
                Result.failure(e)
            }
        }
    }

    override suspend fun getTvDetails(tvId: Int): Result<MediaDetails> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getTvDetails(tvId)

                if (response.isSuccessful && response.body() != null) {
                    val details = response.body()!!

                    val ratingsResponse =
                        try {
                            apiService.get().getTvRatings(tvId)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to fetch ratings for TV show $tvId")
                            null
                        }

                    val ratings =
                        if (
                            ratingsResponse?.isSuccessful == true && ratingsResponse.body() != null
                        ) {
                            val rtRating = ratingsResponse.body()!!
                            RatingsCombined(rt = rtRating, imdb = null)
                        } else {
                            Timber.w("TV ratings response unsuccessful or null for tvId $tvId")
                            null
                        }

                    val enrichedDetails = details.copy(ratingsCombined = ratings)
                    Result.success(enrichedDetails)
                } else {
                    Result.failure(Exception("Failed to get TV details: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get TV show details for ID: $tvId")
                Result.failure(e)
            }
        }
    }

    override suspend fun findMediaByName(
        name: String,
        mediaType: MediaType?,
    ): Result<List<SearchResultItem>> {
        return searchMedia(name, 1).map { searchResult ->
            if (mediaType != null) searchResult.results.filter { it.getMediaType() == mediaType }
            else searchResult.results
        }
    }

    override suspend fun getTrending(page: Int, limit: Int?): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getTrending(page)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Result.success(
                        if (limit != null) body.copy(results = body.results.take(limit)) else body
                    )
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverMovies(
        page: Int,
        sortBy: String,
        studio: Int?,
        limit: Int?,
    ): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    apiService
                        .get()
                        .getDiscoverMovies(page = page, sortBy = sortBy, studio = studio)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Result.success(
                        if (limit != null) body.copy(results = body.results.take(limit)) else body
                    )
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverTv(
        page: Int,
        sortBy: String,
        network: Int?,
        limit: Int?,
    ): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    apiService.get().getDiscoverTv(page = page, sortBy = sortBy, network = network)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Result.success(
                        if (limit != null) body.copy(results = body.results.take(limit)) else body
                    )
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getUpcomingMovies(page: Int, limit: Int?): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getUpcomingMovies(page)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Result.success(
                        if (limit != null) body.copy(results = body.results.take(limit)) else body
                    )
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getUpcomingTv(page: Int, limit: Int?): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getUpcomingTv(page)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Result.success(
                        if (limit != null) body.copy(results = body.results.take(limit)) else body
                    )
                } else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMoviesByStudio(
        studioId: Int,
        page: Int,
    ): Result<JellyseerrSearchResult> = getDiscoverMovies(page, studio = studioId)

    override suspend fun getTvByNetwork(networkId: Int, page: Int): Result<JellyseerrSearchResult> =
        getDiscoverTv(page, network = networkId)

    override suspend fun getMovieGenreSlider(): Result<List<GenreSliderItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getMovieGenreSlider()
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTvGenreSlider(): Result<List<GenreSliderItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getTvGenreSlider()
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMoviesByGenre(genreId: Int, page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getMoviesByGenre(genreId, page)
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTvByGenre(genreId: Int, page: Int): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getTvByGenre(genreId, page)
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getServiceSettings(mediaType: MediaType): Result<List<ServiceSettings>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response =
                    when (mediaType) {
                        MediaType.MOVIE -> apiService.get().getRadarrSettings()
                        MediaType.TV -> apiService.get().getSonarrSettings()
                    }
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception("Failed to get service settings: ${response.message()}")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get service settings")
                Result.failure(e)
            }
        }
    }

    override suspend fun getServiceDetails(
        mediaType: MediaType,
        serviceId: Int,
    ): Result<ServiceDetailsResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response =
                    when (mediaType) {
                        MediaType.MOVIE -> apiService.get().getRadarrDetails(serviceId)
                        MediaType.TV -> apiService.get().getSonarrDetails(serviceId)
                    }
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception("Failed to get service details: ${response.message()}")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get service details")
                Result.failure(e)
            }
        }
    }

    private suspend fun cacheRequest(request: JellyseerrRequest) {
        val (serverId, userId) = activeContext ?: return
        try {
            val entity = request.toEntity(serverId, userId.toString())
            jellyseerrDao.insertRequest(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache request")
        }
    }

    private fun JellyseerrRequest.toEntity(
        serverId: String,
        userId: String,
    ): JellyseerrRequestEntity {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val seasonsJsonString =
            if (seasons != null) {
                kotlinx.serialization.json.Json.encodeToString(seasons)
            } else null
        return JellyseerrRequestEntity(
            id = id,
            jellyfinServerId = serverId,
            jellyfinUserId = userId,
            status = status,
            mediaType = media.mediaType,
            tmdbId = media.tmdbId,
            tvdbId = media.tvdbId,
            title = media.title ?: media.name ?: "Unknown",
            posterPath = media.posterPath,
            requestedAt =
                try {
                    dateFormat.parse(createdAt)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                },
            updatedAt =
                try {
                    dateFormat.parse(updatedAt)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                },
            requestedByName = requestedBy.displayName,
            requestedByAvatar = requestedBy.avatar,
            mediaTitle = media.title,
            mediaName = media.name,
            mediaBackdropPath = media.backdropPath,
            mediaReleaseDate = media.releaseDate,
            mediaFirstAirDate = media.firstAirDate,
            mediaStatus = media.status,
            mediaStatus4k = media.status4k,
            is4k = is4k,
            serverId = this.serverId,
            profileId = profileId,
            rootFolder = rootFolder,
            seasonsJson = seasonsJsonString,
        )
    }

    private fun JellyseerrRequestEntity.toJellyseerrRequest(): JellyseerrRequest {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val seasonsList =
            if (seasonsJson != null) {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<
                        List<com.makd.afinity.data.models.jellyseerr.SeasonRequest>
                    >(
                        seasonsJson
                    )
                } catch (e: Exception) {
                    null
                }
            } else null
        return JellyseerrRequest(
            id = id,
            status = status,
            media =
                MediaInfo(
                    id = id,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    tvdbId = tvdbId,
                    status = mediaStatus,
                    status4k = mediaStatus4k,
                    mediaAddedAt = null,
                    title = mediaTitle,
                    name = mediaName,
                    posterPath = posterPath,
                    backdropPath = mediaBackdropPath,
                    releaseDate = mediaReleaseDate,
                    firstAirDate = mediaFirstAirDate,
                ),
            requestedBy = RequestUser(0, requestedByName, requestedByAvatar),
            modifiedBy = null,
            createdAt = dateFormat.format(Date(requestedAt)),
            updatedAt = dateFormat.format(Date(updatedAt)),
            seasons = seasonsList,
            is4k = is4k,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder,
        )
    }
}
