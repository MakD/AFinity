package com.makd.afinity.ui.admin.config

import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ServerConfigSource

object ConfigFormIds {
    const val GENERAL = "general"
    const val LIBRARY_DISPLAY = "library-display"
    const val PLAYBACK = "playback"
    const val TRICKPLAY = "trickplay"
    const val TRANSCODING = "transcoding"
    const val NETWORKING = "networking"
    const val LIBRARY_METADATA = "library-metadata"
    const val NFO = "nfo"
}

private const val BITS_PER_MEGABIT = 1_000_000L
private const val MAX_CONCURRENCY = 64L
private const val MAX_RETENTION_DAYS = 3650L
private const val MAX_RESUME_DURATION_SECONDS = 86_400L
private const val MAX_AUDIOBOOK_MINUTES = 100L
private const val MAX_BITRATE_MEGABITS = 2147.25
private const val MAX_SLOW_RESPONSE_MS = 60_000L
private const val MAX_TRICKPLAY_WIDTH = 3_840L
private const val MAX_PROCESS_THREADS = 64L
private const val METADATA_DOCUMENT = "MetadataConfiguration"

private val GENERAL_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.GENERAL,
        title = R.string.config_general_title,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_server,
                    listOf(
                        TextInputField(
                            "ServerName",
                            R.string.config_server_name,
                            R.string.config_server_name_summary,
                            required = true,
                        ),
                        SwitchField("QuickConnectAvailable", R.string.config_quick_connect),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_performance,
                    listOf(
                        NumberField(
                            "LibraryScanFanoutConcurrency",
                            R.string.config_scan_concurrency,
                            R.string.config_scan_concurrency_summary,
                            max = MAX_CONCURRENCY,
                        ),
                        NumberField(
                            "ParallelImageEncodingLimit",
                            R.string.config_image_encoding_limit,
                            R.string.config_image_encoding_limit_summary,
                            max = MAX_CONCURRENCY,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_logs,
                    listOf(
                        NumberField(
                            "ActivityLogRetentionDays",
                            R.string.config_activity_retention,
                            max = MAX_RETENTION_DAYS,
                        ),
                        NumberField(
                            "LogFileRetentionDays",
                            R.string.config_log_retention,
                            max = MAX_RETENTION_DAYS,
                        ),
                        SwitchField(
                            "EnableSlowResponseWarning",
                            R.string.config_slow_response_warning,
                        ),
                        NumberField(
                            "SlowResponseThresholdMs",
                            R.string.config_slow_response_threshold,
                            max = MAX_SLOW_RESPONSE_MS,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_paths,
                    listOf(
                        TextInputField(
                            "CachePath",
                            R.string.config_cache_path,
                            R.string.config_cache_path_summary,
                        ),
                        TextInputField(
                            "MetadataPath",
                            R.string.config_metadata_path,
                            R.string.config_metadata_path_summary,
                        ),
                    ),
                ),
            ),
    )

private val LIBRARY_DISPLAY_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.LIBRARY_DISPLAY,
        title = R.string.config_library_display_title,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_display,
                    listOf(
                        ChoiceField(
                            "$METADATA_DOCUMENT.UseFileCreationTimeForDateAdded",
                            R.string.config_date_added,
                            listOf(
                                ChoiceOption("false", R.string.config_date_added_import),
                                ChoiceOption("true", R.string.config_date_added_file),
                            ),
                            R.string.config_date_added_summary,
                            boolean = true,
                        ),
                        SwitchField(
                            "EnableFolderView",
                            R.string.config_folder_view,
                            R.string.config_folder_view_summary,
                        ),
                        SwitchField(
                            "DisplaySpecialsWithinSeasons",
                            R.string.config_specials_in_seasons,
                        ),
                        SwitchField(
                            "EnableGroupingMoviesIntoCollections",
                            R.string.config_group_movies,
                            R.string.config_group_movies_summary,
                        ),
                        SwitchField(
                            "EnableGroupingShowsIntoCollections",
                            R.string.config_group_shows,
                            R.string.config_group_shows_summary,
                        ),
                        SwitchField(
                            "EnableExternalContentInSuggestions",
                            R.string.config_external_suggestions,
                            R.string.config_external_suggestions_summary,
                        ),
                    ),
                )
            ),
    )

