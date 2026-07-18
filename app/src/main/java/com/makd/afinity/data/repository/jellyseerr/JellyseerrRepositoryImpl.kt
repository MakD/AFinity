package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.JellyseerrAddressEntity
import com.makd.afinity.data.database.entities.JellyseerrConfigEntity
import com.makd.afinity.data.database.entities.JellyseerrDiscoverFilterEntity
import com.makd.afinity.data.database.entities.JellyseerrRequestEntity
import com.makd.afinity.data.models.jellyseerr.CollectionDetails
import com.makd.afinity.data.models.jellyseerr.CreateRequestBody
import com.makd.afinity.data.models.jellyseerr.DiscoverFilterOptions
import com.makd.afinity.data.models.jellyseerr.DiscoverSlider
import com.makd.afinity.data.models.jellyseerr.Genre
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
import com.makd.afinity.data.models.jellyseerr.PersonCombinedCreditsResponse
import com.makd.afinity.data.models.jellyseerr.PublicSettings
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.data.models.jellyseerr.RequestUser
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceDetailsResponse
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.models.jellyseerr.SonarrSeries
import com.makd.afinity.data.models.jellyseerr.TmdbKeywordSearchResponse
import com.makd.afinity.data.models.jellyseerr.UserQuotaResponse
import com.makd.afinity.data.models.jellyseerr.WatchProviderDetails
import com.makd.afinity.data.models.jellyseerr.WatchProviderRegion
import com.makd.afinity.data.models.server.AddressCheck
import com.makd.afinity.data.network.JellyseerrApiService
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.RequestEvent
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val addressResolver: JellyseerrAddressResolver,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : JellyseerrRepository {

    private val jellyseerrDao = database.jellyseerrDao()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    override val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _requestEvents = MutableSharedFlow<RequestEvent>()
    override val requestEvents: SharedFlow<RequestEvent> = _requestEvents.asSharedFlow()

    private var activeContext: Pair<String, UUID>? = null

    private var cachedPublicSettings: PublicSettings? = null

    companion object {
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    }

    private suspend fun <T> seerrResult(
        errorMessage: String,
        call: suspend (JellyseerrApiService) -> retrofit2.Response<T>,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response = call(apiService.get())
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("$errorMessage: ${response.message()}"))
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, errorMessage)
                Result.failure(e)
            }
        }

    init {
        repositoryScope.launch {
            networkConnectivityMonitor.isNetworkAvailable.collect { isAvailable ->
                if (!isAvailable) return@collect
                val (serverId, userId) = activeContext ?: return@collect
                if (!_isAuthenticated.value) return@collect

                val config = jellyseerrDao.getConfig(serverId, userId.toString()) ?: return@collect
                try {
                    val result =
                        addressResolver.resolveAddress(
                            serverId,
                            userId.toString(),
                            config.serverUrl,
                        )
                    if (
                        result is JellyseerrAddressResult.Success &&
                            result.address !=
                                securePreferencesRepository.getCachedJellyseerrServerUrl()
                    ) {
                        Timber.d("Jellyseerr: Network changed, switching to ${result.address}")
                        securePreferencesRepository.updateCachedJellyseerrServerUrl(result.address)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Jellyseerr: Failed to re-resolve address on network change")
                }
            }
        }
    }

    override suspend fun verifyServer(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var cleanUrl = url.trim().removeSuffix("/")
                if (!cleanUrl.endsWith("/api/v1/status", ignoreCase = true)) {
                    cleanUrl = "$cleanUrl/api/v1/status"
                }

                val client =
                    okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                val request = okhttp3.Request.Builder().url(cleanUrl).get().build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Timber.d("Jellyseerr server verification failed for $url: ${e.message}")
                false
            }
        }
    }

    private fun identityClient() =
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    private fun fetchInstanceId(baseUrl: String): String? {
        return try {
            val request =
                okhttp3.Request.Builder().url("$baseUrl/api/v1/settings/public").get().build()
            identityClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                Json.parseToJsonElement(body)
                    .jsonObject["plexClientIdentifier"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Timber.d("Jellyseerr instance id fetch failed for $baseUrl: ${e.message}")
            null
        }
    }

    override suspend fun verifyAddressIdentity(url: String): AddressCheck {
        return withContext(Dispatchers.IO) {
            val candidateBase = url.trim().removeSuffix("/")
            val currentBase =
                securePreferencesRepository
                    .getCachedJellyseerrServerUrl()
                    ?.trim()
                    ?.removeSuffix("/")
                    ?.takeIf { it.isNotBlank() }

            if (currentBase != null) {
                val currentId = fetchInstanceId(currentBase)
                val candidateId = fetchInstanceId(candidateBase)
                if (currentId != null && candidateId != null) {
                    return@withContext if (currentId == candidateId) AddressCheck.SAME_SERVER
                    else AddressCheck.DIFFERENT_SERVER
                }
            }

            verifyAddressIdentityByCredential(candidateBase)
        }
    }

    private suspend fun verifyAddressIdentityByCredential(url: String): AddressCheck {
        return withContext(Dispatchers.IO) {
            try {
                val currentUserId =
                    getCurrentUser().getOrNull()?.id
                        ?: return@withContext AddressCheck.INDETERMINATE
                val rawCookie =
                    securePreferencesRepository.getCachedJellyseerrCookie()
                        ?: return@withContext AddressCheck.INDETERMINATE

                val base = url.trim().removeSuffix("/")
                val httpUrl =
                    "$base/api/v1/auth/me".toHttpUrlOrNull()
                        ?: return@withContext AddressCheck.INDETERMINATE
                val cookie =
                    okhttp3.Cookie.parse(httpUrl, rawCookie)
                        ?: return@withContext AddressCheck.INDETERMINATE

                val request =
                    okhttp3.Request.Builder()
                        .url(httpUrl)
                        .header("Cookie", "${cookie.name}=${cookie.value}")
                        .get()
                        .build()

                identityClient().newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        return@use AddressCheck.DIFFERENT_SERVER
                    }
                    if (!response.isSuccessful) return@use AddressCheck.INDETERMINATE
                    val body = response.body?.string() ?: return@use AddressCheck.INDETERMINATE
                    val id =
                        Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.intOrNull
                    when (id) {
                        null -> AddressCheck.INDETERMINATE
                        currentUserId -> AddressCheck.SAME_SERVER
                        else -> AddressCheck.DIFFERENT_SERVER
                    }
                }
            } catch (e: Exception) {
                Timber.d("Jellyseerr identity check failed for $url: ${e.message}")
                AddressCheck.INDETERMINATE
            }
        }
    }

    override suspend fun setActiveJellyfinSession(serverId: String, userId: UUID) {
        Timber.d("Switching Jellyseerr context to Server: $serverId, User: $userId")
        _isAuthenticated.value = false
        cachedPublicSettings = null
        activeContext = serverId to userId
        _currentSessionId.value = "${serverId}_$userId"

        val hasAuth = securePreferencesRepository.switchJellyseerrContext(serverId, userId)
        val config = jellyseerrDao.getConfig(serverId, userId.toString())

        if (hasAuth && config?.isLoggedIn == true) {
            if (networkConnectivityMonitor.isCurrentlyConnected()) {
                try {
                    val result =
                        addressResolver.resolveAddress(
                            serverId,
                            userId.toString(),
                            config.serverUrl,
                        )
                    if (
                        result is JellyseerrAddressResult.Success &&
                            result.address != config.serverUrl
                    ) {
                        Timber.d(
                            "Jellyseerr: Resolved to ${result.address} (config: ${config.serverUrl})"
                        )
                        securePreferencesRepository.updateCachedJellyseerrServerUrl(result.address)
                    } else if (result is JellyseerrAddressResult.AllFailed) {
                        Timber.w("Jellyseerr: All addresses failed: ${result.attemptedAddresses}")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Jellyseerr: Address resolution failed, using config URL")
                }
            }
            _isAuthenticated.value = true
        }

        Timber.d("Jellyseerr Context Switched. Authenticated: ${_isAuthenticated.value}")
    }

    override fun clearActiveSession() {
        activeContext = null
        cachedPublicSettings = null
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
                    val existingConfig =
                        jellyseerrDao.getConfig(currentServerId, currentUserId.toString())
                    if (
                        existingConfig != null &&
                            existingConfig.serverUrl != serverUrl &&
                            existingConfig.serverUrl.isNotBlank()
                    ) {
                        val oldExists =
                            jellyseerrDao.getAddressByUrl(
                                currentServerId,
                                currentUserId.toString(),
                                existingConfig.serverUrl,
                            )
                        if (oldExists == null) {
                            jellyseerrDao.insertAddress(
                                JellyseerrAddressEntity(
                                    id = UUID.randomUUID(),
                                    jellyfinServerId = currentServerId,
                                    jellyfinUserId = currentUserId.toString(),
                                    address = existingConfig.serverUrl,
                                )
                            )
                        }
                    }
                    if (serverUrl.isNotBlank()) {
                        val newExists =
                            jellyseerrDao.getAddressByUrl(
                                currentServerId,
                                currentUserId.toString(),
                                serverUrl,
                            )
                        if (newExists == null) {
                            jellyseerrDao.insertAddress(
                                JellyseerrAddressEntity(
                                    id = UUID.randomUUID(),
                                    jellyfinServerId = currentServerId,
                                    jellyfinUserId = currentUserId.toString(),
                                    address = serverUrl,
                                )
                            )
                        }
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

                jellyseerrDao.clearDiscoverFilterState(currentServerId, currentUserId.toString())

                securePreferencesRepository.clearActiveJellyseerrCache()

                cachedPublicSettings = null
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

    override suspend fun getPublicSettings(forceRefresh: Boolean): Result<PublicSettings> {
        return withContext(Dispatchers.IO) {
            val cached = cachedPublicSettings
            if (cached != null && !forceRefresh) {
                return@withContext Result.success(cached)
            }
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response = apiService.get().getPublicSettings()
                if (response.isSuccessful && response.body() != null) {
                    val settings = response.body()!!
                    cachedPublicSettings = settings
                    Result.success(settings)
                } else {
                    Result.failure(
                        Exception("Failed to get public settings: ${response.message()}")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Jellyseerr public settings")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUserQuota(userId: Int): Result<UserQuotaResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response = apiService.get().getUserQuota(userId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get user quota: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Jellyseerr user quota")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUsers(take: Int): Result<List<JellyseerrUser>> {
        return seerrResult("Failed to get users") { api -> api.getUsers(take) }.map { it.results }
    }

    override suspend fun getPersonCombinedCredits(
        personId: Int
    ): Result<PersonCombinedCreditsResponse> {
        return seerrResult("Failed to get person credits") { api ->
            api.getPersonCombinedCredits(personId)
        }
    }

    override suspend fun getCollection(collectionId: Int): Result<CollectionDetails> {
        return seerrResult("Failed to get collection") { api -> api.getCollection(collectionId) }
    }

    override suspend fun getRecommendations(
        mediaType: MediaType,
        tmdbId: Int,
        page: Int,
    ): Result<JellyseerrSearchResult> {
        return seerrResult("Failed to get recommendations") { api ->
            when (mediaType) {
                MediaType.MOVIE -> api.getMovieRecommendations(tmdbId, page)
                MediaType.TV -> api.getTvRecommendations(tmdbId, page)
            }
        }
    }

    override suspend fun getSimilar(
        mediaType: MediaType,
        tmdbId: Int,
        page: Int,
    ): Result<JellyseerrSearchResult> {
        return seerrResult("Failed to get similar") { api ->
            when (mediaType) {
                MediaType.MOVIE -> api.getMovieSimilar(tmdbId, page)
                MediaType.TV -> api.getTvSimilar(tmdbId, page)
            }
        }
    }

    override suspend fun sonarrLookup(tmdbId: Int): Result<List<SonarrSeries>> {
        return seerrResult("Sonarr lookup failed") { api -> api.sonarrLookup(tmdbId) }
    }

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

    override suspend fun getAllKnownAddresses(): List<String> =
        withContext(Dispatchers.IO) { jellyseerrDao.getAllAddressStrings() }

    override suspend fun createRequest(
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>?,
        is4k: Boolean,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tvdbId: Int?,
        languageProfileId: Int?,
        tags: List<Int>?,
        userId: Int?,
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
                        tvdbId = tvdbId,
                        languageProfileId = languageProfileId,
                        tags = tags,
                        userId = userId,
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
                            val baseRequests = response.body()!!.results

                            if (skip == 0 && take >= 20) {
                                val expiryTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
                                jellyseerrDao.deleteExpiredRequests(
                                    expiryTime,
                                    currentServerId,
                                    currentUserId.toString(),
                                )
                            }

                            val existingById =
                                jellyseerrDao
                                    .getAllRequests(currentServerId, currentUserId.toString())
                                    .first()
                                    .associateBy { it.id }

                            jellyseerrDao.insertRequests(
                                baseRequests.map { request ->
                                    val base =
                                        request.toEntity(currentServerId, currentUserId.toString())
                                    val existing = existingById[base.id]
                                    if (existing == null) base
                                    else
                                        base.copy(
                                            title =
                                                base.title.takeUnless { it == "Unknown" }
                                                    ?: existing.title,
                                            mediaTitle = base.mediaTitle ?: existing.mediaTitle,
                                            mediaName = base.mediaName ?: existing.mediaName,
                                            posterPath = base.posterPath ?: existing.posterPath,
                                            mediaBackdropPath =
                                                base.mediaBackdropPath
                                                    ?: existing.mediaBackdropPath,
                                            mediaReleaseDate =
                                                base.mediaReleaseDate ?: existing.mediaReleaseDate,
                                            mediaFirstAirDate =
                                                base.mediaFirstAirDate
                                                    ?: existing.mediaFirstAirDate,
                                            requestedByName =
                                                base.requestedByName ?: existing.requestedByName,
                                            requestedByAvatar =
                                                base.requestedByAvatar
                                                    ?: existing.requestedByAvatar,
                                        )
                                }
                            )
                            repositoryScope.launch {
                                val enrichedEntities =
                                    baseRequests
                                        .map { request ->
                                            async {
                                                try {
                                                    val tmdbId =
                                                        request.media.tmdbId ?: return@async null
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
                                                        request
                                                            .copy(
                                                                media =
                                                                    request.media.copy(
                                                                        title = details.title,
                                                                        name = details.name,
                                                                        posterPath =
                                                                            details.posterPath,
                                                                        backdropPath =
                                                                            details.backdropPath,
                                                                        releaseDate =
                                                                            details.releaseDate,
                                                                        firstAirDate =
                                                                            details.firstAirDate,
                                                                    )
                                                            )
                                                            .toEntity(
                                                                currentServerId,
                                                                currentUserId.toString(),
                                                            )
                                                    } else null
                                                } catch (e: Exception) {
                                                    Timber.w(
                                                        e,
                                                        "Failed to enrich request ${request.id}",
                                                    )
                                                    null
                                                }
                                            }
                                        }
                                        .awaitAll()
                                        .filterNotNull()

                                if (enrichedEntities.isNotEmpty()) {
                                    try {
                                        jellyseerrDao.insertRequests(enrichedEntities)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to batch-cache enriched requests")
                                    }
                                }
                            }

                            return@withContext Result.success(baseRequests)
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

    override suspend fun approveRequest(
        requestId: Int,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
    ): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            try {
                val body =
                    if (serverId != null || profileId != null || rootFolder != null) {
                        com.makd.afinity.data.models.jellyseerr.ApproveRequestBody(
                            serverId = serverId,
                            profileId = profileId,
                            rootFolder = rootFolder,
                        )
                    } else null
                val response = apiService.get().approveRequest(requestId, body)

                if (response.isSuccessful && response.body() != null) {
                    var req = response.body()!!
                    val (currentServerId, currentUserId) =
                        activeContext ?: return@withContext Result.failure(Exception("No session"))
                    val existingEntity =
                        jellyseerrDao
                            .getAllRequests(currentServerId, currentUserId.toString())
                            .first()
                            .find { it.id == requestId }

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
                } else Result.failure(Exception("Failed to approve request"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateRequest(
        requestId: Int,
        mediaId: Int,
        mediaType: MediaType,
        seasons: List<Int>?,
        is4k: Boolean,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
    ): Result<JellyseerrRequest> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) =
                activeContext ?: return@withContext Result.failure(Exception("No active session"))

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

                val response = apiService.get().updateRequest(requestId, requestBody)

                if (response.isSuccessful && response.body() != null) {
                    var updatedRequest = response.body()!!
                    val existingEntity =
                        jellyseerrDao
                            .getAllRequests(currentServerId, currentUserId.toString())
                            .first()
                            .find { it.id == requestId }
                    updatedRequest =
                        updatedRequest.copy(
                            media =
                                updatedRequest.media.copy(
                                    title =
                                        if (!updatedRequest.media.title.isNullOrBlank())
                                            updatedRequest.media.title
                                        else existingEntity?.mediaTitle,
                                    name =
                                        if (!updatedRequest.media.name.isNullOrBlank())
                                            updatedRequest.media.name
                                        else existingEntity?.mediaName,
                                    posterPath =
                                        updatedRequest.media.posterPath
                                            ?: existingEntity?.posterPath,
                                    backdropPath =
                                        updatedRequest.media.backdropPath
                                            ?: existingEntity?.mediaBackdropPath,
                                    releaseDate =
                                        updatedRequest.media.releaseDate
                                            ?: existingEntity?.mediaReleaseDate,
                                    firstAirDate =
                                        updatedRequest.media.firstAirDate
                                            ?: existingEntity?.mediaFirstAirDate,
                                )
                        )

                    cacheRequest(updatedRequest)
                    _requestEvents.emit(RequestEvent(updatedRequest))
                    Result.success(updatedRequest)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = "Failed to update: ${response.code()} - $errorBody"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update Jellyseerr request")
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
                    Result.success(response.body()!!)
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
        return seerrResult("Failed to get TV details") { api ->
            api.getTvDetails(tvId)
        }
    }

    override suspend fun getRatings(mediaType: MediaType, tmdbId: Int): Result<RatingsCombined> {
        return withContext(Dispatchers.IO) {
            try {
                when (mediaType) {
                    MediaType.MOVIE -> {
                        val response = apiService.get().getMovieRatingsCombined(tmdbId)
                        if (response.isSuccessful && response.body() != null) {
                            Result.success(response.body()!!)
                        } else {
                            Result.failure(
                                Exception("Failed to get ratings: ${response.message()}")
                            )
                        }
                    }
                    MediaType.TV -> {
                        val response = apiService.get().getTvRatings(tmdbId)
                        if (response.isSuccessful && response.body() != null) {
                            Result.success(RatingsCombined(rt = response.body()!!, imdb = null))
                        } else {
                            Result.failure(
                                Exception("Failed to get ratings: ${response.message()}")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch ratings for $mediaType $tmdbId")
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

    override suspend fun getDiscoverSliders(): Result<List<DiscoverSlider>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }
                val response = apiService.get().getDiscoverSliders()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(
                        Exception("Failed to get discover sliders: ${response.message()}")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Jellyseerr discover sliders")
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverMovies(
        page: Int,
        sortBy: String,
        studio: Int?,
        limit: Int?,
        keywords: String?,
        watchRegion: String?,
        watchProviders: String?,
        filterOptions: DiscoverFilterOptions,
    ): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    apiService
                        .get()
                        .getDiscoverMovies(
                            page = page,
                            sortBy = sortBy,
                            studio = studio,
                            genre =
                                filterOptions.genreIds
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(","),
                            keywords =
                                keywords
                                    ?: filterOptions.keywordIds
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(","),
                            excludeKeywords =
                                filterOptions.excludeKeywordIds
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(","),
                            watchRegion = watchRegion ?: filterOptions.watchRegion,
                            watchProviders =
                                watchProviders
                                    ?: filterOptions.watchProviderIds
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(","),
                            primaryReleaseDateGte = filterOptions.releaseDateGte,
                            primaryReleaseDateLte = filterOptions.releaseDateLte,
                            withRuntimeGte = filterOptions.runtimeGte,
                            withRuntimeLte = filterOptions.runtimeLte,
                            voteAverageGte = filterOptions.voteAverageGte,
                            voteAverageLte = filterOptions.voteAverageLte,
                            voteCountGte = filterOptions.voteCountGte,
                            voteCountLte = filterOptions.voteCountLte,
                            certificationCountry =
                                if (filterOptions.certification.isNotEmpty()) "US" else null,
                            certification =
                                filterOptions.certification
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString("|"),
                        )
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
        keywords: String?,
        watchRegion: String?,
        watchProviders: String?,
        filterOptions: DiscoverFilterOptions,
    ): Result<JellyseerrSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    apiService
                        .get()
                        .getDiscoverTv(
                            page = page,
                            sortBy = sortBy,
                            network = network,
                            genre =
                                filterOptions.genreIds
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(","),
                            keywords =
                                keywords
                                    ?: filterOptions.keywordIds
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(","),
                            excludeKeywords =
                                filterOptions.excludeKeywordIds
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(","),
                            watchRegion = watchRegion ?: filterOptions.watchRegion,
                            watchProviders =
                                watchProviders
                                    ?: filterOptions.watchProviderIds
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(","),
                            firstAirDateGte = filterOptions.releaseDateGte,
                            firstAirDateLte = filterOptions.releaseDateLte,
                            withRuntimeGte = filterOptions.runtimeGte,
                            withRuntimeLte = filterOptions.runtimeLte,
                            voteAverageGte = filterOptions.voteAverageGte,
                            voteAverageLte = filterOptions.voteAverageLte,
                            voteCountGte = filterOptions.voteCountGte,
                            voteCountLte = filterOptions.voteCountLte,
                            status =
                                filterOptions.tvStatus
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(","),
                            certificationCountry =
                                if (filterOptions.certification.isNotEmpty()) "US" else null,
                            certification =
                                filterOptions.certification
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString("|"),
                        )
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

    override suspend fun getMovieGenres(): Result<List<Genre>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getMovieGenres()
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTvGenres(): Result<List<Genre>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getTvGenres()
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMoviesByGenre(
        genreId: Int,
        page: Int,
        sortBy: String,
        filterOptions: DiscoverFilterOptions,
    ): Result<JellyseerrSearchResult> =
        getDiscoverMovies(
            page = page,
            sortBy = sortBy,
            filterOptions = filterOptions.copy(genreIds = listOf(genreId)),
        )

    override suspend fun getTvByGenre(
        genreId: Int,
        page: Int,
        sortBy: String,
        filterOptions: DiscoverFilterOptions,
    ): Result<JellyseerrSearchResult> =
        getDiscoverTv(
            page = page,
            sortBy = sortBy,
            filterOptions = filterOptions.copy(genreIds = listOf(genreId)),
        )

    override suspend fun getWatchProviderRegions(): Result<List<WatchProviderRegion>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getWatchProviderRegions()
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMovieWatchProviders(
        watchRegion: String
    ): Result<List<WatchProviderDetails>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getMovieWatchProviders(watchRegion)
                if (response.isSuccessful && response.body() != null)
                    Result.success(
                        response.body()!!.sortedBy { it.displayPriority ?: Int.MAX_VALUE }
                    )
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTvWatchProviders(
        watchRegion: String
    ): Result<List<WatchProviderDetails>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().getTvWatchProviders(watchRegion)
                if (response.isSuccessful && response.body() != null)
                    Result.success(
                        response.body()!!.sortedBy { it.displayPriority ?: Int.MAX_VALUE }
                    )
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun searchKeywords(
        query: String,
        page: Int,
    ): Result<TmdbKeywordSearchResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.get().searchKeywords(query, page)
                if (response.isSuccessful && response.body() != null)
                    Result.success(response.body()!!)
                else Result.failure(Exception("Failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDiscoverFilterState(
        contextKey: String
    ): Pair<String, DiscoverFilterOptions>? {
        return withContext(Dispatchers.IO) {
            val (serverId, userId) = activeContext ?: return@withContext null
            try {
                val entity =
                    jellyseerrDao.getDiscoverFilterState(serverId, userId.toString(), contextKey)
                        ?: return@withContext null
                val options =
                    kotlinx.serialization.json.Json.decodeFromString<DiscoverFilterOptions>(
                        entity.filterOptionsJson
                    )
                entity.sortBy to options
            } catch (e: Exception) {
                Timber.w(e, "Failed to load discover filter state for $contextKey")
                null
            }
        }
    }

    override suspend fun saveDiscoverFilterState(
        contextKey: String,
        sortBy: String,
        filterOptions: DiscoverFilterOptions,
    ) {
        withContext(Dispatchers.IO) {
            val (serverId, userId) = activeContext ?: return@withContext
            try {
                jellyseerrDao.saveDiscoverFilterState(
                    JellyseerrDiscoverFilterEntity(
                        jellyfinServerId = serverId,
                        jellyfinUserId = userId.toString(),
                        filterContextKey = contextKey,
                        sortBy = sortBy,
                        filterOptionsJson =
                            kotlinx.serialization.json.Json.encodeToString(filterOptions),
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to save discover filter state for $contextKey")
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
        return seerrResult("Failed to get service details") { api ->
            when (mediaType) {
                MediaType.MOVIE -> api.getRadarrDetails(serviceId)
                MediaType.TV -> api.getSonarrDetails(serviceId)
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
