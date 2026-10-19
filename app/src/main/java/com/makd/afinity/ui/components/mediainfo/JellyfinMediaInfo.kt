package com.makd.afinity.ui.components.mediainfo

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.hdrLabel
import com.makd.afinity.data.models.media.isDolbyVision
import com.makd.afinity.ui.player.toLocalizedLanguageName
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

fun buildJellyfinMediaInfo(
    context: Context,
    subtitle: String,
    sources: List<AfinitySource>,
    runtimeTicks: Long,
    showPath: Boolean,
    selectedSourceId: String? = null,
    imageUrl: String? = null,
    album: String? = null,
): MediaInfo {
    val format = MediaInfoFormat(context)
    val versions = sources.mapIndexed { index, source ->
        MediaInfoVersion(
            name =
                source.name.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.media_info_track_fallback_fmt, index + 1),
            badgeSource = source,
            chips = audioOnlyChips(format, source),
            sections =
                listOfNotNull(
                    fileSection(format, source, runtimeTicks, showPath, album),
                    videoSection(format, source),
                    audioSection(format, source),
                    subtitleSection(format, source),
                    extrasSection(format, source),
                ),
        )
    }
    return MediaInfo(
        subtitle = subtitle,
        imageUrl = imageUrl,
        versions = versions,
        initialVersion = sources.indexOfFirst { it.id == selectedSourceId }.coerceAtLeast(0),
    )
}

private fun AfinitySource.streams(type: MediaStreamType) = mediaStreams.filter { it.type == type }

private fun audioOnlyChips(format: MediaInfoFormat, source: AfinitySource): List<String> {
    if (source.streams(MediaStreamType.VIDEO).isNotEmpty()) return emptyList()
    val audio = source.streams(MediaStreamType.AUDIO).firstOrNull() ?: return emptyList()
    val sampleRate = audio.sampleRate
    val depth = audio.bitDepth
    return listOfNotNull(
        when {
            depth != null && sampleRate != null ->
                format.string(
                    R.string.media_info_bit_depth_sample_rate_fmt,
                    depth,
                    format.kiloHertz(sampleRate),
                )
            sampleRate != null ->
                format.string(R.string.media_info_sample_rate_fmt, format.kiloHertz(sampleRate))
            else -> null
        },
        source
            .streams(MediaStreamType.LYRIC)
            .takeIf { it.isNotEmpty() }
            ?.let { format.string(R.string.media_info_label_lyrics) },
    )
}

private fun fileSection(
    format: MediaInfoFormat,
    source: AfinitySource,
    runtimeTicks: Long,
    showPath: Boolean,
    album: String?,
): MediaInfoSection {
    val isAudioOnly = source.streams(MediaStreamType.VIDEO).isEmpty()
    val rows =
        listOfNotNull(
            prettyContainer(source.container)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_container), it)
            },
            source.size
                .takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_size), format.size(it))
                },
            source.bitrate
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_bitrate),
                        format.bitrate(it),
                    )
                },
            runtimeTicks
                .takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_duration),
                        if (isAudioOnly) format.clock(it / 10_000_000.0) else format.runtime(it),
                    )
                },
            album
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_album), it) },
            source.path
                .takeIf { showPath && source.type == AfinitySourceType.REMOTE && it.isNotBlank() }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_path), it, muted = true)
                },
        )
    return MediaInfoSection(title = format.string(R.string.media_info_section_file), rows = rows)
}

