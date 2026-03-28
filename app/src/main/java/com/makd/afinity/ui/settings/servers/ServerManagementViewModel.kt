package com.makd.afinity.ui.settings.servers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.data.database.entities.AudiobookshelfAddressEntity
import com.makd.afinity.data.database.entities.JellyseerrAddressEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.ListeningStats
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.util.isLocalAddress
import com.makd.afinity.util.isTailscaleAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider

data class ServerManagementState(
    val servers: List<ServerWithUserCount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverToDelete: ServerWithUserCount? = null,
    val detailServer: ServerWithUserCount? = null,
    val detailStats: ServerDetailStats? = null,
    val statsLoading: Boolean = false,
)

enum class AddressType {
    LOCAL,
    TAILSCALE,
    REMOTE,
}

data class ServiceStatus(
    val jellyseerrConfigured: Boolean = false,
    val audiobookshelfConfigured: Boolean = false,
    val tmdbConfigured: Boolean = false,
    val mdbListConfigured: Boolean = false,
)

data class UserServiceInfo(
    val userId: String,
    val userName: String,
    val serviceStatus: ServiceStatus = ServiceStatus(),
)

data class JellyfinStats(
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val episodeCount: Int = 0,
    val boxsetCount: Int = 0,
)

data class JellyseerrStats(
    val user: JellyseerrUser? = null,
    val totalRequests: Int = 0,
    val pendingRequests: Int = 0,
    val approvedRequests: Int = 0,
    val availableRequests: Int = 0,
)

data class AudiobookshelfStats(
    val libraries: List<Library> = emptyList(),
    val inProgressCount: Int = 0,
    val totalItems: Int = 0,
    val totalDurationHours: Double = 0.0,
    val totalListeningTimeSeconds: Double = 0.0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val finishedCount: Int = 0,
    val activeDays: Int = 0,
    val todaySeconds: Double = 0.0,
    val weekSeconds: Double = 0.0,
    val monthSeconds: Double = 0.0,
)

data class ServerDetailStats(
    val jellyfinStats: JellyfinStats? = null,
    val jellyseerrStats: JellyseerrStats? = null,
    val audiobookshelfStats: AudiobookshelfStats? = null,
)

data class ServerWithUserCount(
    val server: Server,
    val addresses: List<ServerAddress> = emptyList(),
    val userCount: Int,
    val currentUserServiceStatus: ServiceStatus = ServiceStatus(),
    val userServices: List<UserServiceInfo> = emptyList(),
    val addressType: AddressType = AddressType.REMOTE,
    val currentConnectionUrl: String = "",
    val currentConnectionType: AddressType = AddressType.REMOTE,
    val isActiveServer: Boolean = false,
    val jellyseerrAddresses: List<JellyseerrAddressEntity> = emptyList(),
    val audiobookshelfAddresses: List<AudiobookshelfAddressEntity> = emptyList(),
    val jellyseerrConnectionUrl: String? = null,
    val jellyseerrConnectionType: AddressType? = null,
    val audiobookshelfConnectionUrl: String? = null,
    val audiobookshelfConnectionType: AddressType? = null,
)

