package com.makd.afinity.ui.components.mediainfo

import androidx.compose.runtime.Immutable
import com.makd.afinity.data.models.media.AfinitySource

@Immutable
data class MediaInfo(
    val subtitle: String,
    val imageUrl: String? = null,
    val versions: List<MediaInfoVersion>,
    val initialVersion: Int = 0,
)

@Immutable
data class MediaInfoVersion(
    val name: String,
    val badgeSource: AfinitySource? = null,
    val chips: List<String> = emptyList(),
    val sections: List<MediaInfoSection>,
)

@Immutable
data class MediaInfoSection(
    val title: String,
    val count: Int? = null,
    val rows: List<MediaInfoRow> = emptyList(),
    val entries: List<MediaInfoEntry> = emptyList(),
    val visibleEntries: Int = Int.MAX_VALUE,
)

@Immutable data class MediaInfoRow(val label: String, val value: String, val muted: Boolean = false)

@Immutable
data class MediaInfoEntry(
    val title: String,
    val number: String? = null,
    val badges: List<MediaInfoBadge> = emptyList(),
    val summary: String? = null,
    val groups: List<MediaInfoGroup>,
)

@Immutable data class MediaInfoBadge(val text: String, val emphasized: Boolean = false)

@Immutable data class MediaInfoGroup(val header: String?, val rows: List<MediaInfoRow>)

internal fun MediaInfoSection.copyText(): String = buildString {
    append(title)
    rows.forEach { append('\n').append(it.label).append(": ").append(it.value) }
    entries.forEach { entry ->
        append("\n\n")
        entry.number?.let { append(it).append(". ") }
        append(entry.title)
        entry.summary?.let { append(" — ").append(it) }
        entry.groups.forEach { group ->
            group.header?.let { append('\n').append(it) }
            group.rows.forEach { append('\n').append(it.label).append(": ").append(it.value) }
        }
    }
}