private fun videoSection(format: MediaInfoFormat, source: AfinitySource): MediaInfoSection? {
    val video = source.streams(MediaStreamType.VIDEO).firstOrNull() ?: return null
    val rows =
        listOfNotNull(
            codecLine(format, video)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_codec), it)
            },
            resolutionLine(video)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_resolution), it)
            },
            video.frameRate
                ?.takeIf { it > 0f }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_frame_rate),
                        format.string(
                            R.string.media_info_fps_fmt,
                            format.decimal(it.toDouble(), 3),
                        ),
                    )
                },
            video.bitRate
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_bitrate),
                        format.bitrate(it.toLong()),
                    )
                },
            video.bitDepth?.let {
                MediaInfoRow(
                    format.string(R.string.media_info_label_bit_depth),
                    format.string(R.string.media_info_bit_depth_fmt, it),
                )
            },
            rangeLine(format, video)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_range), it)
            },
        )
    return MediaInfoSection(
        title = format.string(R.string.media_info_section_video),
        rows = rows,
        entries = listOfNotNull(colorEntry(format, video)),
    )
}

private fun codecLine(format: MediaInfoFormat, video: AfinityMediaStream): String? {
    val codec = video.codec.takeIf { it.isNotBlank() }?.uppercase() ?: return null
    val profile = video.profile?.takeIf { it.isNotBlank() }
    val level = levelText(format, video)
    val detail =
        when {
            profile != null && level != null -> "$profile @ L$level"
            profile != null -> profile
            level != null -> "L$level"
            else -> null
        }
    return listOfNotNull(codec, detail).joinToString(" · ")
}

private fun levelText(format: MediaInfoFormat, video: AfinityMediaStream): String? {
    val level = video.level?.takeIf { it > 0 } ?: return null
    return when (video.codec.lowercase()) {
        "hevc",
        "h265" -> String.format(java.util.Locale.ROOT, "%.1f", level / 30.0)
        "h264",
        "avc" -> String.format(java.util.Locale.ROOT, "%.1f", level / 10.0)
        else -> format.decimal(level, 1)
    }
}

private fun resolutionLine(video: AfinityMediaStream): String? {
    val width = video.width ?: return null
    val height = video.height ?: return null
    val aspect = video.aspectRatio?.takeIf { it.isNotBlank() && it != "0:1" }
    return listOfNotNull("${width}×${height}", aspect).joinToString(" · ")
}

private fun rangeLine(format: MediaInfoFormat, video: AfinityMediaStream): String? =
    when {
        video.isDolbyVision() ->
            video.videoDoViTitle?.takeIf { it.isNotBlank() }
                ?: format.string(R.string.media_info_group_dolby_vision)
        else ->
            video.hdrLabel()
                ?: format.string(R.string.media_info_sdr).takeIf {
                    video.videoRangeType == VideoRangeType.SDR
                }
    }

private fun dolbyVisionProfile(video: AfinityMediaStream): String? {
    val profile = video.dvProfile ?: return null
    val compatibility = video.dvBlSignalCompatibilityId?.takeIf { it > 0 }
    return if (compatibility != null) "$profile.$compatibility" else profile.toString()
}

