package com.makd.afinity.ui.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.repository.subtitles.SubtitleCandidate
import com.makd.afinity.data.repository.subtitles.SubtitleLanguageCodes
import com.makd.afinity.data.repository.subtitles.SubtitleSearchMode
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.AfinityMenuItem
import com.makd.afinity.ui.components.DisclosureBlock
import com.makd.afinity.ui.components.formatRuntimeSeconds
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.player.SubtitleLanguageEntry
import com.makd.afinity.ui.player.SubtitleOptInReason
import com.makd.afinity.ui.player.SubtitleSearchStage
import com.makd.afinity.ui.player.SubtitleSearchUiState
import com.makd.afinity.ui.player.subtitleLanguageDisplayName
import java.text.NumberFormat

@Composable
fun SubtitleSearchPanel(
    state: SubtitleSearchUiState,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onEnableSubtitleDb: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onLoadAllLanguages: () -> Unit,
    onAdd: (SubtitleCandidate) -> Unit,
    onRemove: (SubtitleCandidate) -> Unit,
    onRetry: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var showAllLanguages by rememberSaveable { mutableStateOf(false) }
    val closeMenu = {
        menuOpen = false
        showAllLanguages = false
    }

    BackHandler { if (menuOpen) closeMenu() else onBack() }

    Box(
        modifier =
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .playerOverlayInsets()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 440.dp),
        ) {
            Box {
                Column(modifier = Modifier.padding(24.dp)) {
                    PanelHeader(
                        state = state,
                        onBack = onBack,
                        onFilter = { menuOpen = true },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    when (state.stage) {
                        SubtitleSearchStage.OPT_IN ->
                            OptInContent(
                                reason = state.optInReason,
                                onEnable = onEnableSubtitleDb,
                                onNotNow = onBack,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        SubtitleSearchStage.LOADING -> LoadingRows()
                        SubtitleSearchStage.RESULTS ->
                            ResultList(
                                state = state,
                                onAdd = onAdd,
                                onRemove = onRemove,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        SubtitleSearchStage.EMPTY ->
                            CenteredMessage(
                                iconRes = R.drawable.ic_search,
                                title =
                                    stringResource(
                                        R.string.subtitle_search_empty_title_fmt,
                                        subtitleLanguageDisplayName(state.language),
                                    ),
                                body =
                                    if (state.showLanguageFilter) {
                                        stringResource(R.string.subtitle_search_empty_hint)
                                    } else {
                                        null
                                    },
                            )
                        SubtitleSearchStage.ERROR ->
                            CenteredMessage(
                                iconRes = R.drawable.ic_cloud_off,
                                title =
                                    stringResource(
                                        if (state.mode?.usesSubtitleDb == true) {
                                            R.string.subtitle_search_error_subtitledb
                                        } else {
                                            R.string.subtitle_search_error_server
                                        }
                                    ),
                                body = stringResource(R.string.subtitle_search_error_hint),
                                actionLabel = stringResource(R.string.action_retry),
                                onAction = onRetry,
                            )
                    }
                    val footer = footerText(state)
                    if (footer != null) {
                        TranscodeReasonLine(text = footer, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (menuOpen) {
                    LanguageMenu(
                        state = state,
                        showAllLanguages = showAllLanguages,
                        onShowAllLanguages = {
                            showAllLanguages = true
                            onLoadAllLanguages()
                        },
                        onHideAllLanguages = { showAllLanguages = false },
                        onSelect = { code ->
                            closeMenu()
                            onSelectLanguage(code)
                        },
                        onDismiss = closeMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(state: SubtitleSearchUiState, onBack: () -> Unit, onFilter: () -> Unit) {
    val optIn = state.stage == SubtitleSearchStage.OPT_IN
    Row(verticalAlignment = Alignment.CenterVertically) {
        AfinityBackButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        if (optIn) R.string.subtitledb_connect_title
                        else R.string.subtitle_search_title
                    ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!optIn) {
                Text(
                    text = headerSubtitle(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val filterVisible =
            state.showLanguageFilter &&
                (state.stage == SubtitleSearchStage.RESULTS ||
                    state.stage == SubtitleSearchStage.EMPTY)
        if (filterVisible) {
            IconButton(onClick = onFilter) {
                Icon(
                    painter =
                        painterResource(
                            if (state.isLanguageFiltered) R.drawable.ic_filter_active
                            else R.drawable.ic_filter
                        ),
                    contentDescription = stringResource(R.string.cd_subtitle_language_filter),
                    tint =
                        if (state.isLanguageFiltered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun headerSubtitle(state: SubtitleSearchUiState): String {
    val fromSubtitleDb = state.mode?.usesSubtitleDb == true
    val parts = buildList {
        add(state.itemTitle)
        if (fromSubtitleDb && state.runtimeTicks > 0L) add(formatRuntimeTicks(state.runtimeTicks))
        if (state.language.isNotBlank()) add(subtitleLanguageDisplayName(state.language))
        add(
            stringResource(
                if (fromSubtitleDb) R.string.subtitle_search_from_subtitledb
                else R.string.subtitle_search_from_server
            )
        )
    }
    return parts.joinToString(" · ")
}

@Composable
private fun footerText(state: SubtitleSearchUiState): String? {
    if (state.addFailed) return stringResource(R.string.subtitle_search_add_failed)
    if (state.stage != SubtitleSearchStage.RESULTS && state.stage != SubtitleSearchStage.EMPTY) {
        return null
    }
    return when (state.mode) {
        SubtitleSearchMode.SERVER,
        SubtitleSearchMode.UPLOAD -> stringResource(R.string.subtitle_search_footer_server)
        SubtitleSearchMode.DEVICE ->
            stringResource(
                if (state.optInReason == SubtitleOptInReason.DOWNLOADED) {
                    R.string.subtitle_search_footer_downloaded
                } else {
                    R.string.subtitle_search_footer_device
                }
            )
        null -> null
    }
}

@Composable
private fun OptInContent(
    reason: SubtitleOptInReason,
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text =
                stringResource(
                    when (reason) {
                        SubtitleOptInReason.NO_PROVIDER -> R.string.subtitledb_blurb_no_provider
                        SubtitleOptInReason.NO_PERMISSION -> R.string.subtitledb_blurb_no_permission
                        SubtitleOptInReason.DOWNLOADED -> R.string.subtitledb_blurb_downloaded
                    }
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DisclosureBlock(
                title = stringResource(R.string.wikidata_connect_sends_title),
                body = stringResource(R.string.subtitledb_sends_body),
                modifier = Modifier.weight(1f),
            )
            DisclosureBlock(
                title = stringResource(R.string.wikidata_connect_never_title),
                body = stringResource(R.string.wikidata_connect_never_body),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.wikidata_scope_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.action_not_now)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onEnable) { Text(stringResource(R.string.subtitledb_connect_action)) }
        }
    }
}

@Composable
private fun ResultList(
    state: SubtitleSearchUiState,
    onAdd: (SubtitleCandidate) -> Unit,
    onRemove: (SubtitleCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state.downloadingId != null
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(state.candidates, key = { it.id }) { candidate ->
            val downloading = state.downloadingId == candidate.id
            val savedHere = state.savedOnDevice.containsKey(candidate.id)
            SubtitleResultRow(
                candidate = candidate,
                fromSubtitleDb = state.mode?.usesSubtitleDb == true,
                savedOnDevice = savedHere,
                downloading = downloading,
                enabled = !busy,
                onClick = { onAdd(candidate) },
                onRemove = { onRemove(candidate) },
            )
        }
    }
}

@Composable
private fun SubtitleResultRow(
    candidate: SubtitleCandidate,
    fromSubtitleDb: Boolean,
    savedOnDevice: Boolean,
    downloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .alpha(if (enabled || downloading) 1f else 0.45f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (downloading) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else Color.Transparent
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.releaseName,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (downloading) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (downloading) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (downloading) stringResource(R.string.subtitle_search_downloading)
                    else candidateDetails(candidate, fromSubtitleDb, savedOnDevice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            downloading -> {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            savedOnDevice ->
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.cd_subtitle_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
        }
    }
}

@Composable
private fun candidateDetails(
    candidate: SubtitleCandidate,
    fromSubtitleDb: Boolean,
    savedOnDevice: Boolean,
): String {
    val lines =
        candidate.lineCount?.let { count ->
            pluralStringResource(
                R.plurals.subtitle_search_lines_fmt,
                count,
                NumberFormat.getIntegerInstance().format(count),
            )
        }
    val parts = buildList {
        if (savedOnDevice) add(stringResource(R.string.player_subtitle_on_device))
        add(candidate.format.uppercase())
        if (fromSubtitleDb) {
            lines?.let { add(it) }
            candidate.durationSeconds?.takeIf { it > 0 }?.let { add(formatRuntimeSeconds(it)) }
        } else {
            candidate.providerName?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (candidate.hearingImpaired) add(stringResource(R.string.subtitle_search_tag_hi))
        if (candidate.aiTranscribed) add(stringResource(R.string.subtitle_search_tag_ai))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun LoadingRows() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    iconRes: Int,
    title: String,
    body: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LanguageMenu(
    state: SubtitleSearchUiState,
    showAllLanguages: Boolean,
    onShowAllLanguages: () -> Unit,
    onHideAllLanguages: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 6.dp,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .widthIn(min = 220.dp, max = 280.dp)
                    .heightIn(max = 300.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
            ) {
                if (showAllLanguages) {
                    AfinityMenuItem(
                        text = stringResource(R.string.subtitle_search_your_languages),
                        iconRes = R.drawable.ic_chevron_left,
                        onClick = onHideAllLanguages,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (state.allLanguagesLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    state.allLanguages.forEach { code ->
                        LanguageMenuRow(
                            entry = SubtitleLanguageEntry(code, null),
                            selected = SubtitleLanguageCodes.sameLanguage(code, state.language),
                            onSelect = onSelect,
                        )
                    }
                } else {
                    val (current, others) =
                        state.languages.partition {
                            SubtitleLanguageCodes.sameLanguage(it.code, state.language)
                        }
                    current.forEach { entry ->
                        LanguageMenuRow(entry = entry, selected = true, onSelect = onSelect)
                    }
                    if (current.isNotEmpty() && others.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    others.forEach { entry ->
                        LanguageMenuRow(entry = entry, selected = false, onSelect = onSelect)
                    }
                    if (state.mode == SubtitleSearchMode.SERVER) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        AfinityMenuItem(
                            text = stringResource(R.string.subtitle_search_all_languages),
                            iconRes = null,
                            trailingText = "›",
                            onClick = onShowAllLanguages,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMenuRow(
    entry: SubtitleLanguageEntry,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val noneText = stringResource(R.string.track_none)
    AfinityMenuItem(
        text = subtitleLanguageDisplayName(entry.code),
        iconRes = if (selected) R.drawable.ic_check else null,
        iconTint = if (selected) MaterialTheme.colorScheme.primary else null,
        enabled = entry.count != 0,
        trailingText =
            when (val count = entry.count) {
                null -> null
                0 -> noneText
                else -> NumberFormat.getIntegerInstance().format(count)
            },
        onClick = { onSelect(entry.code) },
    )
}
