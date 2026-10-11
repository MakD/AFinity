package com.makd.afinity.ui.admin.libraries

import com.makd.afinity.R
import com.makd.afinity.ui.admin.config.ChoiceField
import com.makd.afinity.ui.admin.config.ChoiceOption
import com.makd.afinity.ui.admin.config.ChoiceOptionsSource
import com.makd.afinity.ui.admin.config.ConfigField
import com.makd.afinity.ui.admin.config.ConfigFormSpec
import com.makd.afinity.ui.admin.config.ConfigSection
import com.makd.afinity.ui.admin.config.FieldCondition
import com.makd.afinity.ui.admin.config.SwitchField
import com.makd.afinity.ui.admin.config.TextInputField
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val FORM_ID = "library-options"
private const val TYPE_MOVIES = "movies"
private const val TYPE_SHOWS = "tvshows"
private const val TYPE_MUSIC = "music"
private const val TYPE_MUSIC_VIDEOS = "musicvideos"
private const val TYPE_HOME_VIDEOS = "homevideos"
private const val TYPE_PHOTOS = "photos"
private const val TYPE_MIXED = "mixed"

internal fun newLibraryDefaults(): JsonObject = buildJsonObject {
    put("Enabled", true)
    put("EnablePhotos", true)
    put("EnableRealtimeMonitor", true)
    put("EnableLUFSScan", true)
    put("SaveLocalMetadata", false)
    put("EnableAutomaticSeriesGrouping", false)
    put("AutomaticRefreshIntervalDays", 0)
    put("PreferredMetadataLanguage", "")
    put("MetadataCountryCode", "")
    put("SeasonZeroDisplayName", "Specials")
    put("EnableEmbeddedTitles", false)
    put("EnableEmbeddedExtrasTitles", false)
    put("EnableEmbeddedEpisodeInfos", false)
    put("AutomaticallyAddToCollection", false)
    put("EnableTrickplayImageExtraction", false)
    put("ExtractTrickplayImagesDuringLibraryScan", false)
    put("SaveTrickplayWithMedia", false)
    put("EnableChapterImageExtraction", false)
    put("ExtractChapterImagesDuringLibraryScan", false)
    put("AllowEmbeddedSubtitles", "AllowAll")
    put("RequirePerfectSubtitleMatch", true)
    put("SkipSubtitlesIfAudioTrackMatches", false)
    put("SkipSubtitlesIfEmbeddedSubtitlesPresent", false)
    put("SaveSubtitlesWithMedia", true)
    put("SaveLyricsWithMedia", false)
    put("PreferNonstandardArtistsTag", false)
    put("UseCustomTagDelimiters", false)
}

private val CHAPTER_TYPES = setOf(TYPE_HOME_VIDEOS, TYPE_MOVIES, TYPE_MUSIC_VIDEOS, TYPE_SHOWS)
private val SUBTITLE_TYPES = setOf(TYPE_SHOWS, TYPE_MOVIES, TYPE_MUSIC_VIDEOS, TYPE_MIXED)
private val NO_EMBEDDED_TITLE_TYPES = setOf("books", "boxsets", "playlists", TYPE_MUSIC)