private fun colorEntry(format: MediaInfoFormat, video: AfinityMediaStream): MediaInfoEntry? {
    val dolbyVision =
        if (video.dvProfile != null) {
            MediaInfoGroup(
                header = format.string(R.string.media_info_group_dolby_vision),
                rows =
                    listOfNotNull(
                        dolbyVisionProfile(video)?.let {
                            MediaInfoRow(format.string(R.string.media_info_label_profile), it)
                        },
                        video.dvLevel?.let {
                            MediaInfoRow(format.string(R.string.media_info_label_level), "$it")
                        },
                        video.dvVersionMajor?.let { major ->
                            MediaInfoRow(
                                format.string(R.string.media_info_label_version),
                                "$major.${video.dvVersionMinor ?: 0}",
                            )
                        },
                        format.presence(video.rpuPresentFlag)?.let {
                            MediaInfoRow(format.string(R.string.media_info_label_rpu), it)
                        },
                        format.presence(video.elPresentFlag)?.let {
                            MediaInfoRow(
                                format.string(R.string.media_info_label_enhancement_layer),
                                it,
                            )
                        },
                        format.presence(video.blPresentFlag)?.let {
                            MediaInfoRow(format.string(R.string.media_info_label_base_layer), it)
                        },
                    ),
            )
        } else {
            null
        }
    val colorRows =
        listOfNotNull(
            prettyColor(video.colorSpace)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_color_space), it)
            },
            prettyColor(video.colorTransfer)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_color_transfer), it)
            },
            prettyColor(video.colorPrimaries)?.let {
                MediaInfoRow(format.string(R.string.media_info_label_color_primaries), it)
            },
            video.pixelFormat
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_pixel_format), it) },
            MediaInfoRow(
                format.string(R.string.media_info_label_interlaced),
                format.yesNo(video.isInterlaced),
            ),
            video.refFrames
                ?.takeIf { it > 0 }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_ref_frames), "$it") },
        )
    if (dolbyVision == null && colorRows.size <= 1) return null
    val summary =
        listOfNotNull(
                dolbyVisionProfile(video),
                video.dvLevel?.let { "L$it" },
                prettyColor(video.colorPrimaries),
                shortTransfer(video.colorTransfer),
            )
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    return MediaInfoEntry(
        title =
            format.string(
                if (dolbyVision != null) R.string.media_info_dolby_vision_and_color
                else R.string.media_info_color_details
            ),
        summary = summary,
        groups =
            listOfNotNull(
                dolbyVision,
                MediaInfoGroup(
                    header =
                        format.string(R.string.media_info_group_color).takeIf {
                            dolbyVision != null
                        },
                    rows = colorRows,
                ),
            ),
    )
}

private fun languageName(stream: AfinityMediaStream): String? =
    stream.localizedLanguage?.takeIf { it.isNotBlank() }
        ?: stream.language
            .takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
            ?.toLocalizedLanguageName()

private fun trackTitle(format: MediaInfoFormat, stream: AfinityMediaStream, position: Int): String =
    languageName(stream)
        ?: stream.title.takeIf { it.isNotBlank() }
        ?: stream.displayTitle?.takeIf { it.isNotBlank() }
        ?: format.string(R.string.media_info_track_fallback_fmt, position)

private fun trackBadges(format: MediaInfoFormat, stream: AfinityMediaStream) =
    listOfNotNull(
        MediaInfoBadge(format.string(R.string.media_info_badge_default), emphasized = true).takeIf {
            stream.isDefault
        },
        MediaInfoBadge(format.string(R.string.media_info_badge_forced)).takeIf { stream.isForced },
        MediaInfoBadge(format.string(R.string.media_info_badge_sdh)).takeIf {
            stream.isHearingImpaired
        },
        MediaInfoBadge(format.string(R.string.media_info_badge_external)).takeIf {
            stream.isExternal
        },
    )

private fun flagRows(format: MediaInfoFormat, stream: AfinityMediaStream) =
    listOf(
        MediaInfoRow(
            format.string(R.string.media_info_label_default),
            format.yesNo(stream.isDefault),
        ),
        MediaInfoRow(
            format.string(R.string.media_info_label_forced),
            format.yesNo(stream.isForced),
        ),
        MediaInfoRow(
            format.string(R.string.media_info_label_external),
            format.yesNo(stream.isExternal),
        ),
    )