private val PLAYBACK_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.PLAYBACK,
        title = R.string.config_playback_title,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_resume,
                    listOf(
                        NumberField(
                            "MinResumePct",
                            R.string.config_min_resume_pct,
                            R.string.config_min_resume_pct_summary,
                            max = 100,
                        ),
                        NumberField(
                            "MaxResumePct",
                            R.string.config_max_resume_pct,
                            R.string.config_max_resume_pct_summary,
                            min = 1,
                            max = 100,
                        ),
                        NumberField(
                            "MinResumeDurationSeconds",
                            R.string.config_min_resume_duration,
                            R.string.config_min_resume_duration_summary,
                            max = MAX_RESUME_DURATION_SECONDS,
                        ),
                        NumberField(
                            "MinAudiobookResume",
                            R.string.config_min_audiobook_resume,
                            R.string.config_min_audiobook_resume_summary,
                            max = MAX_AUDIOBOOK_MINUTES,
                        ),
                        NumberField(
                            "MaxAudiobookResume",
                            R.string.config_max_audiobook_resume,
                            R.string.config_max_audiobook_resume_summary,
                            min = 1,
                            max = MAX_AUDIOBOOK_MINUTES,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_streaming,
                    listOf(
                        DecimalField(
                            "RemoteClientBitrateLimit",
                            R.string.config_remote_bitrate_limit,
                            R.string.config_remote_bitrate_limit_summary,
                            max = MAX_BITRATE_MEGABITS,
                            storedPerShown = BITS_PER_MEGABIT,
                            storedAsWholeNumber = true,
                        )
                    ),
                ),
            ),
        orderedPairs = listOf(FieldOrder("MinResumePct", "MaxResumePct")),
    )

private val TRICKPLAY_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.TRICKPLAY,
        title = R.string.config_trickplay_title,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_trickplay_processing,
                    listOf(
                        SwitchField(
                            "TrickplayOptions.EnableHwAcceleration",
                            R.string.config_trickplay_hw_decoding,
                        ),
                        SwitchField(
                            "TrickplayOptions.EnableHwEncoding",
                            R.string.config_trickplay_hw_encoding,
                            R.string.config_trickplay_hw_encoding_summary,
                        ),
                        SwitchField(
                            "TrickplayOptions.EnableKeyFrameOnlyExtraction",
                            R.string.config_trickplay_key_frames,
                            R.string.config_trickplay_key_frames_summary,
                        ),
                        ChoiceField(
                            "TrickplayOptions.ScanBehavior",
                            R.string.config_trickplay_scan_behavior,
                            listOf(
                                ChoiceOption("NonBlocking", R.string.config_scan_non_blocking),
                                ChoiceOption("Blocking", R.string.config_scan_blocking),
                            ),
                            R.string.config_trickplay_scan_behavior_summary,
                        ),
                        ChoiceField(
                            "TrickplayOptions.ProcessPriority",
                            R.string.config_trickplay_priority,
                            listOf(
                                ChoiceOption("High", R.string.config_priority_high),
                                ChoiceOption("AboveNormal", R.string.config_priority_above_normal),
                                ChoiceOption("Normal", R.string.config_priority_normal),
                                ChoiceOption("BelowNormal", R.string.config_priority_below_normal),
                                ChoiceOption("Idle", R.string.config_priority_idle),
                            ),
                            R.string.config_trickplay_priority_summary,
                        ),
                        NumberField(
                            "TrickplayOptions.ProcessThreads",
                            R.string.config_trickplay_threads,
                            R.string.config_trickplay_threads_summary,
                            max = MAX_PROCESS_THREADS,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_trickplay_images,
                    listOf(
                        NumberField(
                            "TrickplayOptions.Interval",
                            R.string.config_trickplay_interval,
                            R.string.config_trickplay_interval_summary,
                            min = 1,
                        ),
                        NumberListField(
                            "TrickplayOptions.WidthResolutions",
                            R.string.config_trickplay_widths,
                            R.string.config_trickplay_widths_summary,
                            max = MAX_TRICKPLAY_WIDTH,
                        ),
                        NumberField(
                            "TrickplayOptions.TileWidth",
                            R.string.config_trickplay_tile_width,
                            R.string.config_trickplay_tile_width_summary,
                            min = 1,
                        ),
                        NumberField(
                            "TrickplayOptions.TileHeight",
                            R.string.config_trickplay_tile_height,
                            R.string.config_trickplay_tile_height_summary,
                            min = 1,
                        ),
                        NumberField(
                            "TrickplayOptions.Qscale",
                            R.string.config_trickplay_qscale,
                            R.string.config_trickplay_qscale_summary,
                            min = 2,
                            max = 31,
                        ),
                        NumberField(
                            "TrickplayOptions.JpegQuality",
                            R.string.config_trickplay_jpeg_quality,
                            R.string.config_trickplay_jpeg_quality_summary,
                            min = 1,
                            max = 100,
                        ),
                    ),
                ),
            ),
    )