@HiltViewModel
class ServerManagementViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val jellyseerrDao: JellyseerrDao,
    private val audiobookshelfDao: AudiobookshelfDao,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val serverRepository: ServerRepository,
    private val jellyseerrRepositoryProvider: Provider<JellyseerrRepository>,
    private val audiobookshelfRepositoryProvider: Provider<AudiobookshelfRepository>,
    private val jellyfinRepositoryProvider: Provider<JellyfinRepository>,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerManagementState())
    val state: StateFlow<ServerManagementState> = _state.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                loadServersInternal()
            } catch (e: Exception) {
                Timber.e(e, "Error loading servers")
                _state.value =
                    _state.value.copy(
                        error = e.message ?: context.getString(R.string.error_load_servers_failed),
                        isLoading = false,
                    )
            }
        }
    }

    private fun classifyAddress(address: String): AddressType {
        return when {
            isLocalAddress(address) -> AddressType.LOCAL
            isTailscaleAddress(address) -> AddressType.TAILSCALE
            else -> AddressType.REMOTE
        }
    }

    fun showDeleteConfirmation(serverWithUserCount: ServerWithUserCount) {
        _state.value = _state.value.copy(serverToDelete = serverWithUserCount)
    }

    fun hideDeleteConfirmation() {
        _state.value = _state.value.copy(serverToDelete = null)
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                _state.value =
                    _state.value.copy(isLoading = true, error = null, serverToDelete = null)

                val currentSession = sessionManager.currentSession.value
                if (currentSession?.serverId == serverId) {
                    _state.value =
                        _state.value.copy(
                            error = context.getString(R.string.error_delete_active_server),
                            isLoading = false,
                        )
                    return@launch
                }

                databaseRepository.deleteServer(serverId)
                databaseRepository.clearServerData(serverId)
                loadServers()

                Timber.d("Server deleted successfully: $serverId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting server")
                _state.value =
                    _state.value.copy(
                        error = e.message ?: context.getString(R.string.error_delete_server_failed),
                        isLoading = false,
                    )
            }
        }
    }

    fun deleteAddress(addressId: UUID) {
        viewModelScope.launch {
            try {
                databaseRepository.deleteServerAddress(addressId)
                reloadAndRefreshDetail()
                Timber.d("Server address deleted: $addressId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting server address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun setPrimaryAddress(serverId: String, newPrimaryAddress: String) {
        viewModelScope.launch {
            try {
                val server = databaseRepository.getServer(serverId) ?: return@launch
                val oldPrimary = server.address
                if (oldPrimary != newPrimaryAddress) {
                    val oldExists = databaseRepository.getServerAddressByUrl(serverId, oldPrimary)
                    if (oldExists == null) {
                        databaseRepository.insertServerAddress(
                            ServerAddress(
                                id = UUID.randomUUID(),
                                serverId = serverId,
                                address = oldPrimary,
                            )
                        )
                    }
                }
                databaseRepository.updateServer(server.copy(address = newPrimaryAddress))
                reloadAndRefreshDetail()
                Timber.d("Primary address updated for server $serverId: $newPrimaryAddress")
            } catch (e: Exception) {
                Timber.e(e, "Error setting primary address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun showServerDetail(serverWithUserCount: ServerWithUserCount) {
        _state.value =
            _state.value.copy(
                detailServer = serverWithUserCount,
                detailStats = ServerDetailStats(),
                statsLoading = serverWithUserCount.isActiveServer,
            )
        if (serverWithUserCount.isActiveServer) {
            loadDetailStats(serverWithUserCount)
        }
    }

    fun hideServerDetail() {
        _state.value = _state.value.copy(detailServer = null, detailStats = null)
    }

    private fun loadDetailStats(serverWithCount: ServerWithUserCount) {
        val status = serverWithCount.currentUserServiceStatus
        val serverId = serverWithCount.server.id

        viewModelScope.launch {
            try {
                val jellyfinRepository = jellyfinRepositoryProvider.get()
                jellyfinRepository.getLibraryStatsFlow(serverId).collect { freshJellyfinStats ->
                    val currentStats = _state.value.detailStats ?: ServerDetailStats()

                    _state.value =
                        _state.value.copy(
                            detailStats = currentStats.copy(jellyfinStats = freshJellyfinStats),
                            statsLoading = false,
                        )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Jellyfin stats")
                _state.value = _state.value.copy(statsLoading = false)
            }
        }

        if (status.jellyseerrConfigured) {
            viewModelScope.launch {
                try {
                    val stats = loadJellyseerrStats()
                    val currentStats = _state.value.detailStats ?: ServerDetailStats()
                    _state.value =
                        _state.value.copy(detailStats = currentStats.copy(jellyseerrStats = stats))
                } catch (e: Exception) {
                    Timber.e(e, "Error loading Jellyseerr stats")
                }
            }
        }

        if (status.audiobookshelfConfigured) {
            viewModelScope.launch {
                try {
                    val stats = loadAudiobookshelfStats()
                    val currentStats = _state.value.detailStats ?: ServerDetailStats()
                    _state.value =
                        _state.value.copy(
                            detailStats = currentStats.copy(audiobookshelfStats = stats)
                        )
                } catch (e: Exception) {
                    Timber.e(e, "Error loading Audiobookshelf stats")
                }
            }
        }
    }

    private suspend fun loadJellyseerrStats(): JellyseerrStats {
        val jellyseerrRepository = jellyseerrRepositoryProvider.get()
        return try {
            val user = jellyseerrRepository.getCurrentUser().getOrNull()
            val allRequests = jellyseerrRepository.getRequests(take = 100).getOrDefault(emptyList())
            val pending = allRequests.count { it.status == 1 }
            val approved = allRequests.count { it.status == 2 }
            val available = allRequests.count { it.status == 4 || it.status == 5 }
            JellyseerrStats(
                user = user,
                totalRequests = allRequests.size,
                pendingRequests = pending,
                approvedRequests = approved,
                availableRequests = available,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Jellyseerr stats")
            JellyseerrStats()
        }
    }

    private suspend fun loadAudiobookshelfStats(): AudiobookshelfStats {
        val absRepository = audiobookshelfRepositoryProvider.get()
        return try {
            val libraryList = absRepository.refreshLibraries().getOrDefault(emptyList())
            val libraries =
                libraryList.map { lib ->
                    val stats = absRepository.getLibraryStats(lib.id).getOrNull()
                    if (stats != null) lib.copy(stats = stats) else lib
                }
            val inProgress = absRepository.getInProgressItemsFlow().first()
            val totalItems = libraries.sumOf { it.stats?.totalItems ?: 0 }
            val totalDurationSec = libraries.sumOf { it.stats?.totalDuration ?: 0.0 }
            val allProgress = absRepository.getAllProgressFlow().first()
            val finishedCount = allProgress.values.count { it.isFinished }
            val listeningStats = absRepository.getListeningStats().getOrNull()
            val dailyMap = listeningStats?.let { extractDailySeconds(it) } ?: emptyMap()

            AudiobookshelfStats(
                libraries = libraries,
                inProgressCount = inProgress.size,
                totalItems = totalItems,
                totalDurationHours = totalDurationSec / 3600.0,
                totalListeningTimeSeconds = listeningStats?.totalTime ?: 0.0,
                currentStreak = calculateCurrentStreak(dailyMap),
                longestStreak = calculateLongestStreak(dailyMap),
                finishedCount = finishedCount,
                activeDays = dailyMap.count { it.value > 0 },
                todaySeconds = todaySeconds(dailyMap),
                weekSeconds = periodSeconds(dailyMap, 7),
                monthSeconds = periodSeconds(dailyMap, 30),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Audiobookshelf stats")
            AudiobookshelfStats()
        }
    }

    private fun extractDailySeconds(stats: ListeningStats): Map<String, Double> {
        val rawMap = stats.dayListeningMap ?: stats.days ?: return emptyMap()
        return rawMap.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.doubleOrNull ?: 0.0
                is JsonObject -> {
                    val time = value["timeListening"]
                    (time as? JsonPrimitive)?.doubleOrNull
                        ?: (value["totalTime"] as? JsonPrimitive)?.doubleOrNull
                        ?: 0.0
                }
                else -> 0.0
            }
        }
    }

    private fun todaySeconds(dailyMap: Map<String, Double>): Double {
        val today = java.time.LocalDate.now().toString()
        return dailyMap[today] ?: 0.0
    }

    private fun periodSeconds(dailyMap: Map<String, Double>, days: Int): Double {
        val now = java.time.LocalDate.now()
        return (0 until days).sumOf { i -> dailyMap[now.minusDays(i.toLong()).toString()] ?: 0.0 }
    }

    private fun calculateCurrentStreak(dailyMap: Map<String, Double>): Int {
        val now = java.time.LocalDate.now()
        val startOffset = if ((dailyMap[now.toString()] ?: 0.0) > 0) 0 else 1
        var streak = 0
        for (i in startOffset until 365) {
            if ((dailyMap[now.minusDays(i.toLong()).toString()] ?: 0.0) > 0) {
                streak++
            } else break
        }
        return streak
    }

    private fun calculateLongestStreak(dailyMap: Map<String, Double>): Int {
        val sorted =
            dailyMap.keys
                .filter { (dailyMap[it] ?: 0.0) > 0 }
                .mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                .sorted()
        if (sorted.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (i in 1 until sorted.size) {
            if (sorted[i].toEpochDay() - sorted[i - 1].toEpochDay() == 1L) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }
        return longest
    }

    fun deleteJellyseerrAddress(addressId: UUID) {
        viewModelScope.launch {
            try {
                jellyseerrDao.deleteAddress(addressId)
                reloadAndRefreshDetail()
                Timber.d("Jellyseerr address deleted: $addressId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting Jellyseerr address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteAudiobookshelfAddress(addressId: UUID) {
        viewModelScope.launch {
            try {
                audiobookshelfDao.deleteAddress(addressId)
                reloadAndRefreshDetail()
                Timber.d("Audiobookshelf address deleted: $addressId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting Audiobookshelf address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addJellyseerrAddress(serverId: String, address: String) {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.currentSession.value ?: return@launch
                if (currentSession.serverId != serverId) return@launch
                val cleanAddress = sanitizeUrl(address)
                val userId = currentSession.userId.toString()
                val existing = jellyseerrDao.getAddressByUrl(serverId, userId, address)
                if (existing != null) {
                    _state.value = _state.value.copy(error = "Address already exists")
                    return@launch
                }
                jellyseerrDao.insertAddress(
                    JellyseerrAddressEntity(
                        id = UUID.randomUUID(),
                        jellyfinServerId = serverId,
                        jellyfinUserId = userId,
                        address = cleanAddress,
                    )
                )
                reloadAndRefreshDetail()
                Timber.d("Jellyseerr address added: $address")
            } catch (e: Exception) {
                Timber.e(e, "Error adding Jellyseerr address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addAudiobookshelfAddress(serverId: String, address: String) {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.currentSession.value ?: return@launch
                if (currentSession.serverId != serverId) return@launch
                val cleanAddress = sanitizeUrl(address)
                val userId = currentSession.userId.toString()
                val existing = audiobookshelfDao.getAddressByUrl(serverId, userId, address)
                if (existing != null) {
                    _state.value = _state.value.copy(error = "Address already exists")
                    return@launch
                }
                audiobookshelfDao.insertAddress(
                    AudiobookshelfAddressEntity(
                        id = UUID.randomUUID(),
                        jellyfinServerId = serverId,
                        jellyfinUserId = userId,
                        address = cleanAddress,
                    )
                )
                reloadAndRefreshDetail()
                Timber.d("Audiobookshelf address added: $address")
            } catch (e: Exception) {
                Timber.e(e, "Error adding Audiobookshelf address")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private suspend fun reloadAndRefreshDetail() {
        val detailServerId = _state.value.detailServer?.server?.id
        loadServersInternal()
        if (detailServerId != null) {
            val updated = _state.value.servers.find { it.server.id == detailServerId }
            _state.value = _state.value.copy(detailServer = updated)
        }
    }

    private suspend fun loadServersInternal() {
        val currentSession = sessionManager.currentSession.value
        val currentBaseUrl = serverRepository.currentBaseUrl.value
        val servers = databaseRepository.getAllServers()
        val serversWithCounts =
            servers.map { server ->
                val users = databaseRepository.getUsersForServer(server.id)
                val addresses = databaseRepository.getServerAddresses(server.id)
                val isActive = currentSession?.serverId == server.id

                val currentUserId = currentSession?.userId?.toString()

                val userServicesList =
                    users.map { user ->
                        val userId = user.id.toString()
                        val hasJellyseerr = jellyseerrDao.getConfig(server.id, userId) != null
                        val hasAudiobookshelf =
                            audiobookshelfDao.getConfig(server.id, userId) != null
                        val hasTmdb =
                            securePreferencesRepository.getTmdbApiKey(server.id, userId) != null
                        val hasMdbList =
                            securePreferencesRepository.getMdbListApiKey(server.id, userId) != null

                        UserServiceInfo(
                            userId = userId,
                            userName = user.name,
                            serviceStatus =
                                ServiceStatus(
                                    jellyseerrConfigured = hasJellyseerr,
                                    audiobookshelfConfigured = hasAudiobookshelf,
                                    tmdbConfigured = hasTmdb,
                                    mdbListConfigured = hasMdbList,
                                ),
                        )
                    }
                val activeUserId = if (isActive) currentUserId else null
                val allJellyseerrAddresses =
                    if (activeUserId != null) {
                        jellyseerrDao.getAddresses(server.id, activeUserId).distinctBy {
                            it.address
                        }
                    } else emptyList()
                val allAudiobookshelfAddresses =
                    if (activeUserId != null) {
                        audiobookshelfDao.getAddresses(server.id, activeUserId).distinctBy {
                            it.address
                        }
                    } else emptyList()

                val currentUserStatus =
                    if (isActive && currentUserId != null) {
                        userServicesList.find { it.userId == currentUserId }?.serviceStatus
                            ?: ServiceStatus()
                    } else {
                        ServiceStatus()
                    }
                val connectionUrl =
                    if (isActive && currentBaseUrl.isNotBlank()) {
                        currentBaseUrl
                    } else {
                        server.address
                    }

                val jellyseerrUrl =
                    if (isActive && currentUserStatus.jellyseerrConfigured) {
                        securePreferencesRepository.getCachedJellyseerrServerUrl()
                    } else null
                val audiobookshelfUrl =
                    if (isActive && currentUserStatus.audiobookshelfConfigured) {
                        securePreferencesRepository.getCachedAudiobookshelfServerUrl()
                    } else null

                ServerWithUserCount(
                    server = server,
                    addresses = addresses,
                    userCount = users.size,
                    currentUserServiceStatus = currentUserStatus,
                    userServices = userServicesList,
                    addressType = classifyAddress(server.address),
                    currentConnectionUrl = connectionUrl,
                    currentConnectionType = classifyAddress(connectionUrl),
                    isActiveServer = isActive,
                    jellyseerrAddresses = allJellyseerrAddresses,
                    audiobookshelfAddresses = allAudiobookshelfAddresses,
                    jellyseerrConnectionUrl = jellyseerrUrl,
                    jellyseerrConnectionType = jellyseerrUrl?.let { classifyAddress(it) },
                    audiobookshelfConnectionUrl = audiobookshelfUrl,
                    audiobookshelfConnectionType = audiobookshelfUrl?.let { classifyAddress(it) },
                )
            }

        _state.value = _state.value.copy(servers = serversWithCounts, isLoading = false)
    }

    private fun sanitizeUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