private fun audioSection(format: MediaInfoFormat, source: AfinitySource): MediaInfoSection? {
    val streams =
        source.streams(MediaStreamType.AUDIO).ifEmpty {
            return null
        }
    val entries = streams.mapIndexed { index, stream ->
        val codec = stream.codec.takeIf { it.isNotBlank() }
        val channels = channelLabel(stream.channelLayout, stream.channels)
        MediaInfoEntry(
            title = trackTitle(format, stream, index + 1),
            badges = trackBadges(format, stream),
            summary =
                listOfNotNull(
                        codec?.let { prettyAudioCodec(it, stream.profile) },
                        channels,
                        stream.bitRate?.takeIf { it > 0 }?.let { format.bitrate(it.toLong()) },
                    )
                    .joinToString(" · ")
                    .takeIf { it.isNotBlank() },
            groups =
                listOf(
                    MediaInfoGroup(
                        header = null,
                        rows =
                            listOfNotNull(
                                codec?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_codec),
                                        it.uppercase(),
                                    )
                                },
                                stream.profile
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {
                                        MediaInfoRow(
                                            format.string(R.string.media_info_label_profile),
                                            it,
                                        )
                                    },
                                (stream.channels ?: 0)
                                    .takeIf { it > 0 }
                                    ?.let { count ->
                                        MediaInfoRow(
                                            format.string(R.string.media_info_label_channels),
                                            format.string(
                                                R.string.media_info_channels_fmt,
                                                channels ?: "$count",
                                                count,
                                            ),
                                        )
                                    },
                                stream.bitRate
                                    ?.takeIf { it > 0 }
                                    ?.let {
                                        MediaInfoRow(
                                            format.string(R.string.media_info_label_bitrate),
                                            format.bitrate(it.toLong()),
                                        )
                                    },
                                stream.sampleRate?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_sample_rate),
                                        format.string(
                                            R.string.media_info_sample_rate_fmt,
                                            format.kiloHertz(it),
                                        ),
                                    )
                                },
                                stream.bitDepth?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_bit_depth),
                                        format.string(R.string.media_info_bit_depth_fmt, it),
                                    )
                                },
                                languageName(stream)?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_language),
                                        it,
                                    )
                                },
                            ) + flagRows(format, stream),
                    )
                ),
        )
    }
    return MediaInfoSection(
        title = format.string(R.string.media_info_section_audio),
        count = entries.size.takeIf { it > 1 },
        entries = entries,
    )
}

private fun subtitleSection(format: MediaInfoFormat, source: AfinitySource): MediaInfoSection? {
    val streams =
        source.streams(MediaStreamType.SUBTITLE).ifEmpty {
            return null
        }
    val entries = streams.mapIndexed { index, stream ->
        val codec = stream.codec.takeIf { it.isNotBlank() }?.let(::prettySubtitleCodec)
        val title = stream.title.takeIf { it.isNotBlank() && it != languageName(stream) }
        MediaInfoEntry(
            title = trackTitle(format, stream, index + 1),
            badges = trackBadges(format, stream),
            summary = listOfNotNull(codec, title).joinToString(" · ").takeIf { it.isNotBlank() },
            groups =
                listOf(
                    MediaInfoGroup(
                        header = null,
                        rows =
                            listOfNotNull(
                                codec?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_codec),
                                        it,
                                    )
                                },
                                languageName(stream)?.let {
                                    MediaInfoRow(
                                        format.string(R.string.media_info_label_language),
                                        it,
                                    )
                                },
                                MediaInfoRow(
                                    format.string(R.string.media_info_label_hearing_impaired),
                                    format.yesNo(stream.isHearingImpaired),
                                ),
                            ) + flagRows(format, stream),
                    )
                ),
        )
    }
    return MediaInfoSection(
        title = format.string(R.string.media_info_section_subtitles),
        count = entries.size,
        entries = entries,
        visibleEntries = SUBTITLES_VISIBLE,
    )
}

private fun extrasSection(format: MediaInfoFormat, source: AfinitySource): MediaInfoSection? {
    if (source.streams(MediaStreamType.VIDEO).isNotEmpty()) return null
    val hasArt = source.streams(MediaStreamType.EMBEDDED_IMAGE).isNotEmpty()
    val hasLyrics = source.streams(MediaStreamType.LYRIC).isNotEmpty()
    if (!hasArt && !hasLyrics) return null
    return MediaInfoSection(
        title = format.string(R.string.media_info_section_extras),
        rows =
            listOf(
                MediaInfoRow(
                    format.string(R.string.media_info_label_embedded_artwork),
                    format.yesNo(hasArt),
                ),
                MediaInfoRow(
                    format.string(R.string.media_info_label_lyrics),
                    format.yesNo(hasLyrics),
                ),
            ),
    )
}

private const val SUBTITLES_VISIBLE = 5
