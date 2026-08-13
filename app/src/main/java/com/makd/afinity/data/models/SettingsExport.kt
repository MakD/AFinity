package com.makd.afinity.data.models

import com.makd.afinity.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class SettingsSection(val key: String, val labelRes: Int) {
    HOME("home", R.string.backup_section_home),
    APPEARANCE("appearance", R.string.backup_section_appearance),
    PLAYBACK("playback", R.string.backup_section_playback),
    SUBTITLES("subtitles", R.string.backup_section_subtitles),
    LIBRARY("library", R.string.backup_section_library),
    DOWNLOADS("downloads", R.string.backup_section_downloads),
    PRIVACY("privacy", R.string.backup_section_privacy);

    companion object {
        fun fromKey(key: String): SettingsSection? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class AfinitySettingsExport(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val home: HomePayload? = null,
    val preferences: Map<String, JsonObject> = emptyMap(),
) {
    companion object {
        const val FORMAT = "afinity.settings"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class HomePayload(
    val hiddenRows: List<String> = emptyList(),
    val discovery: DiscoveryExport = DiscoveryExport(),
    val customSections: List<CustomSectionExport> = emptyList(),
)
