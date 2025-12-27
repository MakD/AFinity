package com.makd.afinity.data.repository.impl

import android.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.player.SubtitleHorizontalAlignment
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.SubtitleVerticalPosition
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.di.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @AppPreferences private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private object Keys {
        val CURRENT_SERVER_ID = stringPreferencesKey("current_server_id")
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val REMEMBER_LOGIN = booleanPreferencesKey("remember_login")

        val DEFAULT_SORT_BY = stringPreferencesKey("default_sort_by")
        val SORT_DESCENDING = booleanPreferencesKey("sort_descending")
        val ITEMS_PER_PAGE = intPreferencesKey("items_per_page")

        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val MAX_BITRATE = intPreferencesKey("max_bitrate")
        val SKIP_INTRO_ENABLED = booleanPreferencesKey("skip_intro_enabled")
        val SKIP_OUTRO_ENABLED = booleanPreferencesKey("skip_outro_enabled")
        val USE_EXO_PLAYER = booleanPreferencesKey("use_exo_player")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PIP_GESTURE_ENABLED = booleanPreferencesKey("pip_gesture_enabled")

        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val GRID_LAYOUT = booleanPreferencesKey("grid_layout")
        val COMBINE_LIBRARY_SECTIONS = booleanPreferencesKey("combine_library_sections")
        val HOME_SORT_BY_DATE_ADDED = booleanPreferencesKey("home_sort_by_date_added")

        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val MAX_DOWNLOADS = intPreferencesKey("max_downloads")

        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")

        val CRASH_REPORTING = booleanPreferencesKey("crash_reporting")
        val USAGE_ANALYTICS = booleanPreferencesKey("usage_analytics")

        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")

        val UPDATE_CHECK_FREQUENCY = intPreferencesKey("update_check_frequency")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")

        val VIDEO_ZOOM_MODE = intPreferencesKey("video_zoom_mode")

        val SUBTITLE_TEXT_COLOR = intPreferencesKey("subtitle_text_color")
        val SUBTITLE_TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        val SUBTITLE_BOLD = booleanPreferencesKey("subtitle_bold")
        val SUBTITLE_ITALIC = booleanPreferencesKey("subtitle_italic")
        val SUBTITLE_OUTLINE_STYLE = stringPreferencesKey("subtitle_outline_style")
        val SUBTITLE_OUTLINE_COLOR = intPreferencesKey("subtitle_outline_color")
        val SUBTITLE_OUTLINE_SIZE = stringPreferencesKey("subtitle_outline_size")
        val SUBTITLE_BACKGROUND_COLOR = intPreferencesKey("subtitle_background_color")
        val SUBTITLE_WINDOW_COLOR = intPreferencesKey("subtitle_window_color")
        val SUBTITLE_VERTICAL_POSITION = stringPreferencesKey("subtitle_vertical_position")
        val SUBTITLE_HORIZONTAL_ALIGNMENT = stringPreferencesKey("subtitle_horizontal_alignment")
    }

    override suspend fun setCurrentServerId(serverId: String?) {
        dataStore.edit { preferences ->
            if (serverId != null) {
                preferences[Keys.CURRENT_SERVER_ID] = serverId
            } else {
                preferences.remove(Keys.CURRENT_SERVER_ID)
            }
        }
    }

    override suspend fun getCurrentServerId(): String? {
        return dataStore.data.first()[Keys.CURRENT_SERVER_ID]
    }

    override fun getCurrentServerIdFlow(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[Keys.CURRENT_SERVER_ID]
        }
    }

    override suspend fun setCurrentUserId(userId: String?) {
        dataStore.edit { preferences ->
            if (userId != null) {
                preferences[Keys.CURRENT_USER_ID] = userId
            } else {
                preferences.remove(Keys.CURRENT_USER_ID)
            }
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return dataStore.data.first()[Keys.CURRENT_USER_ID]
    }

    override fun getCurrentUserIdFlow(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[Keys.CURRENT_USER_ID]
        }
    }

    override suspend fun setRememberLogin(remember: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.REMEMBER_LOGIN] = remember
        }
    }

    override suspend fun getRememberLogin(): Boolean {
        return dataStore.data.first()[Keys.REMEMBER_LOGIN] ?: true
    }

    override suspend fun setDefaultSortBy(sortBy: SortBy) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_SORT_BY] = sortBy.name
        }
    }

    override suspend fun getDefaultSortBy(): SortBy {
        val sortByName = dataStore.data.first()[Keys.DEFAULT_SORT_BY]
        return if (sortByName != null) {
            SortBy.fromString(sortByName)
        } else {
            SortBy.defaultValue
        }
    }

    override suspend fun setSortDescending(descending: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SORT_DESCENDING] = descending
        }
    }

    override suspend fun getSortDescending(): Boolean {
        return dataStore.data.first()[Keys.SORT_DESCENDING] ?: false
    }

    override suspend fun setItemsPerPage(count: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.ITEMS_PER_PAGE] = count
        }
    }

    override suspend fun getItemsPerPage(): Int {
        return dataStore.data.first()[Keys.ITEMS_PER_PAGE] ?: 50
    }

    override suspend fun setAutoPlay(autoPlay: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_PLAY] = autoPlay
        }
    }

    override suspend fun getAutoPlay(): Boolean {
        return dataStore.data.first()[Keys.AUTO_PLAY] ?: true
    }

    override suspend fun setMaxBitrate(bitrate: Int?) {
        dataStore.edit { preferences ->
            if (bitrate != null) {
                preferences[Keys.MAX_BITRATE] = bitrate
            } else {
                preferences.remove(Keys.MAX_BITRATE)
            }
        }
    }

    override suspend fun getMaxBitrate(): Int? {
        return dataStore.data.first()[Keys.MAX_BITRATE]
    }

    override suspend fun setCombineLibrarySections(combine: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.COMBINE_LIBRARY_SECTIONS] = combine
        }
    }

    override suspend fun getCombineLibrarySections(): Boolean {
        return dataStore.data.first()[Keys.COMBINE_LIBRARY_SECTIONS] ?: false
    }

    override fun getCombineLibrarySectionsFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[Keys.COMBINE_LIBRARY_SECTIONS] ?: false
        }
    }

    override suspend fun setHomeSortByDateAdded(sortByDateAdded: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HOME_SORT_BY_DATE_ADDED] = sortByDateAdded
        }
    }

    override suspend fun getHomeSortByDateAdded(): Boolean {
        return dataStore.data.first()[Keys.HOME_SORT_BY_DATE_ADDED] ?: true
    }

    override fun getHomeSortByDateAddedFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[Keys.HOME_SORT_BY_DATE_ADDED] ?: true
        }
    }

    override suspend fun setSkipIntroEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_INTRO_ENABLED] = enabled
        }
    }

    override suspend fun getSkipIntroEnabled(): Boolean {
        return dataStore.data.first()[Keys.SKIP_INTRO_ENABLED] ?: true
    }

    override suspend fun setSkipOutroEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_OUTRO_ENABLED] = enabled
        }
    }

    override suspend fun getSkipOutroEnabled(): Boolean {
        return dataStore.data.first()[Keys.SKIP_OUTRO_ENABLED] ?: true
    }

    override suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode
        }
    }

    override suspend fun getThemeMode(): String {
        return dataStore.data.first()[Keys.THEME_MODE] ?: "SYSTEM"
    }

    override fun getThemeModeFlow(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[Keys.THEME_MODE] ?: "SYSTEM"
        }
    }

    override fun getDynamicColorsFlow(): Flow<Boolean> =
        dataStore.data.map { it[Keys.DYNAMIC_COLORS] ?: true }

    override fun getAutoPlayFlow(): Flow<Boolean> =
        dataStore.data.map { it[Keys.AUTO_PLAY] ?: true }

    override fun getSkipIntroEnabledFlow(): Flow<Boolean> =
        dataStore.data.map { it[Keys.SKIP_INTRO_ENABLED] ?: true }

    override fun getSkipOutroEnabledFlow(): Flow<Boolean> =
        dataStore.data.map { it[Keys.SKIP_OUTRO_ENABLED] ?: true }

    override suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLORS] = enabled
        }
    }

    override suspend fun getDynamicColors(): Boolean {
        return dataStore.data.first()[Keys.DYNAMIC_COLORS] ?: true
    }

    override suspend fun setGridLayout(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.GRID_LAYOUT] = enabled
        }
    }

    override suspend fun getGridLayout(): Boolean {
        return dataStore.data.first()[Keys.GRID_LAYOUT] ?: true
    }

    override suspend fun setDownloadOverWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    override suspend fun getDownloadOverWifiOnly(): Boolean {
        return dataStore.data.first()[Keys.DOWNLOAD_WIFI_ONLY] ?: true
    }

    override suspend fun setDownloadQuality(quality: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_QUALITY] = quality
        }
    }

    override suspend fun getDownloadQuality(): String {
        return dataStore.data.first()[Keys.DOWNLOAD_QUALITY] ?: "720p"
    }

    override suspend fun setMaxDownloads(maxDownloads: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.MAX_DOWNLOADS] = maxDownloads
        }
    }

    override suspend fun getMaxDownloads(): Int {
        return dataStore.data.first()[Keys.MAX_DOWNLOADS] ?: 3
    }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SYNC_ENABLED] = enabled
        }
    }

    override suspend fun getSyncEnabled(): Boolean {
        return dataStore.data.first()[Keys.SYNC_ENABLED] ?: true
    }

    override suspend fun setSyncInterval(intervalMinutes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.SYNC_INTERVAL] = intervalMinutes
        }
    }

    override suspend fun getSyncInterval(): Int {
        return dataStore.data.first()[Keys.SYNC_INTERVAL] ?: 30
    }

    override suspend fun setLastSyncTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_SYNC_TIME] = timestamp
        }
    }

    override suspend fun getLastSyncTime(): Long {
        return dataStore.data.first()[Keys.LAST_SYNC_TIME] ?: 0L
    }

    override suspend fun setCrashReporting(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CRASH_REPORTING] = enabled
        }
    }

    override suspend fun getCrashReporting(): Boolean {
        return dataStore.data.first()[Keys.CRASH_REPORTING] ?: true
    }

    override suspend fun setUsageAnalytics(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.USAGE_ANALYTICS] = enabled
        }
    }

    override suspend fun getUsageAnalytics(): Boolean {
        return dataStore.data.first()[Keys.USAGE_ANALYTICS] ?: true
    }

    override suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.OFFLINE_MODE] = enabled
        }
    }

    override suspend fun getOfflineMode(): Boolean {
        return dataStore.data.first()[Keys.OFFLINE_MODE] ?: false
    }

    override fun getOfflineModeFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[Keys.OFFLINE_MODE] ?: false
        }
    }

    override suspend fun clearAllPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    override suspend fun clearServerPreferences() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.CURRENT_SERVER_ID)
            preferences.remove(Keys.CURRENT_USER_ID)
            preferences.remove(Keys.REMEMBER_LOGIN)
        }
    }

    override suspend fun clearUserPreferences() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.CURRENT_USER_ID)
            preferences.remove(Keys.LAST_SYNC_TIME)
        }
    }

    override val useExoPlayer: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.USE_EXO_PLAYER] ?: false
        }

    override suspend fun setUseExoPlayer(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.USE_EXO_PLAYER] = value
        }
    }

    override suspend fun setPipGestureEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.PIP_GESTURE_ENABLED] = enabled
        }
    }

    override suspend fun getPipGestureEnabled(): Boolean {
        return dataStore.data.first()[Keys.PIP_GESTURE_ENABLED] ?: false
    }

    override fun getPipGestureEnabledFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[Keys.PIP_GESTURE_ENABLED] ?: false
        }
    }

    override suspend fun setUpdateCheckFrequency(hours: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.UPDATE_CHECK_FREQUENCY] = hours
        }
    }

    override suspend fun getUpdateCheckFrequency(): Int {
        return dataStore.data.first()[Keys.UPDATE_CHECK_FREQUENCY] ?: 0
    }

    override fun getUpdateCheckFrequencyFlow(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[Keys.UPDATE_CHECK_FREQUENCY] ?: 0
        }
    }

    override suspend fun setLastUpdateCheck(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_UPDATE_CHECK] = timestamp
        }
    }

    override suspend fun getLastUpdateCheck(): Long {
        return dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L
    }

    override suspend fun setSubtitlePreferences(preferences: SubtitlePreferences) {
        dataStore.edit { prefs ->
            prefs[Keys.SUBTITLE_TEXT_COLOR] = preferences.textColor
            prefs[Keys.SUBTITLE_TEXT_SIZE] = preferences.textSize.toString()
            prefs[Keys.SUBTITLE_BOLD] = preferences.bold
            prefs[Keys.SUBTITLE_ITALIC] = preferences.italic
            prefs[Keys.SUBTITLE_OUTLINE_STYLE] = preferences.outlineStyle.name
            prefs[Keys.SUBTITLE_OUTLINE_COLOR] = preferences.outlineColor
            prefs[Keys.SUBTITLE_OUTLINE_SIZE] = preferences.outlineSize.toString()
            prefs[Keys.SUBTITLE_BACKGROUND_COLOR] = preferences.backgroundColor
            prefs[Keys.SUBTITLE_WINDOW_COLOR] = preferences.windowColor
            prefs[Keys.SUBTITLE_VERTICAL_POSITION] = preferences.verticalPosition.name
            prefs[Keys.SUBTITLE_HORIZONTAL_ALIGNMENT] = preferences.horizontalAlignment.name
        }
    }

    override suspend fun getSubtitlePreferences(): SubtitlePreferences {
        val prefs = dataStore.data.first()
        return SubtitlePreferences(
            textColor = prefs[Keys.SUBTITLE_TEXT_COLOR] ?: Color.WHITE,
            textSize = prefs[Keys.SUBTITLE_TEXT_SIZE]?.toFloatOrNull() ?: 1.0f,
            bold = prefs[Keys.SUBTITLE_BOLD] ?: false,
            italic = prefs[Keys.SUBTITLE_ITALIC] ?: false,
            outlineStyle = prefs[Keys.SUBTITLE_OUTLINE_STYLE]?.let {
                SubtitleOutlineStyle.fromString(
                    it
                )
            }
                ?: SubtitleOutlineStyle.NONE,
            outlineColor = prefs[Keys.SUBTITLE_OUTLINE_COLOR] ?: Color.BLACK,
            outlineSize = prefs[Keys.SUBTITLE_OUTLINE_SIZE]?.toFloatOrNull() ?: 0f,
            backgroundColor = prefs[Keys.SUBTITLE_BACKGROUND_COLOR] ?: Color.TRANSPARENT,
            windowColor = prefs[Keys.SUBTITLE_WINDOW_COLOR] ?: Color.TRANSPARENT,
            verticalPosition = prefs[Keys.SUBTITLE_VERTICAL_POSITION]?.let {
                SubtitleVerticalPosition.fromString(
                    it
                )
            }
                ?: SubtitleVerticalPosition.BOTTOM,
            horizontalAlignment = prefs[Keys.SUBTITLE_HORIZONTAL_ALIGNMENT]?.let {
                SubtitleHorizontalAlignment.fromString(
                    it
                )
            }
                ?: SubtitleHorizontalAlignment.CENTER
        )
    }

    override fun getSubtitlePreferencesFlow(): Flow<SubtitlePreferences> {
        return dataStore.data.map { prefs ->
            SubtitlePreferences(
                textColor = prefs[Keys.SUBTITLE_TEXT_COLOR] ?: Color.WHITE,
                textSize = prefs[Keys.SUBTITLE_TEXT_SIZE]?.toFloatOrNull() ?: 1.0f,
                bold = prefs[Keys.SUBTITLE_BOLD] ?: false,
                italic = prefs[Keys.SUBTITLE_ITALIC] ?: false,
                outlineStyle = prefs[Keys.SUBTITLE_OUTLINE_STYLE]?.let {
                    SubtitleOutlineStyle.fromString(
                        it
                    )
                }
                    ?: SubtitleOutlineStyle.NONE,
                outlineColor = prefs[Keys.SUBTITLE_OUTLINE_COLOR] ?: Color.BLACK,
                outlineSize = prefs[Keys.SUBTITLE_OUTLINE_SIZE]?.toFloatOrNull() ?: 0f,
                backgroundColor = prefs[Keys.SUBTITLE_BACKGROUND_COLOR] ?: Color.TRANSPARENT,
                windowColor = prefs[Keys.SUBTITLE_WINDOW_COLOR] ?: Color.TRANSPARENT,
                verticalPosition = prefs[Keys.SUBTITLE_VERTICAL_POSITION]?.let {
                    SubtitleVerticalPosition.fromString(
                        it
                    )
                }
                    ?: SubtitleVerticalPosition.BOTTOM,
                horizontalAlignment = prefs[Keys.SUBTITLE_HORIZONTAL_ALIGNMENT]?.let {
                    SubtitleHorizontalAlignment.fromString(
                        it
                    )
                }
                    ?: SubtitleHorizontalAlignment.CENTER
            )
        }
    }
}