private val LIBRARY_METADATA_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.LIBRARY_METADATA,
        title = R.string.config_metadata_title,
        note = R.string.config_metadata_note,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_metadata_language,
                    listOf(
                        ChoiceField(
                            "PreferredMetadataLanguage",
                            R.string.config_metadata_language,
                            emptyList(),
                            optionsSource = ChoiceOptionsSource.CULTURES,
                        ),
                        ChoiceField(
                            "MetadataCountryCode",
                            R.string.config_metadata_country,
                            emptyList(),
                            optionsSource = ChoiceOptionsSource.COUNTRIES,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_chapters,
                    listOf(
                        NumberField(
                            "DummyChapterDuration",
                            R.string.config_chapter_interval,
                            R.string.config_chapter_interval_summary,
                        ),
                        ChoiceField(
                            "ChapterImageResolution",
                            R.string.config_chapter_image_resolution,
                            listOf(
                                ChoiceOption(
                                    "MatchSource",
                                    R.string.config_resolution_match_source,
                                ),
                                ChoiceOption("P2160", text = "2160p"),
                                ChoiceOption("P1440", text = "1440p"),
                                ChoiceOption("P1080", text = "1080p"),
                                ChoiceOption("P720", text = "720p"),
                                ChoiceOption("P480", text = "480p"),
                                ChoiceOption("P360", text = "360p"),
                                ChoiceOption("P240", text = "240p"),
                                ChoiceOption("P144", text = "144p"),
                            ),
                            R.string.config_chapter_image_resolution_summary,
                        ),
                    ),
                ),
            ),
    )

private val NFO_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.NFO,
        title = R.string.config_nfo_title,
        note = R.string.config_nfo_note,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_nfo,
                    listOf(
                        ChoiceField(
                            "UserId",
                            R.string.config_nfo_user,
                            listOf(ChoiceOption("", R.string.config_none)),
                            R.string.config_nfo_user_summary,
                            optionsSource = ChoiceOptionsSource.USERS,
                        ),
                        SwitchField(
                            "SaveImagePathsInNfo",
                            R.string.config_nfo_image_paths,
                            R.string.config_nfo_image_paths_summary,
                        ),
                        SwitchField(
                            "EnablePathSubstitution",
                            R.string.config_nfo_path_substitution,
                            R.string.config_nfo_path_substitution_summary,
                        ),
                        SwitchField(
                            "EnableExtraThumbsDuplication",
                            R.string.config_nfo_extra_thumbs,
                            R.string.config_nfo_extra_thumbs_summary,
                        ),
                    ),
                )
            ),
    )

private val SERVER_CONFIG_FORMS =
    listOf(
            ServerConfigForm(ServerConfigSource.SYSTEM, GENERAL_FORM),
            ServerConfigForm(
                ServerConfigSource.SYSTEM,
                LIBRARY_DISPLAY_FORM,
                nestedSources = mapOf(METADATA_DOCUMENT to ServerConfigSource.METADATA),
            ),
            ServerConfigForm(ServerConfigSource.SYSTEM, LIBRARY_METADATA_FORM),
            ServerConfigForm(ServerConfigSource.NFO, NFO_FORM),
            ServerConfigForm(ServerConfigSource.SYSTEM, PLAYBACK_FORM),
            ServerConfigForm(ServerConfigSource.SYSTEM, TRICKPLAY_FORM),
            ServerConfigForm(ServerConfigSource.ENCODING, TRANSCODING_FORM),
            ServerConfigForm(ServerConfigSource.NETWORK, NETWORKING_FORM),
        )
        .associateBy { it.spec.id }

fun serverConfigForm(id: String): ServerConfigForm? = SERVER_CONFIG_FORMS[id]
