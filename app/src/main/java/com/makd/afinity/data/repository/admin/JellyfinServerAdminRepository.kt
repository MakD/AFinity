package com.makd.afinity.data.repository.admin

import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.admin.ActivityEntry
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.models.admin.ActivityToday
import com.makd.afinity.data.models.admin.LibraryOptionsDocument
import com.makd.afinity.data.models.admin.ServerConfigSource
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.data.models.admin.UserPolicyDocument
import com.makd.afinity.data.paging.ActivityLogPagingSource
import com.makd.afinity.data.paging.toActivityEntry
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.di.DownloadClient
import com.makd.afinity.util.forUser
import com.makd.afinity.util.redactSecrets
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.operations.AuthenticationApi
import org.jellyfin.sdk.api.operations.BackupApi
import org.jellyfin.sdk.api.operations.ChannelApi
import org.jellyfin.sdk.api.operations.DeviceApi
import org.jellyfin.sdk.api.operations.EnvironmentApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.LibraryStructureApi
import org.jellyfin.sdk.api.operations.LocalizationApi
import org.jellyfin.sdk.api.operations.PluginApi
import org.jellyfin.sdk.api.operations.ScheduledTaskApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.ActivityLogEntryMessage
import org.jellyfin.sdk.model.api.AuthenticationInfo
import org.jellyfin.sdk.model.api.BackupManifestDto
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import org.jellyfin.sdk.model.api.CountryInfo
import org.jellyfin.sdk.model.api.CreateUserByName
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.DeviceInfoDto
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.FileSystemEntryType
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.LogFile
import org.jellyfin.sdk.model.api.MediaPathDto
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.PackageInfo
import org.jellyfin.sdk.model.api.ParentalRating
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.RepositoryInfo
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.UpdateUserPassword
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.VirtualFolderInfo
import timber.log.Timber

private const val STOP_TIMEOUT_MS = 5_000L
private const val POLL_INTERVAL_MS = 5_000L
private const val ACTIVITY_PAGE_SIZE = 50
private const val FAILED_SIGN_IN_WINDOW_HOURS = 24L
private const val FAILED_SIGN_IN_SAMPLE = 50
private const val FAILED_SIGN_IN_TYPE = "AuthenticationFailed"
private const val SIGN_IN_TYPE = "AuthenticationSucceeded"
private const val PLAYBACK_TYPE = "Playback"
private const val PLAYBACK_STOPPED_MARKER = "Stopped"
private const val TODAY_ACTIVITY_SAMPLE = 500
private const val LOG_COPY_BUFFER_BYTES = 64 * 1024
private const val FFMPEG_LOG_PREFIX = "FFmpeg."
private const val FAILURE_LOG_MAX_BYTES = 256 * 1024L
private const val FAILURE_REASON_MAX_CHARS = 200
private val FFMPEG_ERROR_LINE =
    Regex("(?i)error|failed|invalid|denied|refused|not found|unable|forbidden")
private val FFMPEG_CONTEXT_PREFIX = Regex("^\\[[^\\]]+\\]\\s*")
private val FFMPEG_GENERIC_LINES = setOf("Conversion failed!")

internal fun ffmpegFailureLine(log: String): String? =
    log.lineSequence()
        .map { it.trim() }
        .filter {
            it.isNotEmpty() && it !in FFMPEG_GENERIC_LINES && FFMPEG_ERROR_LINE.containsMatchIn(it)
        }
        .lastOrNull()
        ?.replace(FFMPEG_CONTEXT_PREFIX, "")
        ?.take(FAILURE_REASON_MAX_CHARS)
private const val CATALOG_TTL_MS = 10 * 60 * 1000L
private const val BACKUP_TIMEOUT_MINUTES = 30L
private const val CONFIG_PATH_SEPARATOR = '.'
private val SECRET_CONFIG_KEYS = setOf("CertificatePassword")
private const val LIBRARIES_PATH = "/Library/VirtualFolders"
private const val LIBRARY_OPTIONS_PATH = "/Library/VirtualFolders/LibraryOptions"
private const val LIBRARY_NAME_KEY = "Name"
private const val LIBRARY_TYPE_KEY = "CollectionType"
private const val LIBRARY_ITEM_ID_KEY = "ItemId"
private const val LIBRARY_OPTIONS_KEY = "LibraryOptions"
private const val LIBRARY_OPTIONS_ID_KEY = "Id"
private const val LIBRARY_PATH_INFOS_KEY = "PathInfos"
private const val LIBRARY_PATH_KEY = "Path"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val USERS_PATH = "/Users"
private const val USER_PATH = "/Users/{userId}"
private const val USER_POLICY_PATH = "/Users/{userId}/Policy"
private const val USER_ID = "userId"
private const val USER_NAME_KEY = "Name"
private const val USER_POLICY_KEY = "Policy"
private const val POLICY_IS_DISABLED_KEY = "IsDisabled"

