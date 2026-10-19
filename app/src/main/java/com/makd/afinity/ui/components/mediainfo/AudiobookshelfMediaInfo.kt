package com.makd.afinity.ui.components.mediainfo

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AudioFile
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import java.text.DateFormat
import java.util.Date

private data class AbsFile(
    val name: String,
    val format: String?,
    val codec: String?,
    val bitRate: Int?,
    val channels: Int?,
    val channelLayout: String?,
    val timeBase: String?,
    val mimeType: String?,
    val size: Long?,
    val duration: Double?,
    val path: String?,
)

private fun AudioFile.toAbsFile() =
    AbsFile(
        name = metadata.filename,
        format = format,
        codec = codec,
        bitRate = bitRate,
        channels = channels,
        channelLayout = channelLayout,
        timeBase = timeBase,
        mimeType = mimeType,
        size = metadata.size,
        duration = duration,
        path = metadata.path,
    )

private fun AudioTrack.toAbsFile() =
    AbsFile(
        name = metadata?.filename ?: title.orEmpty(),
        format = format,
        codec = codec,
        bitRate = bitRate,
        channels = channels,
        channelLayout = channelLayout,
        timeBase = timeBase,
        mimeType = mimeType,
        size = metadata?.size,
        duration = duration,
        path = metadata?.path,
    )

fun buildAudiobookMediaInfo(
    context: Context,
    item: LibraryItem,
    downloadedTracks: List<AudioTrack>,
    showPath: Boolean,
    imageUrl: String?,
): MediaInfo {
    val format = MediaInfoFormat(context)
    val files =
        item.media.audioFiles
            ?.filter { it.exclude != true }
            ?.sortedBy { it.index ?: Int.MAX_VALUE }
            ?.map { it.toAbsFile() }
            ?.takeIf { it.isNotEmpty() } ?: downloadedTracks.map { it.toAbsFile() }
    val first = files.firstOrNull()
    val totalSize = item.size ?: files.mapNotNull { it.size }.takeIf { it.isNotEmpty() }?.sum()
    val duration =
        item.media.duration ?: files.mapNotNull { it.duration }.takeIf { it.isNotEmpty() }?.sum()

    val bookRows =
        listOfNotNull(
            totalSize
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_total_size),
                        format.size(it),
                    )
                },
            duration
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_duration),
                        format.runtime((it * 10_000_000).toLong()),
                    )
                },
            files
                .takeIf { it.isNotEmpty() }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_audio_files), "${it.size}")
                },
            item.numFiles?.let {
                MediaInfoRow(format.string(R.string.media_info_label_library_files), "$it")
            },
            item.path
                ?.takeIf { showPath && it.isNotBlank() }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_folder), it, muted = true)
                },
        )

    val fileEntries = files.mapIndexed { index, file ->
        MediaInfoEntry(
            title = file.name,
            number = "${index + 1}",
            summary = fileSummary(format, file),
            groups = listOf(MediaInfoGroup(header = null, rows = fileRows(format, file, showPath))),
        )
    }

    return MediaInfo(
        subtitle = item.media.metadata.title.orEmpty(),
        imageUrl = imageUrl,
        versions =
            listOf(
                MediaInfoVersion(
                    name = item.media.metadata.title.orEmpty(),
                    chips =
                        listOfNotNull(
                            first?.codec?.takeIf { it.isNotBlank() }?.uppercase(),
                            first?.bitRate?.takeIf { it > 0 }?.let { format.bitrate(it.toLong()) },
                            first?.let { channelLabel(it.channelLayout, it.channels) },
                            files.size.takeIf { it > 1 }?.let { format.files(it) },
                        ),
                    sections =
                        listOfNotNull(
                            MediaInfoSection(
                                title = format.string(R.string.media_info_section_book),
                                rows = bookRows,
                            ),
                            fileEntries
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    MediaInfoSection(
                                        title =
                                            format.string(R.string.media_info_section_audio_files),
                                        count = it.size,
                                        entries = it,
                                        visibleEntries = FILES_VISIBLE,
                                    )
                                },
                        ),
                )
            ),
    )
}

