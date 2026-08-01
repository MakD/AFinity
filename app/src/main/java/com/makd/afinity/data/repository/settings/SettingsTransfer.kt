package com.makd.afinity.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.models.AfinitySettingsExport
import com.makd.afinity.data.models.SettingsSection
import com.makd.afinity.data.repository.home.HomeConfigTransfer
import com.makd.afinity.data.repository.home.ImportPlan
import com.makd.afinity.di.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SettingsImportResult {
    data class Ready(val preview: SettingsImportPreview) : SettingsImportResult

    data class Failed(val reason: SettingsImportFailure) : SettingsImportResult
}

enum class SettingsImportFailure {
    NOT_AFINITY_BACKUP,
    NEWER_SCHEMA,
    UNREADABLE,
}

data class SettingsImportPreview(
    val appVersion: String,
    val sections: List<SettingsSection>,
    val prefCounts: Map<SettingsSection, Int>,
    val homePlan: ImportPlan?,
    val payload: AfinitySettingsExport,
)

@Singleton
class SettingsTransfer
@Inject
constructor(
    @param:AppPreferences private val dataStore: DataStore<Preferences>,
    private val homeConfigTransfer: HomeConfigTransfer,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): String =
        withContext(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val preferences =
                PortablePreferences.bySection.mapNotNull { (section, specs) ->
                    val entries = specs.mapNotNull { spec ->
                        readPref(prefs, spec)?.let { spec.name to it }
                    }
                    if (entries.isEmpty()) null
                    else
                        section.key to
                            buildJsonObject { entries.forEach { put(it.first, it.second) } }
                }

            val export =
                AfinitySettingsExport(
                    exportedAt = Instant.now().toString(),
                    appVersion = BuildConfig.VERSION_NAME,
                    home = homeConfigTransfer.exportPayload(),
                    preferences = preferences.toMap(),
                )
            json.encodeToString(AfinitySettingsExport.serializer(), export)
        }

    suspend fun parse(raw: String): SettingsImportResult =
        withContext(Dispatchers.IO) {
            val payload =
                try {
                    json.decodeFromString(AfinitySettingsExport.serializer(), raw)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse settings backup")
                    return@withContext SettingsImportResult.Failed(SettingsImportFailure.UNREADABLE)
                }

            if (payload.format != AfinitySettingsExport.FORMAT) {
                return@withContext SettingsImportResult.Failed(
                    SettingsImportFailure.NOT_AFINITY_BACKUP
                )
            }
            if (payload.schemaVersion > AfinitySettingsExport.SCHEMA_VERSION) {
                return@withContext SettingsImportResult.Failed(SettingsImportFailure.NEWER_SCHEMA)
            }

            val homePlan = payload.home?.let { homeConfigTransfer.planFor(it) }
            val prefCounts =
                payload.preferences
                    .mapNotNull { (key, obj) ->
                        val section = SettingsSection.fromKey(key) ?: return@mapNotNull null
                        val known = obj.keys.count { PortablePreferences.find(it) != null }
                        if (known == 0) null else section to known
                    }
                    .toMap()

            val sections =
                SettingsSection.entries.filter { section ->
                    if (section == SettingsSection.HOME) homePlan != null
                    else prefCounts.containsKey(section)
                }

            SettingsImportResult.Ready(
                SettingsImportPreview(
                    appVersion = payload.appVersion,
                    sections = sections,
                    prefCounts = prefCounts,
                    homePlan = homePlan,
                    payload = payload,
                )
            )
        }

    suspend fun apply(preview: SettingsImportPreview, selected: Set<SettingsSection>) =
        withContext(Dispatchers.IO) {
            if (SettingsSection.HOME in selected) {
                preview.homePlan?.let { homeConfigTransfer.apply(it) }
            }

            val prefSections = selected.filterNot { it == SettingsSection.HOME }
            if (prefSections.isEmpty()) return@withContext

            dataStore.edit { mutable ->
                prefSections.forEach { section ->
                    val obj = preview.payload.preferences[section.key] ?: return@forEach
                    obj.forEach { (name, element) ->
                        val spec = PortablePreferences.find(name) ?: return@forEach
                        if (spec.section != section) return@forEach
                        writePref(mutable, spec, element as? JsonPrimitive ?: return@forEach)
                    }
                }
            }
        }

    private fun readPref(prefs: Preferences, spec: PrefSpec): JsonPrimitive? =
        try {
            when (spec.type) {
                PrefType.BOOLEAN ->
                    prefs[booleanPreferencesKey(spec.name)]?.let { JsonPrimitive(it) }
                PrefType.INT -> prefs[intPreferencesKey(spec.name)]?.let { JsonPrimitive(it) }
                PrefType.LONG -> prefs[longPreferencesKey(spec.name)]?.let { JsonPrimitive(it) }
                PrefType.STRING ->
                    prefs[stringPreferencesKey(spec.name)]?.let { JsonPrimitive(it) }
            }
        } catch (e: ClassCastException) {
            Timber.e(e, "Preference ${spec.name} is not a ${spec.type}; skipped from backup")
            null
        }

    private fun writePref(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        spec: PrefSpec,
        value: JsonPrimitive,
    ) {
        try {
            when (spec.type) {
                PrefType.BOOLEAN ->
                    value.booleanOrNull?.let { prefs[booleanPreferencesKey(spec.name)] = it }
                PrefType.INT -> value.intOrNull?.let { prefs[intPreferencesKey(spec.name)] = it }
                PrefType.LONG ->
                    value.longOrNull?.let { prefs[longPreferencesKey(spec.name)] = it }
                PrefType.STRING ->
                    value.contentOrNull?.let { prefs[stringPreferencesKey(spec.name)] = it }
            }
        } catch (e: ClassCastException) {
            Timber.e(e, "Preference ${spec.name} is not a ${spec.type}; skipped from import")
        }
    }
}