private class CatalogCache(
    val sessionKey: String,
    val fetchedAtMs: Long,
    val packages: List<PackageInfo>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class JellyfinServerAdminRepository
@Inject
constructor(
    private val apiInvoker: JellyfinApiInvoker,
    private val sessionManager: SessionManager,
    private val deviceInfo: DeviceInfo,
    private val clientInfo: ClientInfo,
    @param:DownloadClient private val downloadClient: OkHttpClient,
    webSocketManager: JellyfinWebSocketManager,
    @ApplicationScope private val scope: CoroutineScope,
) : ServerAdminRepository {

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val taskRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val pluginRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _isCreatingBackup = MutableStateFlow(false)
    override val isCreatingBackup: StateFlow<Boolean> = _isCreatingBackup.asStateFlow()

    private val _userCountUpdates = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val userCountUpdates: Flow<Int> = _userCountUpdates.asSharedFlow()

    private val _deviceCountUpdates = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val deviceCountUpdates: Flow<Int> = _deviceCountUpdates.asSharedFlow()

    private val backupClient: OkHttpClient by lazy {
        downloadClient.newBuilder().readTimeout(BACKUP_TIMEOUT_MINUTES, TimeUnit.MINUTES).build()
    }

    private val catalogMutex = Mutex()

    @Volatile private var catalogCache: CatalogCache? = null

    private val adminSession: Flow<String?> =
        sessionManager.currentSession
            .map { session ->
                session?.takeIf { it.isAdmin == true }?.let { "${it.serverId}|${it.userId}" }
            }
            .distinctUntilChanged()

    private val reconnects: Flow<Unit> =
        webSocketManager.connectionState.drop(1).filter { it == WebSocketState.CONNECTED }.map {}

    override val systemInfo: StateFlow<Result<SystemInfo>?> =
        shared(
            triggers = merge(webSocketManager.restartRequired, reconnects, pluginRefreshRequests),
            pollWhile = { false },
        ) { api ->
            SystemApi(api).getSystemInfo().content
        }

    override val plugins: StateFlow<Result<List<PluginInfo>>?> =
        shared(
            triggers = merge(webSocketManager.restartRequired, reconnects, pluginRefreshRequests),
            pollWhile = { false },
        ) { api ->
            PluginApi(api).getPlugins().content
        }

    override val recentFailedSignIns: StateFlow<Result<Int>?> =
        shared(pollWhile = { false }) { api ->
            val since = LocalDateTime.now().minusHours(FAILED_SIGN_IN_WINDOW_HOURS)
            SystemApi(api)
                .getLogEntries(limit = FAILED_SIGN_IN_SAMPLE, type = FAILED_SIGN_IN_TYPE)
                .content
                .items
                .count { it.date.isAfter(since) }
        }

    override val sessions: StateFlow<Result<List<SessionInfoDto>>?> =
        shared(pushes = webSocketManager.liveSessions, pollWhile = { true }) { api ->
            SessionApi(api).getSessions().content
        }

    override val scheduledTasks: StateFlow<Result<List<TaskInfo>>?> =
        shared(
            pushes = webSocketManager.liveTasks,
            triggers = taskRefreshRequests,
            pollWhile = { tasks -> tasks.any { it.state != TaskState.IDLE } },
        ) { api ->
            ScheduledTaskApi(api).getTasks(isHidden = false).content
        }

    override fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    override fun refreshTasks() {
        taskRefreshRequests.tryEmit(Unit)
    }

    override fun refreshPlugins() {
        pluginRefreshRequests.tryEmit(Unit)
    }

    override suspend fun getUsers(): Result<List<UserDto>> =
        apiInvoker
            .apiResult { api, _ -> UserApi(api).getUsers().content }
            .onSuccess { _userCountUpdates.tryEmit(it.size) }

    override suspend fun createUser(name: String, password: String?): Result<UUID> =
        apiInvoker
            .apiResult { api, _ ->
                UserApi(api)
                    .createUserByName(
                        CreateUserByName(name = name.trim(), password = password?.ifEmpty { null })
                    )
                    .content
                    .id
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to create user")
            }

    override suspend fun renameUser(id: UUID, name: String): Result<Unit> =
        execute("rename user $id") { api, _ ->
            val user = getJson(api, USER_PATH, mapOf(USER_ID to id))
            val renamed = JsonObject(user + (USER_NAME_KEY to JsonPrimitive(name.trim())))
            api.request(
                method = HttpMethod.POST,
                pathTemplate = USERS_PATH,
                queryParameters = mapOf(USER_ID to id),
                requestBody = renamed,
            )
        }

    override suspend fun setUserPassword(
        id: UUID,
        newPassword: String,
        currentPassword: String?,
    ): Result<Unit> =
        execute("set password of user $id") { api, _ ->
            UserApi(api)
                .updateUserPassword(
                    userId = id,
                    data =
                        UpdateUserPassword(
                            currentPw = currentPassword,
                            newPw = newPassword,
                            resetPassword = false,
                        ),
                )
        }

    override suspend fun setUserDisabled(id: UUID, disabled: Boolean): Result<Unit> =
        execute("set user $id disabled=$disabled") { api, _ ->
            patchUserPolicy(api, id, mapOf(POLICY_IS_DISABLED_KEY to JsonPrimitive(disabled)))
        }

    override suspend fun deleteUser(id: UUID): Result<Unit> =
        execute("delete user $id") { api, _ -> UserApi(api).deleteUser(id) }

    override suspend fun setUserImage(
        id: UUID,
        imageData: ByteArray,
        mimeType: String,
    ): Result<Unit> =
        execute("set the picture of user $id") { api, _ ->
                val encoded =
                    Base64.encodeToString(imageData, Base64.NO_WRAP).toByteArray(Charsets.US_ASCII)
                ImageApi(api)
                    .postUserImage(
                        userId = id,
                        data = FileInfo(content = encoded, mediaType = mimeType),
                    )
            }
            .onSuccess { syncOwnImageTag(id) }

    override suspend fun deleteUserImage(id: UUID): Result<Unit> =
        execute("remove the picture of user $id") { api, _ ->
                ImageApi(api).deleteUserImage(userId = id)
            }
            .onSuccess { syncOwnImageTag(id) }

    private suspend fun syncOwnImageTag(id: UUID) {
        if (sessionManager.currentSession.value?.userId != id) return
        apiInvoker
            .apiResult { api, _ -> UserApi(api).getUserById(id).content.primaryImageTag }
            .onSuccess { tag -> sessionManager.updateCurrentUserImageTag(id, tag) }
    }

    override suspend fun getUserPolicy(id: UUID): Result<UserPolicyDocument> =
        apiInvoker.apiResult { api, _ ->
            val user = getJson(api, USER_PATH, mapOf(USER_ID to id))
            UserPolicyDocument(
                userName = (user[USER_NAME_KEY] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                policy = user.policyOf(id),
            )
        }

    override suspend fun updateUserPolicy(
        id: UUID,
        changes: Map<String, JsonElement>,
    ): Result<Unit> =
        execute("update policy of user $id") { api, _ -> patchUserPolicy(api, id, changes) }

    private suspend fun readUserPolicy(api: ApiClient, id: UUID): JsonObject =
        getJson(api, USER_PATH, mapOf(USER_ID to id)).policyOf(id)

    private fun JsonObject.policyOf(id: UUID): JsonObject =
        this[USER_POLICY_KEY] as? JsonObject
            ?: throw IOException("User $id has no policy in the server's answer")

    private suspend fun patchUserPolicy(
        api: ApiClient,
        id: UUID,
        changes: Map<String, JsonElement>,
    ) {
        val policy = readUserPolicy(api, id)
        api.request(
            method = HttpMethod.POST,
            pathTemplate = USER_POLICY_PATH,
            pathParameters = mapOf(USER_ID to id),
            requestBody = JsonObject(policy + changes),
        )
    }

    private suspend fun getJson(
        api: ApiClient,
        pathTemplate: String,
        pathParameters: Map<String, Any?> = emptyMap(),
    ): JsonObject {
        val response = api.request(pathTemplate = pathTemplate, pathParameters = pathParameters)
        return Json.parseToJsonElement(response.body.decodeToString()).jsonObject
    }

    override suspend fun getParentalRatings(): Result<List<ParentalRating>> =
        apiInvoker.apiResult { api, _ ->
            LocalizationApi(api).getParentalRatings().content
        }

    override suspend fun getCultures(): Result<List<CultureDto>> = apiInvoker.apiResult { api, _ ->
        LocalizationApi(api).getCultures().content
    }

    override suspend fun getCountries(): Result<List<CountryInfo>> =
        apiInvoker.apiResult { api, _ ->
            LocalizationApi(api).getCountries().content
        }

    override suspend fun getDevices(): Result<List<DeviceInfoDto>> =
        apiInvoker
            .apiResult { api, _ -> DeviceApi(api).getDevices().content.items }
            .onSuccess { _deviceCountUpdates.tryEmit(it.size) }

    override fun getActivityPaging(filter: ActivityFilter): Flow<PagingData<ActivityEntry>> =
        Pager(
                config =
                    PagingConfig(
                        pageSize = ACTIVITY_PAGE_SIZE,
                        enablePlaceholders = false,
                        initialLoadSize = ACTIVITY_PAGE_SIZE,
                    )
            ) {
                ActivityLogPagingSource(apiInvoker, filter)
            }
            .flow

    override suspend fun getServerConfig(source: ServerConfigSource): Result<JsonObject> =
        apiInvoker.apiResult { api, _ ->
            JsonObject(getJson(api, source.path).filterKeys { it !in SECRET_CONFIG_KEYS })
        }

    override suspend fun updateServerConfig(
        source: ServerConfigSource,
        changes: Map<String, JsonElement>,
    ): Result<Unit> =
        execute("update server configuration ${source.name}") { api, _ ->
            val current = getJson(api, source.path)
            api.request(
                method = HttpMethod.POST,
                pathTemplate = source.path,
                requestBody = current.withChanges(changes),
            )
        }

    private fun JsonObject.withChanges(changes: Map<String, JsonElement>): JsonObject {
        val result = toMutableMap()
        changes.forEach { (path, value) ->
            if (CONFIG_PATH_SEPARATOR in path) {
                val outer = path.substringBefore(CONFIG_PATH_SEPARATOR)
                val inner = path.substringAfter(CONFIG_PATH_SEPARATOR)
                val nested = (result[outer] as? JsonObject).orEmpty()
                result[outer] = JsonObject(nested + (inner to value))
            } else {
                result[path] = value
            }
        }
        return JsonObject(result)
    }

    override suspend fun getBackups(): Result<List<BackupManifestDto>> =
        apiInvoker.apiResult { api, _ ->
            BackupApi(api).listBackups().content
        }

    override suspend fun createBackup(
        metadata: Boolean,
        subtitles: Boolean,
        trickplay: Boolean,
    ): Result<Unit> {
        if (!_isCreatingBackup.compareAndSet(expect = false, update = true)) {
            return Result.failure(IllegalStateException("A backup is already being created"))
        }
        return scope
            .async<Result<Unit>>(Dispatchers.IO) {
                try {
                    val apiClient = sessionManager.getCurrentApiClient()
                    val baseUrl = apiClient?.baseUrl
                    if (apiClient == null || baseUrl == null) {
                        return@async Result.failure(NoActiveSessionException())
                    }
                    val options = buildJsonObject {
                        put("Database", true)
                        put("Metadata", metadata)
                        put("Subtitles", subtitles)
                        put("Trickplay", trickplay)
                    }
                    val request =
                        Request.Builder()
                            .url("$baseUrl/Backup/Create")
                            .header(
                                "Authorization",
                                "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                            )
                            .post(options.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                    backupClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Result.success(Unit)
                        } else {
                            Result.failure(IOException("HTTP ${response.code}"))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create backup")
                    Result.failure(e)
                } finally {
                    _isCreatingBackup.value = false
                }
            }
            .await()
    }

    override suspend fun getTodayActivity(): Result<ActivityToday> =
        apiInvoker.apiResult { api, _ ->
            val page =
                SystemApi(api)
                    .getLogEntries(
                        limit = TODAY_ACTIVITY_SAMPLE,
                        minDate = LocalDate.now().atStartOfDay(),
                    )
                    .content
            val today = page.items.map { it.toActivityEntry() }
            ActivityToday(
                plays =
                    today.count { entry ->
                        entry.type.contains(PLAYBACK_TYPE, ignoreCase = true) &&
                            !entry.type.contains(PLAYBACK_STOPPED_MARKER, ignoreCase = true)
                    },
                people = today.mapNotNull { it.userId }.distinct().size,
                signIns = today.count { it.type == SIGN_IN_TYPE },
                failedSignIns = today.count { it.type == FAILED_SIGN_IN_TYPE },
                isPartial = page.totalRecordCount > today.size,
            )
        }

    override fun activityLogChanges(): Flow<Unit> = flow {
        val apiClient = sessionManager.getCurrentApiClient() ?: return@flow
        emitAll(apiClient.webSocket.subscribe(ActivityLogEntryMessage::class).map {})
    }
        .catch { e -> Timber.w(e, "Activity log updates stopped") }

    override suspend fun getLogFiles(): Result<List<LogFile>> = apiInvoker.apiResult { api, _ ->
        SystemApi(api).getServerLogs().content
    }

    override suspend fun downloadLog(name: String, target: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apiClient = sessionManager.getCurrentApiClient()
            val baseUrl = apiClient?.baseUrl
            if (apiClient == null || baseUrl == null) {
                return@withContext Result.failure(NoActiveSessionException())
            }
            try {
                val request =
                    Request.Builder()
                        .url("$baseUrl/System/Logs/Log?name=${Uri.encode(name)}")
                        .header(
                            "Authorization",
                            "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                        )
                        .build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("HTTP ${response.code}"))
                    }
                    response.body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(LOG_COPY_BUFFER_BYTES)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    Result.success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getTranscodeFailureReason(
        mediaSourceId: String,
        since: LocalDateTime,
    ): String? {
        val key = "_${mediaSourceId.replace("-", "").lowercase()}_"
        val log =
            getLogFiles()
                .getOrNull()
                ?.filter {
                    it.name.startsWith(FFMPEG_LOG_PREFIX) &&
                        key in it.name.lowercase() &&
                        it.dateModified >= since &&
                        it.size <= FAILURE_LOG_MAX_BYTES
                }
                ?.maxByOrNull { it.dateModified } ?: return null
        val text = readLogText(log.name) ?: return null
        return ffmpegFailureLine(text)?.let(::redactSecrets)
    }

    private suspend fun readLogText(name: String): String? =
        withContext(Dispatchers.IO) {
            val apiClient = sessionManager.getCurrentApiClient()
            val baseUrl = apiClient?.baseUrl ?: return@withContext null
            try {
                val request =
                    Request.Builder()
                        .url("$baseUrl/System/Logs/Log?name=${Uri.encode(name)}")
                        .header(
                            "Authorization",
                            "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                        )
                        .build()
                downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to read server log $name")
                null
            }
        }

    override fun findOwnDeviceId(devices: List<DeviceInfoDto>): String? {
        val session = sessionManager.currentSession.value ?: return null
        val knownIds =
            setOfNotNull(
                deviceInfo.id,
                deviceInfo.forUser(session.userId).id,
                session.user?.name?.let { deviceInfo.forUser(it.lowercase()).id },
            )
        devices
            .firstOrNull { it.id in knownIds }
            ?.id
            ?.let {
                return it
            }
        return devices
            .filter { device ->
                device.appName == clientInfo.name &&
                    device.name == deviceInfo.name &&
                    device.lastUserId == session.userId
            }
            .maxWithOrNull(compareBy<DeviceInfoDto> { it.dateLastActivity })
            ?.id
    }

    override suspend fun renameDevice(id: String, name: String): Result<Unit> =
        execute("rename device") { api, _ ->
            DeviceApi(api)
                .updateDeviceOptions(
                    id = id,
                    data =
                        DeviceOptionsDto(
                            id = 0,
                            deviceId = id,
                            customName = name.trim().ifEmpty { null },
                        ),
                )
        }

    override suspend fun signOutDevices(ids: List<String>): Result<Unit> =
        execute("sign out ${ids.size} device(s)") { api, _ -> DeviceApi(api).deleteDevice(ids) }

    override suspend fun getLibraries(): Result<List<VirtualFolderInfo>> =
        apiInvoker.apiResult { api, _ ->
            LibraryStructureApi(api).getVirtualFolders().content
        }

    override suspend fun getChannels(deletableOnly: Boolean): Result<List<BaseItemDto>> =
        apiInvoker.apiResult { api, _ ->
            ChannelApi(api)
                .getChannels(supportsMediaDeletion = deletableOnly.takeIf { it })
                .content
                .items
        }

    override suspend fun getServerFolders(path: String?): Result<List<ServerFolder>> =
        apiInvoker.apiResult { api, _ ->
            val environment = EnvironmentApi(api)
            val entries =
                if (path.isNullOrBlank()) {
                    environment.getDrives().content
                } else {
                    environment
                        .getDirectoryContents(
                            path = path,
                            includeFiles = false,
                            includeDirectories = true,
                        )
                        .content
                }
            entries
                .filter { it.type != FileSystemEntryType.FILE }
                .map { ServerFolder(name = it.name, path = it.path) }
        }

    override suspend fun createLibrary(
        name: String,
        collectionType: CollectionTypeOptions?,
        paths: List<String>,
        options: JsonObject,
    ): Result<Unit> =
        execute("create a library") { api, _ ->
            val pathInfos =
                JsonArray(paths.map { path -> buildJsonObject { put(LIBRARY_PATH_KEY, path) } })
            api.request(
                method = HttpMethod.POST,
                pathTemplate = LIBRARIES_PATH,
                queryParameters =
                    buildMap<String, Any?> {
                        put("name", name.trim())
                        put("refreshLibrary", true)
                        collectionType?.let { put("collectionType", it.serialName) }
                    },
                requestBody =
                    buildJsonObject {
                        put(
                            LIBRARY_OPTIONS_KEY,
                            JsonObject(options + (LIBRARY_PATH_INFOS_KEY to pathInfos)),
                        )
                    },
            )
        }

    override suspend fun addLibraryFolder(name: String, path: String): Result<Unit> =
        execute("add a folder to library $name") { api, _ ->
            LibraryStructureApi(api)
                .addMediaPath(
                    refreshLibrary = true,
                    data = MediaPathDto(name = name, path = path.trim()),
                )
        }

    override suspend fun removeLibraryFolder(name: String, path: String): Result<Unit> =
        execute("remove a folder from library $name") { api, _ ->
            LibraryStructureApi(api)
                .removeMediaPath(name = name, path = path, refreshLibrary = true)
        }

    override suspend fun renameLibrary(name: String, newName: String): Result<Unit> =
        execute("rename library $name") { api, _ ->
            LibraryStructureApi(api)
                .renameVirtualFolder(name = name, newName = newName.trim(), refreshLibrary = true)
        }

    override suspend fun removeLibrary(name: String): Result<Unit> =
        execute("remove library $name") { api, _ ->
            LibraryStructureApi(api).removeVirtualFolder(name = name, refreshLibrary = true)
        }

    override suspend fun getLibraryOptions(name: String): Result<LibraryOptionsDocument> =
        apiInvoker.apiResult { api, _ ->
            val library = readLibrary(api, name)
            LibraryOptionsDocument(
                collectionType = (library[LIBRARY_TYPE_KEY] as? JsonPrimitive)?.contentOrNull,
                options = library[LIBRARY_OPTIONS_KEY] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }

    override suspend fun updateLibraryOptions(
        name: String,
        changes: Map<String, JsonElement>,
    ): Result<Unit> =
        execute("update options of library $name") { api, _ ->
            val library = readLibrary(api, name)
            val itemId =
                library[LIBRARY_ITEM_ID_KEY] as? JsonPrimitive
                    ?: throw IOException("Library $name has no id yet")
            val options = library[LIBRARY_OPTIONS_KEY] as? JsonObject ?: JsonObject(emptyMap())
            api.request(
                method = HttpMethod.POST,
                pathTemplate = LIBRARY_OPTIONS_PATH,
                requestBody =
                    buildJsonObject {
                        put(LIBRARY_OPTIONS_ID_KEY, itemId)
                        put(LIBRARY_OPTIONS_KEY, JsonObject(options + changes))
                    },
            )
        }

    private suspend fun readLibrary(api: ApiClient, name: String): JsonObject {
        val response = api.request(pathTemplate = LIBRARIES_PATH)
        return Json.parseToJsonElement(response.body.decodeToString())
            .jsonArray
            .mapNotNull { it as? JsonObject }
            .firstOrNull { (it[LIBRARY_NAME_KEY] as? JsonPrimitive)?.contentOrNull == name }
            ?: throw IOException("Library $name is not on the server")
    }

    override suspend fun getPluginSettingsPages(): Result<Map<UUID, String>> =
        apiInvoker.apiResult { api, _ ->
            val pages = PluginApi(api).getConfigurationPages().content
            buildMap {
                pages.forEach { page ->
                    val pluginId = page.pluginId ?: return@forEach
                    if (page.enableInMainMenu || pluginId !in this) put(pluginId, page.name)
                }
            }
        }

    override suspend fun setPluginEnabled(
        id: UUID,
        version: String,
        enabled: Boolean,
    ): Result<Unit> =
        execute("set plugin $id enabled=$enabled") { api, _ ->
            if (enabled) PluginApi(api).enablePlugin(id, version)
            else PluginApi(api).disablePlugin(id, version)
        }

    override suspend fun uninstallPlugin(id: UUID, version: String): Result<Unit> =
        execute("uninstall plugin $id") { api, _ ->
            PluginApi(api).uninstallPluginByVersion(id, version)
        }

    override suspend fun getPluginCatalog(forceRefresh: Boolean): Result<List<PackageInfo>> =
        catalogMutex.withLock {
            val sessionKey =
                sessionManager.currentSession.value?.let { "${it.serverId}|${it.userId}" }
            val cached = catalogCache
            val isFresh =
                cached != null &&
                    cached.sessionKey == sessionKey &&
                    SystemClock.elapsedRealtime() - cached.fetchedAtMs < CATALOG_TTL_MS
            if (cached != null && isFresh && !forceRefresh) {
                Result.success(cached.packages)
            } else {
                apiInvoker
                    .apiResult { api, _ -> PluginApi(api).getPackages().content }
                    .onSuccess { packages ->
                        if (sessionKey != null) {
                            catalogCache =
                                CatalogCache(sessionKey, SystemClock.elapsedRealtime(), packages)
                        }
                    }
            }
        }

    override suspend fun installPlugin(
        name: String,
        id: UUID,
        version: String,
        repositoryUrl: String,
    ): Result<Unit> =
        execute("install plugin $name $version") { api, _ ->
            PluginApi(api)
                .installPackage(
                    name = name,
                    assemblyGuid = id,
                    version = version,
                    repositoryUrl = repositoryUrl,
                )
        }

    override suspend fun getPluginRepositories(): Result<List<RepositoryInfo>> =
        apiInvoker.apiResult { api, _ ->
            PluginApi(api).getRepositories().content
        }

    override suspend fun setPluginRepositories(repositories: List<RepositoryInfo>): Result<Unit> =
        execute("set plugin repositories") { api, _ ->
                PluginApi(api).setRepositories(repositories)
            }
            .onSuccess { catalogCache = null }

    override suspend fun getApiKeys(): Result<List<AuthenticationInfo>> =
        apiInvoker.apiResult { api, _ ->
            AuthenticationApi(api).getKeys().content.items
        }

    override suspend fun createApiKey(appName: String): Result<Unit> =
        execute("create api key") { api, _ -> AuthenticationApi(api).createKey(appName.trim()) }

    override suspend fun revokeApiKey(key: String): Result<Unit> =
        execute("revoke api key") { api, _ -> AuthenticationApi(api).revokeKey(key) }

    override suspend fun sendPlaystateCommand(
        sessionId: String,
        command: PlaystateCommand,
        seekPositionTicks: Long?,
    ): Result<Unit> =
        execute("send playstate command $command to session $sessionId") { api, _ ->
            SessionApi(api)
                .sendPlaystateCommand(
                    sessionId = sessionId,
                    command = command,
                    seekPositionTicks = seekPositionTicks,
                )
        }

    override suspend fun sendGeneralCommand(
        sessionId: String,
        command: GeneralCommandType,
    ): Result<Unit> =
        execute("send general command $command to session $sessionId") { api, _ ->
            SessionApi(api).sendGeneralCommand(sessionId = sessionId, command = command)
        }

    override suspend fun setVolume(sessionId: String, volume: Int): Result<Unit> =
        execute("set volume on session $sessionId") { api, userId ->
            SessionApi(api)
                .sendFullGeneralCommand(
                    sessionId = sessionId,
                    data =
                        GeneralCommand(
                            name = GeneralCommandType.SET_VOLUME,
                            controllingUserId = userId,
                            arguments = mapOf("Volume" to volume.coerceIn(0, 100).toString()),
                        ),
                )
        }

    override suspend fun sendMessage(
        sessionId: String,
        header: String,
        text: String,
        timeoutMs: Long?,
    ): Result<Unit> =
        execute("send message to session $sessionId") { api, _ ->
            SessionApi(api)
                .sendMessageCommand(
                    sessionId = sessionId,
                    data = MessageCommand(header = header, text = text, timeoutMs = timeoutMs),
                )
        }

    override suspend fun restartServer(): Result<Unit> =
        execute("restart server") { api, _ -> SystemApi(api).restartApplication() }

    override suspend fun shutdownServer(): Result<Unit> =
        execute("shut down server") { api, _ -> SystemApi(api).shutdownApplication() }

    override suspend fun scanAllLibraries(): Result<Unit> =
        execute("scan libraries") { api, _ -> LibraryApi(api).refreshLibrary() }

    override suspend fun scanLibrary(itemId: UUID): Result<Unit> =
        execute("scan library $itemId") { api, _ ->
            LibraryApi(api)
                .refreshItem(
                    itemId = itemId,
                    metadataRefreshMode = MetadataRefreshMode.DEFAULT,
                    imageRefreshMode = MetadataRefreshMode.DEFAULT,
                    replaceAllMetadata = false,
                    replaceAllImages = false,
                    regenerateTrickplay = false,
                )
        }

    override suspend fun getLibraryImageTags(itemIds: List<UUID>): Result<Map<UUID, String>> =
        if (itemIds.isEmpty()) {
            Result.success(emptyMap())
        } else {
            apiInvoker.apiResult { api, userId ->
                LibraryApi(api)
                    .getItems(userId = userId, ids = itemIds)
                    .content
                    .items
                    .mapNotNull { item ->
                        item.imageTags?.get(ImageType.PRIMARY)?.let { tag -> item.id to tag }
                    }
                    .toMap()
            }
        }

    override suspend fun startTask(taskId: String): Result<Unit> =
        execute("start task $taskId") { api, _ -> ScheduledTaskApi(api).startTask(taskId) }

    override suspend fun stopTask(taskId: String): Result<Unit> =
        execute("stop task $taskId") { api, _ -> ScheduledTaskApi(api).stopTask(taskId) }

    override suspend fun updateTaskTriggers(
        taskId: String,
        triggers: List<TaskTriggerInfo>,
    ): Result<Unit> =
        execute("update triggers of task $taskId") { api, _ ->
            ScheduledTaskApi(api).updateTask(taskId, triggers)
        }

    private suspend fun execute(
        description: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> Unit,
    ): Result<Unit> =
        apiInvoker.apiResult(block).onFailure { e ->
            if (e !is NoActiveSessionException) Timber.e(e, "Failed to $description")
        }

    private fun <T : Any> shared(
        pushes: Flow<T> = emptyFlow(),
        triggers: Flow<Unit> = emptyFlow(),
        pollWhile: (T) -> Boolean,
        fetch: suspend (ApiClient) -> T,
    ): StateFlow<Result<T>?> {
        var servedSession: String? = null
        return adminSession
            .flatMapLatest { session ->
                if (session == null) {
                    servedSession = null
                    flowOf<Result<T>?>(null)
                } else {
                    val showLoading = servedSession != session
                    servedSession = session
                    resource(showLoading, pushes, triggers, pollWhile, fetch)
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
    }

    private fun <T : Any> resource(
        showLoading: Boolean,
        pushes: Flow<T>,
        triggers: Flow<Unit>,
        pollWhile: (T) -> Boolean,
        fetch: suspend (ApiClient) -> T,
    ): Flow<Result<T>?> = flow {
        if (showLoading) emit(null)
        var hasData = false
        updates(pushes, triggers, pollWhile, fetch).collect { result ->
            if (result.isSuccess) {
                hasData = true
                emit(result)
            } else if (!hasData) {
                emit(result)
            }
        }
    }

    private fun <T : Any> updates(
        pushes: Flow<T>,
        triggers: Flow<Unit>,
        pollWhile: (T) -> Boolean,
        fetch: suspend (ApiClient) -> T,
    ): Flow<Result<T>> = channelFlow {
        val latest = AtomicReference<T?>(null)

        suspend fun load() {
            val result = apiInvoker.apiResult { api, _ -> fetch(api) }
            result.onSuccess { latest.set(it) }
            send(result)
        }

        launch {
            pushes.collect { value ->
                latest.set(value)
                send(Result.success(value))
            }
        }
        launch { merge(refreshRequests, triggers).collect { load() } }

        load()
        while (isActive) {
            delay(POLL_INTERVAL_MS.milliseconds)
            val current = latest.get()
            if (current == null || pollWhile(current)) load()
        }
    }
}
