package com.makd.afinity.data.repository.settings

import com.makd.afinity.data.models.SettingsSection

internal enum class PrefType {
    BOOLEAN,
    INT,
    LONG,
    STRING,
}

internal data class PrefSpec(
    val name: String,
    val type: PrefType,
    val section: SettingsSection,
)

internal object PortablePreferences {

    private val EXCLUDED =
        setOf(
            "current_server_id",
            "current_user_id",
            "remember_login",
            "onboarding_first_run_done",
            "notification_permission_declined",
            "offline_mode",
            "last_sync_time",
            "last_update_check",
            "last_cache_invalidated",
            "download_storage_volume_id",
            "max_bitrate",
            "cast_max_bitrate",
            "buffer_size_mb",
            "image_cache_size_mb",
            "ass_render_mode",
            "mpv_hw_dec",
            "mpv_gpu_api",
            "mpv_hdr_output",
            "mpv_tone_mapping",
            "mpv_hdr_peak_detection",
            "mpv_video_output",
            "mpv_audio_output",
        )

    val specs: List<PrefSpec> =
        listOf(
            spec("theme_mode", PrefType.STRING, SettingsSection.APPEARANCE),
            spec("app_font", PrefType.STRING, SettingsSection.APPEARANCE),
            spec("dynamic_colors", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("grid_layout", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("episode_layout", PrefType.STRING, SettingsSection.APPEARANCE),
            spec("navigation_drawer_enabled", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("libraries_in_drawer", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("show_ratings", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("combine_library_sections", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("home_sort_by_date_added", PrefType.BOOLEAN, SettingsSection.APPEARANCE),
            spec("auto_play", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("use_exo_player", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("skip_intro_mode", PrefType.STRING, SettingsSection.PLAYBACK),
            spec("skip_outro_mode", PrefType.STRING, SettingsSection.PLAYBACK),
            spec("video_zoom_mode", PrefType.INT, SettingsSection.PLAYBACK),
            spec("pip_gesture_enabled", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("pip_background_play", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("logo_auto_hide", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("pause_screen_enabled", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("pause_screen_delay_seconds", PrefType.INT, SettingsSection.PLAYBACK),
            spec("chapter_skip_gesture", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("preferred_audio_language", PrefType.STRING, SettingsSection.PLAYBACK),
            spec("preferred_subtitle_language", PrefType.STRING, SettingsSection.PLAYBACK),
            spec("cast_hevc_enabled", PrefType.BOOLEAN, SettingsSection.PLAYBACK),
            spec("subtitle_text_color", PrefType.INT, SettingsSection.SUBTITLES),
            spec("subtitle_text_size", PrefType.STRING, SettingsSection.SUBTITLES),
            spec("subtitle_bold", PrefType.BOOLEAN, SettingsSection.SUBTITLES),
            spec("subtitle_italic", PrefType.BOOLEAN, SettingsSection.SUBTITLES),
            spec("subtitle_outline_style", PrefType.STRING, SettingsSection.SUBTITLES),
            spec("subtitle_outline_color", PrefType.INT, SettingsSection.SUBTITLES),
            spec("subtitle_outline_size", PrefType.STRING, SettingsSection.SUBTITLES),
            spec("subtitle_background_color", PrefType.INT, SettingsSection.SUBTITLES),
            spec("subtitle_window_color", PrefType.INT, SettingsSection.SUBTITLES),
            spec("subtitle_vertical_position", PrefType.STRING, SettingsSection.SUBTITLES),
            spec("subtitle_horizontal_alignment", PrefType.STRING, SettingsSection.SUBTITLES),
            spec("default_sort_by", PrefType.STRING, SettingsSection.LIBRARY),
            spec("sort_descending", PrefType.BOOLEAN, SettingsSection.LIBRARY),
            spec("items_per_page", PrefType.INT, SettingsSection.LIBRARY),
            spec("image_cache_enabled", PrefType.BOOLEAN, SettingsSection.LIBRARY),
            spec("download_wifi_only", PrefType.BOOLEAN, SettingsSection.DOWNLOADS),
            spec("download_quality", PrefType.STRING, SettingsSection.DOWNLOADS),
            spec("max_downloads", PrefType.INT, SettingsSection.DOWNLOADS),
            spec("sync_enabled", PrefType.BOOLEAN, SettingsSection.DOWNLOADS),
            spec("sync_interval", PrefType.INT, SettingsSection.DOWNLOADS),
            spec("crash_reporting", PrefType.BOOLEAN, SettingsSection.PRIVACY),
            spec("usage_analytics", PrefType.BOOLEAN, SettingsSection.PRIVACY),
            spec("update_check_frequency", PrefType.INT, SettingsSection.PRIVACY),
        )

    val bySection: Map<SettingsSection, List<PrefSpec>> = specs.groupBy { it.section }

    fun find(name: String): PrefSpec? = specs.firstOrNull { it.name == name }

    private fun spec(name: String, type: PrefType, section: SettingsSection): PrefSpec {
        require(name !in EXCLUDED) { "$name is not portable and must never be exported" }
        return PrefSpec(name, type, section)
    }
}
