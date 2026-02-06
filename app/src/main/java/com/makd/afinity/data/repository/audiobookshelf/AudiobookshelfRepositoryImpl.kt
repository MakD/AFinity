package com.makd.afinity.data.repository.audiobookshelf

import android.os.Build
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.AudiobookshelfConfigEntity
import com.makd.afinity.data.database.entities.AudiobookshelfItemEntity
import com.makd.afinity.data.database.entities.AudiobookshelfLibraryEntity
import com.makd.afinity.data.database.entities.AudiobookshelfProgressEntity
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfUser
import com.makd.afinity.data.models.audiobookshelf.DeviceInfo
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.LoginRequest
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.MediaProgressSyncData
import com.makd.afinity.data.models.audiobookshelf.PersonalizedView
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.audiobookshelf.PlaybackSessionRequest
import com.makd.afinity.data.models.audiobookshelf.ProgressUpdateRequest
import com.makd.afinity.data.models.audiobookshelf.SearchResponse
import com.makd.afinity.data.network.AudiobookshelfApiService
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.ItemWithProgress
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfRepositoryImpl @Inject constructor(
    private val apiService: Lazy<AudiobookshelfApiService>,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val database: AfinityDatabase,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor
) : AudiobookshelfRepository {

    private val audiobookshelfDao = database.audiobookshelfDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    override val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _currentConfig = MutableStateFlow<AudiobookshelfConfig?>(null)
    override val currentConfig: StateFlow<AudiobookshelfConfig?> = _currentConfig.asStateFlow()

    private var activeContext: Pair<String, UUID>? = null

    private var pendingServerUrl: String? = null

    companion object {
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    }

    override suspend fun setActiveJellyfinSession(serverId: String, userId: UUID) {
        Timber.d("Switching Audiobookshelf context to Server: $serverId, User: $userId")
        _isAuthenticated.value = false
        _currentConfig.value = null
        activeContext = serverId to userId
        _currentSessionId.value = "${serverId}_$userId"

        val hasAuth = securePreferencesRepository.switchAudiobookshelfContext(serverId, userId)
        val config = audiobookshelfDao.getConfig(serverId, userId.toString())

        if (hasAuth && config?.isLoggedIn == true) {
            _isAuthenticated.value = true
            _currentConfig.value = AudiobookshelfConfig(
                serverUrl = config.serverUrl,
                absUserId = config.absUserId,
                username = config.username
            )
        }

        Timber.d("Audiobookshelf Context Switched. Authenticated: ${_isAuthenticated.value}")
    }

    override fun clearActiveSession() {
        activeContext = null
        _currentSessionId.value = null
        securePreferencesRepository.clearActiveAudiobookshelfCache()
        _isAuthenticated.value = false
        _currentConfig.value = null
        pendingServerUrl = null
        Timber.d("Audiobookshelf active session cleared")
    }

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AudiobookshelfUser> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active Jellyfin session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                pendingServerUrl = serverUrl
                securePreferencesRepository.saveAudiobookshelfAuthForUser(
                    jellyfinServerId = currentServerId,
                    jellyfinUserId = currentUserId,
                    serverUrl = serverUrl,
                    accessToken = "",
                    absUserId = "",
                    username = username
                )

                val loginRequest = LoginRequest(username, password)
                val response = apiService.get().login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val user = loginResponse.user
                    val token = user.token
                        ?: return@withContext Result.failure(Exception("No token received"))

                    securePreferencesRepository.saveAudiobookshelfAuthForUser(
                        jellyfinServerId = currentServerId,
                        jellyfinUserId = currentUserId,
                        serverUrl = serverUrl,
                        accessToken = token,
                        absUserId = user.id,
                        username = user.username
                    )

                    audiobookshelfDao.insertConfig(
                        AudiobookshelfConfigEntity(
                            jellyfinServerId = currentServerId,
                            jellyfinUserId = currentUserId.toString(),
                            serverUrl = serverUrl,
                            absUserId = user.id,
                            username = user.username,
                            isLoggedIn = true,
                            lastSync = System.currentTimeMillis()
                        )
                    )

                    _isAuthenticated.value = true
                    _currentConfig.value = AudiobookshelfConfig(
                        serverUrl = serverUrl,
                        absUserId = user.id,
                        username = user.username
                    )

                    user.mediaProgress?.let { progressList ->
                        progressList.forEach { progress ->
                            cacheProgress(progress)
                        }
                    }

                    Timber.d("Audiobookshelf login successful for user: ${user.username}")
                    Result.success(user)
                } else {
                    val errorMsg = "Login failed: ${response.code()} - ${response.message()}"
                    Timber.e(errorMsg)
                    pendingServerUrl = null
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Timber.e(e, "Audiobookshelf login failed")
                pendingServerUrl = null
                Result.failure(e)
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                securePreferencesRepository.clearAudiobookshelfAuthForUser(
                    currentServerId,
                    currentUserId
                )

                audiobookshelfDao.deleteConfig(currentServerId, currentUserId.toString())
                audiobookshelfDao.deleteAllLibraries(currentServerId, currentUserId.toString())
                audiobookshelfDao.deleteAllItems(currentServerId, currentUserId.toString())
                audiobookshelfDao.deleteAllProgress(currentServerId, currentUserId.toString())

                _isAuthenticated.value = false
                _currentConfig.value = null
                pendingServerUrl = null

                Timber.d("Audiobookshelf logout successful")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Audiobookshelf logout failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun validateToken(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().authorize()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(true)
                } else {
                    _isAuthenticated.value = false
                    Result.success(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Token validation failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun setServerUrl(url: String) {
        pendingServerUrl = url
    }

    override suspend fun getServerUrl(): String? {
        return pendingServerUrl ?: _currentConfig.value?.serverUrl
    }

    override suspend fun hasValidConfiguration(): Boolean {
        return _isAuthenticated.value && _currentConfig.value != null
    }

    override fun getLibrariesFlow(): Flow<List<Library>> {
        val (serverId, userId) = activeContext ?: return flowOf(emptyList())
        return audiobookshelfDao.getLibrariesFlow(serverId, userId.toString()).map { entities ->
            entities.map { it.toLibrary() }
        }
    }

    override suspend fun refreshLibraries(): Result<List<Library>> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getLibraries()

                if (response.isSuccessful && response.body() != null) {
                    val libraries = response.body()!!.libraries

                    val entities = libraries.mapIndexed { index, library ->
                        AudiobookshelfLibraryEntity(
                            id = library.id,
                            jellyfinServerId = currentServerId,
                            jellyfinUserId = currentUserId.toString(),
                            name = library.name,
                            mediaType = library.mediaType,
                            icon = library.icon,
                            displayOrder = library.displayOrder ?: index,
                            totalItems = library.stats?.totalItems ?: 0,
                            totalDuration = library.stats?.totalDuration,
                            lastUpdated = library.lastUpdate ?: System.currentTimeMillis(),
                            cachedAt = System.currentTimeMillis()
                        )
                    }

                    audiobookshelfDao.deleteAllLibraries(currentServerId, currentUserId.toString())
                    audiobookshelfDao.insertLibraries(entities)

                    Result.success(libraries)
                } else {
                    Result.failure(Exception("Failed to fetch libraries: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh libraries")
                Result.failure(e)
            }
        }
    }

    override suspend fun getLibrary(libraryId: String): Result<Library> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getLibrary(libraryId)

                if (response.isSuccessful && response.body()?.library != null) {
                    Result.success(response.body()!!.library!!)
                } else {
                    Result.failure(Exception("Failed to fetch library: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library")
                Result.failure(e)
            }
        }
    }

    override fun getLibraryItemsFlow(libraryId: String): Flow<List<LibraryItem>> {
        val (serverId, userId) = activeContext ?: return flowOf(emptyList())
        return audiobookshelfDao.getItemsFlow(serverId, userId.toString(), libraryId)
            .map { entities ->
                entities.map { it.toLibraryItem() }
            }
    }

    override suspend fun refreshLibraryItems(
        libraryId: String,
        limit: Int,
        page: Int
    ): Result<List<LibraryItem>> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val allItems = mutableListOf<LibraryItem>()
                var currentPage = 0
                var totalFetched = 0
                var total = Int.MAX_VALUE
                var isFirstPage = true

                while (totalFetched < total) {
                    val response = apiService.get().getLibraryItems(
                        id = libraryId,
                        limit = limit,
                        page = currentPage,
                        include = "progress",
                        sort =  "media.metadata.title"
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        total = body.total
                        val items = body.results

                        val entities = items.map { item ->
                            item.toEntity(currentServerId, currentUserId.toString())
                        }

                        if (isFirstPage) {
                            audiobookshelfDao.deleteItemsByLibrary(
                                currentServerId,
                                currentUserId.toString(),
                                libraryId
                            )
                            isFirstPage = false
                        }
                        audiobookshelfDao.insertItems(entities)
                        items.forEach { item ->
                            item.userMediaProgress?.let { progress ->
                                cacheProgress(progress)
                            }
                        }

                        allItems.addAll(items)
                        totalFetched += items.size
                        currentPage++

                        Timber.d("Fetched items page $currentPage: ${items.size} items, total: $total")

                        if (items.isEmpty()) break
                    } else {
                        return@withContext Result.failure(Exception("Failed to fetch items: ${response.message()}"))
                    }
                }

                Timber.d("Fetched all ${allItems.size} items for library $libraryId")
                Result.success(allItems)
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh library items")
                Result.failure(e)
            }
        }
    }

    override suspend fun getItemDetails(itemId: String): Result<LibraryItem> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    val cached =
                        audiobookshelfDao.getItem(itemId, currentServerId, currentUserId.toString())
                    if (cached != null) {
                        return@withContext Result.success(cached.toLibraryItem())
                    }
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getItem(itemId)

                if (response.isSuccessful && response.body() != null) {
                    val itemResponse = response.body()!!
                    val item = LibraryItem(
                        id = itemResponse.id ?: itemId,
                        ino = itemResponse.ino,
                        libraryId = itemResponse.libraryId ?: "",
                        mediaType = itemResponse.mediaType ?: "book",
                        media = itemResponse.media!!,
                        addedAt = itemResponse.addedAt,
                        updatedAt = itemResponse.updatedAt,
                        userMediaProgress = itemResponse.userMediaProgress
                    )
                    audiobookshelfDao.insertItem(
                        item.toEntity(
                            currentServerId,
                            currentUserId.toString()
                        )
                    )

                    itemResponse.userMediaProgress?.let { progress ->
                        cacheProgress(progress)
                    }

                    Result.success(item)
                } else {
                    Result.failure(Exception("Failed to fetch item: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get item details")
                Result.failure(e)
            }
        }
    }

    override suspend fun searchLibrary(libraryId: String, query: String): Result<SearchResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().search(libraryId, query)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Search failed: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search library")
                Result.failure(e)
            }
        }
    }

    override suspend fun getSeries(
        libraryId: String,
        limit: Int,
        page: Int
    ): Result<List<AudiobookshelfSeries>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val allSeries = mutableListOf<AudiobookshelfSeries>()
                var currentPage = 0
                var totalFetched = 0
                var total = Int.MAX_VALUE

                while (totalFetched < total) {
                    val response = apiService.get().getSeries(
                        id = libraryId,
                        limit = limit,
                        page = currentPage
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        total = body.total
                        allSeries.addAll(body.results)
                        totalFetched += body.results.size
                        currentPage++

                        Timber.d("Fetched series page $currentPage: ${body.results.size} items, total: $total")

                        if (body.results.isEmpty()) break
                    } else {
                        return@withContext Result.failure(Exception("Failed to fetch series: ${response.message()}"))
                    }
                }

                Timber.d("Fetched all ${allSeries.size} series for library $libraryId")
                Result.success(allSeries)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get series for library $libraryId")
                Result.failure(e)
            }
        }
    }

    override suspend fun getPersonalized(libraryId: String): Result<List<PersonalizedView>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getPersonalized(libraryId)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to fetch personalized: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get personalized")
                Result.failure(e)
            }
        }
    }

    override fun getInProgressItemsFlow(): Flow<List<ItemWithProgress>> {
        val (serverId, userId) = activeContext ?: return flowOf(emptyList())

        val progressFlow = audiobookshelfDao.getInProgressFlow(serverId, userId.toString())
        val itemsFlow = audiobookshelfDao.getItemsFlow(serverId, userId.toString(), "")
        return progressFlow.map { progressList ->
            progressList.mapNotNull { progress ->
                val item =
                    audiobookshelfDao.getItem(progress.libraryItemId, serverId, userId.toString())
                item?.let {
                    ItemWithProgress(
                        item = it.toLibraryItem(),
                        progress = progress.toMediaProgress()
                    )
                }
            }
        }
    }

    override suspend fun refreshProgress(): Result<List<MediaProgress>> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val response = apiService.get().getItemsInProgress()

                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.libraryItems
                    val progressList = items.mapNotNull { it.userMediaProgress }
                    items.forEach { item ->
                        audiobookshelfDao.insertItem(
                            item.toEntity(
                                currentServerId,
                                currentUserId.toString()
                            )
                        )
                        item.userMediaProgress?.let { progress ->
                            cacheProgress(progress)
                        }
                    }

                    Result.success(progressList)
                } else {
                    Result.failure(Exception("Failed to fetch progress: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh progress")
                Result.failure(e)
            }
        }
    }

    override suspend fun updateProgress(
        itemId: String,
        episodeId: String?,
        currentTime: Double,
        duration: Double,
        isFinished: Boolean
    ): Result<MediaProgress> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            try {
                val progress = if (duration > 0) currentTime / duration else 0.0
                val request = ProgressUpdateRequest(
                    currentTime = currentTime,
                    duration = duration,
                    progress = progress,
                    isFinished = isFinished
                )

                if (networkConnectivityMonitor.isCurrentlyConnected()) {
                    val response = if (episodeId != null) {
                        apiService.get().updateEpisodeProgress(itemId, episodeId, request)
                    } else {
                        apiService.get().updateProgress(itemId, request)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val mediaProgress = response.body()!!
                        cacheProgress(mediaProgress)
                        return@withContext Result.success(mediaProgress)
                    }
                }
                val localProgress = AudiobookshelfProgressEntity(
                    id = "${itemId}_${episodeId ?: ""}",
                    jellyfinServerId = currentServerId,
                    jellyfinUserId = currentUserId.toString(),
                    libraryItemId = itemId,
                    episodeId = episodeId,
                    currentTime = currentTime,
                    duration = duration,
                    progress = progress,
                    isFinished = isFinished,
                    lastUpdate = System.currentTimeMillis(),
                    startedAt = System.currentTimeMillis(),
                    finishedAt = if (isFinished) System.currentTimeMillis() else null,
                    pendingSync = true
                )
                audiobookshelfDao.insertProgress(localProgress)

                Result.success(localProgress.toMediaProgress())
            } catch (e: Exception) {
                Timber.e(e, "Failed to update progress")
                Result.failure(e)
            }
        }
    }

    override fun getProgressForItemFlow(itemId: String): Flow<MediaProgress?> {
        val (serverId, userId) = activeContext ?: return flowOf(null)
        return audiobookshelfDao.getProgressForItemFlow(itemId, serverId, userId.toString())
            .map { it?.toMediaProgress() }
    }

    override suspend fun startPlaybackSession(
        itemId: String,
        episodeId: String?
    ): Result<PlaybackSession> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val deviceInfo = DeviceInfo(
                    deviceId = getDeviceId(),
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    sdkVersion = Build.VERSION.SDK_INT,
                    clientName = "AFinity",
                    clientVersion = BuildConfig.VERSION_NAME
                )

                val request = PlaybackSessionRequest(
                    deviceInfo = deviceInfo,
                    forceDirectPlay = true,
                    mediaPlayer = "ExoPlayer",
                    supportedMimeTypes = listOf(
                        "audio/mpeg",
                        "audio/mp4",
                        "audio/ogg",
                        "audio/flac",
                        "audio/wav"
                    )
                )

                val response = if (episodeId != null) {
                    apiService.get().startEpisodePlaybackSession(itemId, episodeId, request)
                } else {
                    apiService.get().startPlaybackSession(itemId, request)
                }

                if (response.isSuccessful && response.body() != null) {
                    val session = response.body()!!
                    Timber.d("Playback session received: id=${session.id}, mediaType=${session.mediaType}")
                    Timber.d("Session displayTitle=${session.displayTitle}, displayAuthor=${session.displayAuthor}")
                    Timber.d("Session audioTracks=${session.audioTracks?.size ?: 0}, chapters=${session.chapters?.size ?: 0}")
                    Timber.d("Session episodeId=${session.episodeId}, duration=${session.duration}")
                    Result.success(session)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Failed to start session: ${response.code()} - ${response.message()}, body=$errorBody")
                    Result.failure(Exception("Failed to start session: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start playback session")
                Result.failure(e)
            }
        }
    }

    override suspend fun syncPlaybackSession(
        sessionId: String,
        timeListened: Double,
        currentTime: Double,
        duration: Double
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val syncData = MediaProgressSyncData(
                    currentTime = currentTime,
                    timeListened = timeListened,
                    duration = duration,
                    progress = if (duration > 0) currentTime / duration else 0.0
                )

                val response = apiService.get().syncPlaybackSession(sessionId, syncData)

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to sync session: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playback session")
                Result.failure(e)
            }
        }
    }

    override suspend fun closePlaybackSession(
        sessionId: String,
        currentTime: Double,
        timeListened: Double,
        duration: Double
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                    return@withContext Result.failure(Exception("No network connection"))
                }

                val syncData = MediaProgressSyncData(
                    currentTime = currentTime,
                    timeListened = timeListened,
                    duration = duration,
                    progress = if (duration > 0) currentTime / duration else 0.0
                )

                val response = apiService.get().closePlaybackSession(sessionId, syncData)

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to close session: ${response.message()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to close playback session")
                Result.failure(e)
            }
        }
    }

    override suspend fun syncPendingProgress(): Result<Int> {
        return withContext(Dispatchers.IO) {
            val (currentServerId, currentUserId) = activeContext
                ?: return@withContext Result.failure(Exception("No active session"))

            if (!networkConnectivityMonitor.isCurrentlyConnected()) {
                return@withContext Result.failure(Exception("No network connection"))
            }

            try {
                val pendingProgress = audiobookshelfDao.getPendingSyncProgress(
                    currentServerId,
                    currentUserId.toString()
                )

                var syncedCount = 0

                pendingProgress.forEach { progress ->
                    try {
                        val request = ProgressUpdateRequest(
                            currentTime = progress.currentTime,
                            duration = progress.duration,
                            progress = progress.progress,
                            isFinished = progress.isFinished
                        )

                        val response = if (progress.episodeId != null) {
                            apiService.get().updateEpisodeProgress(
                                progress.libraryItemId,
                                progress.episodeId,
                                request
                            )
                        } else {
                            apiService.get().updateProgress(progress.libraryItemId, request)
                        }

                        if (response.isSuccessful) {
                            audiobookshelfDao.markSynced(
                                progress.id,
                                currentServerId,
                                currentUserId.toString()
                            )
                            syncedCount++
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to sync progress for item ${progress.libraryItemId}")
                    }
                }

                Result.success(syncedCount)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync pending progress")
                Result.failure(e)
            }
        }
    }

    private suspend fun cacheProgress(progress: MediaProgress) {
        val (currentServerId, currentUserId) = activeContext ?: return

        val entity = AudiobookshelfProgressEntity(
            id = progress.id,
            jellyfinServerId = currentServerId,
            jellyfinUserId = currentUserId.toString(),
            libraryItemId = progress.libraryItemId,
            episodeId = progress.episodeId,
            currentTime = progress.currentTime,
            duration = progress.duration,
            progress = progress.progress,
            isFinished = progress.isFinished,
            lastUpdate = progress.lastUpdate,
            startedAt = progress.startedAt,
            finishedAt = progress.finishedAt,
            pendingSync = false
        )

        audiobookshelfDao.insertProgress(entity)
    }

    private fun getDeviceId(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".replace(" ", "_")
    }

    private fun AudiobookshelfLibraryEntity.toLibrary(): Library {
        return Library(
            id = id,
            name = name,
            mediaType = mediaType,
            icon = icon,
            displayOrder = displayOrder
        )
    }

    private fun LibraryItem.toEntity(serverId: String, userId: String): AudiobookshelfItemEntity {
        return AudiobookshelfItemEntity(
            id = id,
            jellyfinServerId = serverId,
            jellyfinUserId = userId,
            libraryId = libraryId,
            title = media.metadata.title ?: "Unknown",
            authorName = media.metadata.authorName,
            narratorName = media.metadata.narratorName,
            seriesName = media.metadata.seriesName,
            seriesSequence = media.metadata.series?.firstOrNull()?.sequence,
            mediaType = mediaType,
            duration = media.duration,
            coverUrl = media.coverPath,
            description = media.metadata.description,
            publishedYear = media.metadata.publishedYear,
            genres = media.metadata.genres?.let { json.encodeToString(it) },
            numTracks = media.numTracks,
            numChapters = media.numChapters,
            addedAt = addedAt,
            updatedAt = updatedAt,
            cachedAt = System.currentTimeMillis()
        )
    }

    private fun AudiobookshelfItemEntity.toLibraryItem(): LibraryItem {
        val genres = genres?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        }

        return LibraryItem(
            id = id,
            libraryId = libraryId,
            mediaType = mediaType,
            media = com.makd.afinity.data.models.audiobookshelf.Media(
                metadata = com.makd.afinity.data.models.audiobookshelf.MediaMetadata(
                    title = title,
                    authorName = authorName,
                    narratorName = narratorName,
                    seriesName = seriesName,
                    description = description,
                    publishedYear = publishedYear,
                    genres = genres
                ),
                duration = duration,
                coverPath = coverUrl,
                numTracks = numTracks,
                numChapters = numChapters
            ),
            addedAt = addedAt,
            updatedAt = updatedAt
        )
    }

    private fun AudiobookshelfProgressEntity.toMediaProgress(): MediaProgress {
        return MediaProgress(
            id = id,
            libraryItemId = libraryItemId,
            episodeId = episodeId,
            duration = duration,
            progress = progress,
            currentTime = currentTime,
            isFinished = isFinished,
            lastUpdate = lastUpdate,
            startedAt = startedAt,
            finishedAt = finishedAt
        )
    }
}
