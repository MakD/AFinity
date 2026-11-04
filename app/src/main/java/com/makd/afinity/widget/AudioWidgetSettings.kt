package com.makd.afinity.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

enum class WidgetAlignment {
    LEFT,
    RIGHT,
}

enum class WidgetTextColour {
    AUTO,
    LIGHT,
    DARK,
}

enum class WidgetBadgeColours {
    STATUS,
    MATERIAL_YOU,
}

data class AudioWidgetSettings(
    val showCover: Boolean = false,
    val showStatus: Boolean = true,
    val alignment: WidgetAlignment = WidgetAlignment.RIGHT,
    val showContinue: Boolean = true,
    val backgroundOpacity: Int = 0,
    val textShadow: Boolean = true,
    val textColour: WidgetTextColour = WidgetTextColour.AUTO,
    val badgeColours: WidgetBadgeColours = WidgetBadgeColours.STATUS,
)

@Singleton
class AudioWidgetSettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun get(widgetId: Int): AudioWidgetSettings {
        val prefs =
            try {
                dataStore.data.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Widget settings: read failed")
                return AudioWidgetSettings()
            }
        val keys = Keys(widgetId)
        val defaults = AudioWidgetSettings()
        return AudioWidgetSettings(
            showCover = prefs[keys.cover] ?: defaults.showCover,
            showStatus = prefs[keys.status] ?: defaults.showStatus,
            alignment = enumOr(prefs[keys.alignment], defaults.alignment),
            showContinue = prefs[keys.continueListening] ?: defaults.showContinue,
            backgroundOpacity =
                (prefs[keys.opacity] ?: defaults.backgroundOpacity).coerceIn(0, 100),
            textShadow = prefs[keys.shadow] ?: defaults.textShadow,
            textColour = enumOr(prefs[keys.textColour], defaults.textColour),
            badgeColours = enumOr(prefs[keys.badge], defaults.badgeColours),
        )
    }

    suspend fun save(widgetId: Int, settings: AudioWidgetSettings) {
        val keys = Keys(widgetId)
        dataStore.edit {
            it[keys.cover] = settings.showCover
            it[keys.status] = settings.showStatus
            it[keys.alignment] = settings.alignment.name
            it[keys.continueListening] = settings.showContinue
            it[keys.opacity] = settings.backgroundOpacity.coerceIn(0, 100)
            it[keys.shadow] = settings.textShadow
            it[keys.textColour] = settings.textColour.name
            it[keys.badge] = settings.badgeColours.name
        }
    }

    suspend fun delete(widgetIds: IntArray) {
        dataStore.edit { prefs ->
            widgetIds.forEach { id ->
                val keys = Keys(id)
                prefs.remove(keys.cover)
                prefs.remove(keys.status)
                prefs.remove(keys.alignment)
                prefs.remove(keys.continueListening)
                prefs.remove(keys.opacity)
                prefs.remove(keys.shadow)
                prefs.remove(keys.textColour)
                prefs.remove(keys.badge)
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private class Keys(id: Int) {
        val cover = booleanPreferencesKey("widget_${id}_cover")
        val status = booleanPreferencesKey("widget_${id}_status")
        val alignment = stringPreferencesKey("widget_${id}_alignment")
        val continueListening = booleanPreferencesKey("widget_${id}_continue")
        val opacity = intPreferencesKey("widget_${id}_opacity")
        val shadow = booleanPreferencesKey("widget_${id}_shadow")
        val textColour = stringPreferencesKey("widget_${id}_text_colour")
        val badge = stringPreferencesKey("widget_${id}_badge")
    }
}