fun buildPodcastEpisodeMediaInfo(
    context: Context,
    podcastTitle: String?,
    episode: PodcastEpisode,
    downloadedTrack: AudioTrack?,
    showPath: Boolean,
    imageUrl: String?,
): MediaInfo {
    val format = MediaInfoFormat(context)
    val file = episode.audioFile?.toAbsFile() ?: downloadedTrack?.toAbsFile()
    val fileRows =
        listOfNotNull(
            file
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_file_name), it) },
            file
                ?.format
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_format), it) },
            (file?.size ?: episode.size)
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_size), format.size(it))
                },
            (file?.duration ?: episode.duration)
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_duration),
                        format.clock(it),
                    )
                },
            file
                ?.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_mime_type), it) },
            file
                ?.path
                ?.takeIf { showPath && it.isNotBlank() }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_path), it, muted = true)
                },
        )
    val audioRows = file?.let { audioRows(format, it) }.orEmpty()
    val feedRows =
        listOfNotNull(
            (episode.publishedAt ?: episode.addedAt)?.let {
                MediaInfoRow(
                    format.string(R.string.media_info_label_published),
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)),
                )
            },
            episode.enclosure
                ?.type
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_enclosure_type), it) },
            episode.enclosure
                ?.length
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_enclosure_size),
                        format.size(it),
                    )
                },
        )
    return MediaInfo(
        subtitle = listOfNotNull(episode.title, podcastTitle).joinToString(" · "),
        imageUrl = imageUrl,
        versions =
            listOf(
                MediaInfoVersion(
                    name = episode.title,
                    chips =
                        listOfNotNull(
                            file?.codec?.takeIf { it.isNotBlank() }?.uppercase(),
                            file?.bitRate?.takeIf { it > 0 }?.let { format.bitrate(it.toLong()) },
                            file?.let { channelLabel(it.channelLayout, it.channels) },
                        ),
                    sections =
                        listOfNotNull(
                            MediaInfoSection(
                                title = format.string(R.string.media_info_section_file),
                                rows = fileRows,
                            ),
                            audioRows
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    MediaInfoSection(
                                        title = format.string(R.string.media_info_section_audio),
                                        rows = it,
                                    )
                                },
                            feedRows
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    MediaInfoSection(
                                        title = format.string(R.string.media_info_section_feed),
                                        rows = it,
                                    )
                                },
                        ),
                )
            ),
    )
}

private fun fileSummary(format: MediaInfoFormat, file: AbsFile): String? =
    listOfNotNull(
            file.codec?.takeIf { it.isNotBlank() }?.uppercase(),
            file.bitRate?.takeIf { it > 0 }?.let { format.bitrate(it.toLong()) },
            file.size?.takeIf { it > 0 }?.let { format.size(it) },
            file.duration?.takeIf { it > 0 }?.let { format.clock(it) },
        )
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }

private fun audioRows(format: MediaInfoFormat, file: AbsFile): List<MediaInfoRow> =
    listOfNotNull(
        file.codec
            ?.takeIf { it.isNotBlank() }
            ?.let { MediaInfoRow(format.string(R.string.media_info_label_codec), it.uppercase()) },
        file.channels
            ?.takeIf { it > 0 }
            ?.let { count ->
                MediaInfoRow(
                    format.string(R.string.media_info_label_channels),
                    format.string(
                        R.string.media_info_channels_fmt,
                        channelLabel(file.channelLayout, count) ?: "$count",
                        count,
                    ),
                )
            },
        file.bitRate
            ?.takeIf { it > 0 }
            ?.let {
                MediaInfoRow(
                    format.string(R.string.media_info_label_bitrate),
                    format.bitrate(it.toLong()),
                )
            },
        file.timeBase
            ?.takeIf { it.isNotBlank() }
            ?.let { MediaInfoRow(format.string(R.string.media_info_label_time_base), it) },
    )

private fun fileRows(
    format: MediaInfoFormat,
    file: AbsFile,
    showPath: Boolean,
): List<MediaInfoRow> =
    listOfNotNull(
        file.format
            ?.takeIf { it.isNotBlank() }
            ?.let { MediaInfoRow(format.string(R.string.media_info_label_format), it) }
    ) +
        audioRows(format, file) +
        listOfNotNull(
            file.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaInfoRow(format.string(R.string.media_info_label_mime_type), it) },
            file.size
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_size), format.size(it))
                },
            file.duration
                ?.takeIf { it > 0 }
                ?.let {
                    MediaInfoRow(
                        format.string(R.string.media_info_label_duration),
                        format.clock(it),
                    )
                },
            file.path
                ?.takeIf { showPath && it.isNotBlank() }
                ?.let {
                    MediaInfoRow(format.string(R.string.media_info_label_path), it, muted = true)
                },
        )

private const val FILES_VISIBLE = 5
