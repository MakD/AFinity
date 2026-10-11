package com.makd.afinity.data.repository.admin

import androidx.paging.PagingData
import com.makd.afinity.data.models.admin.ActivityEntry
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.models.admin.LibraryOptionsDocument
import com.makd.afinity.data.models.admin.ServerConfigSource
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.data.models.admin.ServerLogTail
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jellyfin.sdk.model.api.AuthenticationInfo
import org.jellyfin.sdk.model.api.BackupManifestDto
import org.jellyfin.sdk.model.api.CollectionTypeOptions
import org.jellyfin.sdk.model.api.CountryInfo
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.DeviceInfoDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.LogFile
import org.jellyfin.sdk.model.api.PackageInfo
import org.jellyfin.sdk.model.api.ParentalRating
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.RepositoryInfo
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.VirtualFolderInfo

interface ServerAdminRepository {

    val systemInfo: StateFlow<Result<SystemInfo>?>

    val plugins: StateFlow<Result<List<PluginInfo>>?>

    val recentFailedSignIns: StateFlow<Result<Int>?>

    val sessions: StateFlow<Result<List<SessionInfoDto>>?>

    val scheduledTasks: StateFlow<Result<List<TaskInfo>>?>

    val isCreatingBackup: StateFlow<Boolean>

    val userCountUpdates: Flow<Int>

    val deviceCountUpdates: Flow<Int>

    fun refresh()

    suspend fun getServerConfig(source: ServerConfigSource): Result<JsonObject>

    suspend fun updateServerConfig(
        source: ServerConfigSource,
        changes: Map<String, JsonElement>,
    ): Result<Unit>

    suspend fun getBackups(): Result<List<BackupManifestDto>>

    suspend fun createBackup(
        metadata: Boolean,
        subtitles: Boolean,
        trickplay: Boolean,
    ): Result<Unit>

    suspend fun getUsers(): Result<List<UserDto>>

    suspend fun createUser(name: String, password: String?): Result<UUID>

    suspend fun renameUser(id: UUID, name: String): Result<Unit>

    suspend fun setUserPassword(
        id: UUID,
        newPassword: String,
        currentPassword: String?,
    ): Result<Unit>

    suspend fun setUserDisabled(id: UUID, disabled: Boolean): Result<Unit>

    suspend fun deleteUser(id: UUID): Result<Unit>

    suspend fun getUserPolicy(id: UUID): Result<JsonObject>

    suspend fun updateUserPolicy(id: UUID, changes: Map<String, JsonElement>): Result<Unit>

    suspend fun getParentalRatings(): Result<List<ParentalRating>>

    suspend fun getCultures(): Result<List<CultureDto>>

    suspend fun getCountries(): Result<List<CountryInfo>>

    suspend fun getDevices(): Result<List<DeviceInfoDto>>

    fun findOwnDeviceId(devices: List<DeviceInfoDto>): String?

    suspend fun renameDevice(id: String, name: String): Result<Unit>

    suspend fun signOutDevices(ids: List<String>): Result<Unit>

    suspend fun getLibraries(): Result<List<VirtualFolderInfo>>

    suspend fun getServerFolders(path: String?): Result<List<ServerFolder>>

    suspend fun createLibrary(
        name: String,
        collectionType: CollectionTypeOptions?,
        paths: List<String>,
        options: JsonObject,
    ): Result<Unit>

    suspend fun addLibraryFolder(name: String, path: String): Result<Unit>

    suspend fun removeLibraryFolder(name: String, path: String): Result<Unit>

    suspend fun renameLibrary(name: String, newName: String): Result<Unit>

    suspend fun removeLibrary(name: String): Result<Unit>

    suspend fun getLibraryOptions(name: String): Result<LibraryOptionsDocument>

    suspend fun updateLibraryOptions(name: String, changes: Map<String, JsonElement>): Result<Unit>

    suspend fun getPluginSettingsPages(): Result<Map<UUID, String>>

    suspend fun setPluginEnabled(id: UUID, version: String, enabled: Boolean): Result<Unit>

    suspend fun uninstallPlugin(id: UUID, version: String): Result<Unit>

    suspend fun getPluginCatalog(forceRefresh: Boolean = false): Result<List<PackageInfo>>

    suspend fun installPlugin(
        name: String,
        id: UUID,
        version: String,
        repositoryUrl: String,
    ): Result<Unit>

    suspend fun getPluginRepositories(): Result<List<RepositoryInfo>>

    suspend fun setPluginRepositories(repositories: List<RepositoryInfo>): Result<Unit>

    suspend fun getApiKeys(): Result<List<AuthenticationInfo>>

    suspend fun createApiKey(appName: String): Result<Unit>

    suspend fun revokeApiKey(key: String): Result<Unit>

    fun getActivityPaging(filter: ActivityFilter): Flow<PagingData<ActivityEntry>>

    fun activityLogChanges(): Flow<Unit>

    suspend fun getLogFiles(): Result<List<LogFile>>

    suspend fun getLogTail(name: String, maxLines: Int): Result<ServerLogTail>

    suspend fun sendPlaystateCommand(
        sessionId: String,
        command: PlaystateCommand,
        seekPositionTicks: Long? = null,
    ): Result<Unit>

    suspend fun sendGeneralCommand(sessionId: String, command: GeneralCommandType): Result<Unit>

    suspend fun setVolume(sessionId: String, volume: Int): Result<Unit>

    suspend fun sendMessage(
        sessionId: String,
        header: String,
        text: String,
        timeoutMs: Long? = null,
    ): Result<Unit>

    suspend fun restartServer(): Result<Unit>

    suspend fun shutdownServer(): Result<Unit>

    suspend fun scanAllLibraries(): Result<Unit>

    suspend fun startTask(taskId: String): Result<Unit>

    suspend fun stopTask(taskId: String): Result<Unit>

    suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit>
}
