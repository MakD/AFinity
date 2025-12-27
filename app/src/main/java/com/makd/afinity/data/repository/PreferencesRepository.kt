package com.makd.afinity.data.repository

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.player.SubtitlePreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {

    suspend fun setCurrentServerId(serverId: String?)
    suspend fun getCurrentServerId(): String?
    fun getCurrentServerIdFlow(): Flow<String?>

    suspend fun setCurrentUserId(userId: String?)
    suspend fun getCurrentUserId(): String?
    fun getCurrentUserIdFlow(): Flow<String?>

    suspend fun setRememberLogin(remember: Boolean)
    suspend fun getRememberLogin(): Boolean

    suspend fun setDefaultSortBy(sortBy: SortBy)
    suspend fun getDefaultSortBy(): SortBy

    suspend fun setSortDescending(descending: Boolean)
    suspend fun getSortDescending(): Boolean

    suspend fun setItemsPerPage(count: Int)
    suspend fun getItemsPerPage(): Int

    suspend fun setAutoPlay(autoPlay: Boolean)
    suspend fun getAutoPlay(): Boolean
    fun getAutoPlayFlow(): Flow<Boolean>

    suspend fun setMaxBitrate(bitrate: Int?)
    suspend fun getMaxBitrate(): Int?

    suspend fun setSkipIntroEnabled(enabled: Boolean)
    suspend fun getSkipIntroEnabled(): Boolean
    fun getSkipIntroEnabledFlow(): Flow<Boolean>

    suspend fun setSkipOutroEnabled(enabled: Boolean)
    suspend fun getSkipOutroEnabled(): Boolean
    fun getSkipOutroEnabledFlow(): Flow<Boolean>

    val useExoPlayer: Flow<Boolean>
    suspend fun setUseExoPlayer(value: Boolean)

    suspend fun setThemeMode(mode: String)
    suspend fun getThemeMode(): String
    fun getThemeModeFlow(): Flow<String>

    suspend fun setDynamicColors(enabled: Boolean)
    suspend fun getDynamicColors(): Boolean
    fun getDynamicColorsFlow(): Flow<Boolean>

    suspend fun setPipGestureEnabled(enabled: Boolean)
    suspend fun getPipGestureEnabled(): Boolean
    fun getPipGestureEnabledFlow(): Flow<Boolean>

    suspend fun setGridLayout(enabled: Boolean)
    suspend fun getGridLayout(): Boolean

    suspend fun setCombineLibrarySections(combine: Boolean)
    suspend fun getCombineLibrarySections(): Boolean
    fun getCombineLibrarySectionsFlow(): Flow<Boolean>
    suspend fun setHomeSortByDateAdded(sortByDateAdded: Boolean)
    suspend fun getHomeSortByDateAdded(): Boolean
    fun getHomeSortByDateAddedFlow(): Flow<Boolean>

    suspend fun setDownloadOverWifiOnly(wifiOnly: Boolean)
    suspend fun getDownloadOverWifiOnly(): Boolean

    suspend fun setDownloadQuality(quality: String)
    suspend fun getDownloadQuality(): String

    suspend fun setMaxDownloads(maxDownloads: Int)
    suspend fun getMaxDownloads(): Int

    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun getSyncEnabled(): Boolean

    suspend fun setSyncInterval(intervalMinutes: Int)
    suspend fun getSyncInterval(): Int

    suspend fun setLastSyncTime(timestamp: Long)
    suspend fun getLastSyncTime(): Long

    suspend fun setUpdateCheckFrequency(hours: Int)
    suspend fun getUpdateCheckFrequency(): Int
    fun getUpdateCheckFrequencyFlow(): Flow<Int>

    suspend fun setLastUpdateCheck(timestamp: Long)
    suspend fun getLastUpdateCheck(): Long

    suspend fun setCrashReporting(enabled: Boolean)
    suspend fun getCrashReporting(): Boolean

    suspend fun setUsageAnalytics(enabled: Boolean)
    suspend fun getUsageAnalytics(): Boolean

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun getOfflineMode(): Boolean
    fun getOfflineModeFlow(): Flow<Boolean>

    suspend fun setSubtitlePreferences(preferences: SubtitlePreferences)
    suspend fun getSubtitlePreferences(): SubtitlePreferences
    fun getSubtitlePreferencesFlow(): Flow<SubtitlePreferences>

    suspend fun clearAllPreferences()
    suspend fun clearServerPreferences()
    suspend fun clearUserPreferences()
}