internal fun libraryOptionsForm(collectionType: String?): ConfigFormSpec {
    val type = collectionType?.lowercase()
    val isMixed = type == null || type == TYPE_MIXED
    val hasChapters = type == null || type in CHAPTER_TYPES
    val hasSubtitles = type == null || type in SUBTITLE_TYPES
    val hasEmbeddedTitles = type !in NO_EMBEDDED_TITLE_TYPES

    val library = buildList {
        add(SwitchField("Enabled", R.string.library_opt_enabled))
        add(
            SwitchField(
                "EnableRealtimeMonitor",
                R.string.library_opt_realtime_monitor,
                R.string.library_opt_realtime_monitor_summary,
            )
        )
        if (type == TYPE_HOME_VIDEOS || type == TYPE_PHOTOS) {
            add(SwitchField("EnablePhotos", R.string.library_opt_photos))
        }
        add(
            ChoiceField(
                "AutomaticRefreshIntervalDays",
                R.string.library_opt_refresh_interval,
                listOf(
                    ChoiceOption("0", R.string.library_opt_refresh_never),
                    ChoiceOption("30", R.string.library_opt_refresh_30),
                    ChoiceOption("60", R.string.library_opt_refresh_60),
                    ChoiceOption("90", R.string.library_opt_refresh_90),
                ),
                numeric = true,
            )
        )
        add(
            SwitchField(
                "SaveLocalMetadata",
                R.string.library_opt_save_local,
                R.string.library_opt_save_local_summary,
            )
        )
    }

    val metadata = buildList {
        add(
            ChoiceField(
                "PreferredMetadataLanguage",
                R.string.library_opt_language,
                listOf(ChoiceOption("", R.string.library_opt_server_default)),
                optionsSource = ChoiceOptionsSource.CULTURES,
            )
        )
        add(
            ChoiceField(
                "MetadataCountryCode",
                R.string.library_opt_country,
                listOf(ChoiceOption("", R.string.library_opt_server_default)),
                optionsSource = ChoiceOptionsSource.COUNTRIES,
            )
        )
        if (type == TYPE_SHOWS) {
            add(
                TextInputField(
                    "SeasonZeroDisplayName",
                    R.string.library_opt_season_zero,
                    required = true,
                )
            )
            add(SwitchField("EnableAutomaticSeriesGrouping", R.string.library_opt_group_series))
            add(SwitchField("EnableEmbeddedEpisodeInfos", R.string.library_opt_embedded_episode))
        }
        if (hasEmbeddedTitles) {
            add(SwitchField("EnableEmbeddedTitles", R.string.library_opt_embedded_titles))
            add(
                SwitchField(
                    "EnableEmbeddedExtrasTitles",
                    R.string.library_opt_embedded_extras_titles,
                    visibleWhen = FieldCondition("EnableEmbeddedTitles", setOf("true")),
                )
            )
        }
        if (type == TYPE_MOVIES || isMixed) {
            add(
                SwitchField(
                    "AutomaticallyAddToCollection",
                    R.string.library_opt_add_to_collection,
                    R.string.library_opt_add_to_collection_summary,
                )
            )
        }
    }

    val trickplay =
        listOf<ConfigField>(
            SwitchField("EnableTrickplayImageExtraction", R.string.library_opt_trickplay),
            SwitchField(
                "ExtractTrickplayImagesDuringLibraryScan",
                R.string.library_opt_during_scan,
            ),
            SwitchField("SaveTrickplayWithMedia", R.string.library_opt_trickplay_with_media),
        )

    val chapters =
        listOf<ConfigField>(
            SwitchField("EnableChapterImageExtraction", R.string.library_opt_chapter_images),
            SwitchField(
                "ExtractChapterImagesDuringLibraryScan",
                R.string.library_opt_during_scan,
            ),
        )

    val subtitles =
        listOf<ConfigField>(
            ChoiceField(
                "AllowEmbeddedSubtitles",
                R.string.library_opt_embedded_subtitles,
                listOf(
                    ChoiceOption("AllowAll", R.string.library_opt_embedded_subtitles_all),
                    ChoiceOption("AllowText", R.string.library_opt_embedded_subtitles_text),
                    ChoiceOption("AllowImage", R.string.library_opt_embedded_subtitles_image),
                    ChoiceOption("AllowNone", R.string.library_opt_embedded_subtitles_none),
                ),
            ),
            SwitchField(
                "RequirePerfectSubtitleMatch",
                R.string.library_opt_perfect_match,
                R.string.library_opt_perfect_match_summary,
            ),
            SwitchField(
                "SkipSubtitlesIfAudioTrackMatches",
                R.string.library_opt_skip_if_audio_matches,
            ),
            SwitchField(
                "SkipSubtitlesIfEmbeddedSubtitlesPresent",
                R.string.library_opt_skip_if_embedded,
            ),
            SwitchField("SaveSubtitlesWithMedia", R.string.library_opt_subtitles_with_media),
        )

    val music =
        listOf<ConfigField>(
            SwitchField(
                "EnableLUFSScan",
                R.string.library_opt_lufs,
                R.string.library_opt_lufs_summary,
            ),
            SwitchField("SaveLyricsWithMedia", R.string.library_opt_lyrics_with_media),
            SwitchField("PreferNonstandardArtistsTag", R.string.library_opt_artists_tag),
            SwitchField("UseCustomTagDelimiters", R.string.library_opt_tag_delimiters),
        )

    val sections = buildList {
        add(ConfigSection(R.string.library_opt_section_library, library))
        add(ConfigSection(R.string.library_opt_section_metadata, metadata))
        if (hasChapters) {
            add(ConfigSection(R.string.library_opt_section_trickplay, trickplay))
            add(ConfigSection(R.string.library_opt_section_chapters, chapters))
        }
        if (hasSubtitles) add(ConfigSection(R.string.library_opt_section_subtitles, subtitles))
        if (type == TYPE_MUSIC) add(ConfigSection(R.string.library_opt_section_music, music))
    }

    return ConfigFormSpec(
        id = FORM_ID,
        title = R.string.library_options_title,
        sections = sections,
    )
}
