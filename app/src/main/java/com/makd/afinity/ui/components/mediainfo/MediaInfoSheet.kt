package com.makd.afinity.ui.components.mediainfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.navigation.LocalSideSheetEnabled
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.ModalSideSheet
import com.makd.afinity.ui.components.isWideWindow
import com.makd.afinity.ui.item.components.shared.MediaStreamBadges
import com.makd.afinity.ui.item.components.shared.VideoMetadataChip

@Composable
fun JellyfinMediaInfoSheet(
    subtitle: String,
    sources: List<AfinitySource>,
    runtimeTicks: Long,
    onDismiss: () -> Unit,
    selectedSourceId: String? = null,
    viewModel: MediaInfoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isAdmin by viewModel.isJellyfinAdmin.collectAsStateWithLifecycle()
    val info =
        remember(subtitle, sources, runtimeTicks, selectedSourceId, isAdmin) {
            buildJellyfinMediaInfo(
                context = context,
                subtitle = subtitle,
                sources = sources,
                runtimeTicks = runtimeTicks,
                showPath = isAdmin,
                selectedSourceId = selectedSourceId,
            )
        }
    MediaInfoSheet(info = info, onDismiss = onDismiss)
}

@Composable
fun TrackMediaInfoSheet(
    track: AfinityTrack,
    onDismiss: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isAdmin by viewModel.isJellyfinAdmin.collectAsStateWithLifecycle()
    val state by viewModel.sources.collectAsStateWithLifecycle()
    LaunchedEffect(track.id) { viewModel.loadSources(track.id) }
    val subtitle = listOfNotNull(track.name, track.artist ?: track.artists.firstOrNull())
    val imageUrl = track.images.primary?.toString()

    when (val current = state) {
        is MediaInfoSources.Ready -> {
            val info =
                remember(current, isAdmin, track) {
                    buildJellyfinMediaInfo(
                        context = context,
                        subtitle = subtitle.joinToString(" · "),
                        sources = current.sources,
                        runtimeTicks = track.runtimeTicks,
                        showPath = isAdmin,
                        imageUrl = imageUrl,
                        album = track.album,
                    )
                }
            MediaInfoSheet(info = info, onDismiss = onDismiss)
        }
        MediaInfoSources.Loading ->
            MediaInfoStatusSheet(
                subtitle = subtitle.joinToString(" · "),
                imageUrl = imageUrl,
                onDismiss = onDismiss,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        MediaInfoSources.Failed ->
            MediaInfoStatusSheet(
                subtitle = subtitle.joinToString(" · "),
                imageUrl = imageUrl,
                onDismiss = onDismiss,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.media_info_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { viewModel.loadSources(track.id) }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
    }
}

@Composable
fun AudiobookMediaInfoSheet(
    item: LibraryItem,
    imageUrl: String?,
    onDismiss: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isAdmin by viewModel.isAudiobookshelfAdmin.collectAsStateWithLifecycle()
    val tracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    LaunchedEffect(item.id) { viewModel.loadAudiobookshelf(item.id, null) }
    val info =
        remember(item, tracks, isAdmin, imageUrl) {
            buildAudiobookMediaInfo(
                context = context,
                item = item,
                downloadedTracks = tracks,
                showPath = isAdmin,
                imageUrl = imageUrl,
            )
        }
    MediaInfoSheet(info = info, onDismiss = onDismiss)
}

@Composable
fun PodcastEpisodeMediaInfoSheet(
    item: LibraryItem,
    episode: PodcastEpisode,
    imageUrl: String?,
    onDismiss: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isAdmin by viewModel.isAudiobookshelfAdmin.collectAsStateWithLifecycle()
    val tracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    LaunchedEffect(item.id, episode.id) { viewModel.loadAudiobookshelf(item.id, episode.id) }
    val info =
        remember(item, episode, tracks, isAdmin, imageUrl) {
            buildPodcastEpisodeMediaInfo(
                context = context,
                podcastTitle = item.media.metadata.title,
                episode = episode,
                downloadedTrack = tracks.firstOrNull(),
                showPath = isAdmin,
                imageUrl = imageUrl,
            )
        }
    MediaInfoSheet(info = info, onDismiss = onDismiss)
}

@Composable
fun MediaInfoSheet(info: MediaInfo, onDismiss: () -> Unit) {
    var versionIndex by
        rememberSaveable(info.versions.size) {
            mutableIntStateOf(
                info.initialVersion.coerceIn(0, (info.versions.size - 1).coerceAtLeast(0))
            )
        }
    val version = info.versions.getOrNull(versionIndex)
    var expanded by remember(versionIndex) { mutableStateOf(emptySet<String>()) }
    var showAll by remember(versionIndex) { mutableStateOf(emptySet<Int>()) }

    MediaInfoContainer(onDismiss = onDismiss) { containerModifier ->
        LazyColumn(
            modifier = containerModifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                MediaInfoHeader(subtitle = info.subtitle, imageUrl = info.imageUrl)
            }
            if (info.versions.size > 1 && version != null) {
                item(key = "versions") {
                    VersionPicker(
                        versions = info.versions,
                        selected = versionIndex,
                        onSelect = { versionIndex = it },
                    )
                }
            }
            if (version != null && (version.badgeSource != null || version.chips.isNotEmpty())) {
                item(key = "chips") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        version.badgeSource?.let { MediaStreamBadges(source = it) }
                        version.chips.forEach { VideoMetadataChip(text = it) }
                    }
                }
            }
            version?.sections?.let { sections ->
                itemsIndexed(sections, key = { index, _ -> "section_${versionIndex}_$index" }) {
                    index,
                    section ->
                    SectionCard(
                        section = section,
                        expandedKeys = expanded,
                        keyPrefix = "$index:",
                        showAll = index in showAll,
                        onToggle = { key ->
                            expanded = if (key in expanded) expanded - key else expanded + key
                        },
                        onShowAll = { showAll = showAll + index },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoStatusSheet(
    subtitle: String,
    imageUrl: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    MediaInfoContainer(onDismiss = onDismiss) { containerModifier ->
        Column(
            modifier =
                containerModifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MediaInfoHeader(subtitle = subtitle, imageUrl = imageUrl)
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MediaInfoContainer(onDismiss: () -> Unit, content: @Composable (Modifier) -> Unit) {
    if (LocalSideSheetEnabled.current && isWideWindow()) {
        ModalSideSheet(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.media_info_title),
        ) {
            content(
                Modifier.statusBarsPadding()
                    .navigationBarsPadding()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(top = 24.dp)
            )
        }
    } else {
        AfinityModalBottomSheet(onDismissRequest = onDismiss) { content(Modifier) }
    }
}

@Composable
private fun MediaInfoHeader(subtitle: String, imageUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (imageUrl != null) {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                targetWidth = 56.dp,
                targetHeight = 56.dp,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.padding(end = 14.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.media_info_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VersionPicker(
    versions: List<MediaInfoVersion>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = versions[selected].name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            versions.forEachIndexed { index, version ->
                DropdownMenuItem(
                    text = { Text(version.name) },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: MediaInfoSection,
    expandedKeys: Set<String>,
    keyPrefix: String,
    showAll: Boolean,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.media_info_copied)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(bottom = if (section.entries.isEmpty()) 8.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                section.count?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { copy(context, section.title, section.copyText(), copiedText) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clipboard),
                        contentDescription =
                            stringResource(R.string.media_info_cd_copy_fmt, section.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            section.rows.forEach { InfoRow(it) }

            val visible =
                if (showAll) section.entries else section.entries.take(section.visibleEntries)
            visible.forEachIndexed { index, entry ->
                val key = "$keyPrefix$index"
                EntryRow(
                    entry = entry,
                    expanded = key in expandedKeys,
                    showDivider = index > 0 || section.rows.isNotEmpty(),
                    onClick = { onToggle(key) },
                )
            }
            if (visible.size < section.entries.size) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.media_info_show_all_fmt, section.entries.size))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(row: MediaInfoRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (row.muted) FontWeight.Normal else FontWeight.SemiBold,
            color =
                if (row.muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EntryRow(
    entry: MediaInfoEntry,
    expanded: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entry.number?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                entry.badges.forEach { EntryBadge(it) }
            }
            entry.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painter =
                painterResource(
                    if (expanded) R.drawable.ic_keyboard_arrow_up
                    else R.drawable.ic_keyboard_arrow_down
                ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
    if (expanded) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 6.dp)
        ) {
            entry.groups.forEach { group ->
                group.header?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    )
                }
                group.rows.forEach { InfoRow(it) }
            }
        }
    }
}

@Composable
private fun EntryBadge(badge: MediaInfoBadge) {
    Text(
        text = badge.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color =
            if (badge.emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.background(
                    if (badge.emphasized) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun copy(context: Context, label: String, text: String, confirmation: String) {
    val manager = context.getSystemService<ClipboardManager>() ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
    }
}
