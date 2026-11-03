package com.makd.afinity.ui.admin.lyrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.LyricsContent
import com.makd.afinity.data.models.admin.RemoteLyrics
import com.makd.afinity.data.models.admin.TrackLyrics
import com.makd.afinity.ui.admin.components.AdminCardShape
import com.makd.afinity.ui.admin.components.AdminStateMessage
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityBadge
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.DeleteItemDialog
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.formatClock
import com.makd.afinity.ui.components.playerClearance
import com.makd.afinity.ui.components.rememberAfinitySheetState

private const val COLLAPSED_LINES = 8
private val TimestampMinWidth = 44.dp
private val ANY_FILE = arrayOf("*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLyricsScreen(onNavigateUp: () -> Unit, viewModel: EditLyricsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewResult by remember { mutableStateOf<RemoteLyrics?>(null) }
    var showAllLines by rememberSaveable { mutableStateOf(false) }
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::uploadFile)
        }

    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val currentLyrics = uiState.target?.lyrics

    previewResult?.let { result ->
        LyricsPreviewSheet(
            result = result,
            enabled = !uiState.working,
            onUse = {
                previewResult = null
                viewModel.useRemote(result)
            },
            onDismiss = { previewResult = null },
        )
    }

    uiState.pending?.let {
        ReplaceLyricsDialog(
            sidecarFileName = currentLyrics?.sidecarFileName,
            working = uiState.working,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
    }

    if (uiState.confirmingDelete && currentLyrics != null) {
        DeleteItemDialog(
            targetName = uiState.target?.trackName.orEmpty(),
            message =
                currentLyrics.sidecarFileName?.let {
                    stringResource(R.string.lyrics_delete_message_sidecar, it)
                } ?: stringResource(R.string.lyrics_delete_message_server),
            deleting = uiState.working,
            errorRes = null,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.lyrics_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                backgroundOpacity = backgroundOpacity,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
    ) { padding ->
        when {
            uiState.loading -> FullScreenLoading(modifier = Modifier.padding(padding))
            uiState.loadFailed ->
                FullScreenError(
                    title = stringResource(R.string.lyrics_load_failed_title),
                    message = stringResource(R.string.lyrics_load_failed_message),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retryLoad,
                    modifier = Modifier.padding(padding),
                )
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp + playerClearance(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.working) {
                        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    }

                    uiState.target?.let { target ->
                        item {
                            Text(
                                text =
                                    listOfNotNull(target.trackName, target.artist)
                                        .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                    item { SectionTitle(stringResource(R.string.lyrics_current)) }

                    item {
                        if (currentLyrics != null) {
                            CurrentLyricsCard(
                                lyrics = currentLyrics,
                                showAll = showAllLines,
                                onToggleShowAll = { showAllLines = !showAllLines },
                            )
                        } else {
                            NoLyricsCard()
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { pickFile.launch(ANY_FILE) },
                                enabled = !uiState.working,
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_file_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.lyrics_upload))
                            }
                            if (currentLyrics != null) {
                                TextButton(
                                    onClick = viewModel::requestDelete,
                                    enabled = !uiState.working,
                                    colors =
                                        ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.lyrics_delete))
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle(
                                text = stringResource(R.string.lyrics_find),
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = viewModel::search,
                                enabled = !uiState.searching && !uiState.working,
                            ) {
                                if (uiState.searching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = LocalContentColor.current,
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.ic_search),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (uiState.hasSearched) R.string.lyrics_search_again
                                        else R.string.lyrics_search
                                    )
                                )
                            }
                        }
                    }

                    when {
                        uiState.results.isNotEmpty() ->
                            items(uiState.results, key = { it.id }) { result ->
                                RemoteLyricsCard(
                                    result = result,
                                    enabled = !uiState.working,
                                    onPreview = { previewResult = result },
                                    onUse = { viewModel.useRemote(result) },
                                )
                            }
                        uiState.searching -> Unit
                        uiState.searchFailed ->
                            item {
                                AdminStateMessage(
                                    iconRes = R.drawable.ic_cloud_off,
                                    title = stringResource(R.string.lyrics_search_failed_title),
                                    message = stringResource(R.string.lyrics_search_failed_message),
                                    actionText = stringResource(R.string.action_retry),
                                    onAction = viewModel::search,
                                )
                            }
                        uiState.hasSearched ->
                            item {
                                AdminStateMessage(
                                    iconRes = R.drawable.ic_search,
                                    title = stringResource(R.string.lyrics_no_results_title),
                                    message = stringResource(R.string.lyrics_no_results_message),
                                )
                            }
                        else ->
                            item {
                                Text(
                                    text = stringResource(R.string.lyrics_search_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                    }
                }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SyncBadge(synced: Boolean) {
    AfinityBadge(
        text =
            stringResource(
                if (synced) R.string.lyrics_badge_synced else R.string.lyrics_badge_plain
            ),
        containerColor =
            if (synced) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (synced) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LineCountBadge(count: Int) {
    AfinityBadge(
        text = stringResource(R.string.lyrics_line_count, count),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CurrentLyricsCard(lyrics: TrackLyrics, showAll: Boolean, onToggleShowAll: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AdminCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyncBadge(synced = lyrics.content.synced)
            Text(
                text =
                    lyrics.sidecarFileName?.let {
                        stringResource(R.string.lyrics_source_sidecar, it)
                    } ?: stringResource(R.string.lyrics_source_server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        LyricLines(content = lyrics.content, limit = if (showAll) null else COLLAPSED_LINES)
        if (lyrics.content.lines.size > COLLAPSED_LINES) {
            TextButton(onClick = onToggleShowAll, modifier = Modifier.align(Alignment.End)) {
                Text(
                    stringResource(
                        if (showAll) R.string.lyrics_show_less else R.string.lyrics_show_all
                    )
                )
            }
        }
    }
}

@Composable
private fun LyricLines(content: LyricsContent, limit: Int?) {
    val lines = if (limit == null) content.lines else content.lines.take(limit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (content.synced) {
                    Text(
                        text = line.startMs?.let(::formatClock).orEmpty(),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = TimestampMinWidth),
                    )
                }
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NoLyricsCard() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AdminCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_none_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.lyrics_none_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteLyricsCard(
    result: RemoteLyrics,
    enabled: Boolean,
    onPreview: () -> Unit,
    onUse: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AdminCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = result.providerName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            result.lengthMs?.let { length ->
                Text(
                    text = formatClock(length),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        val subtitle = listOfNotNull(result.artist, result.album).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SyncBadge(synced = result.synced)
            LineCountBadge(count = result.content.lineCount)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onPreview) {
                Icon(
                    painterResource(R.drawable.ic_eye),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.lyrics_preview))
            }
            Button(onClick = onUse, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.lyrics_use))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LyricsPreviewSheet(
    result: RemoteLyrics,
    enabled: Boolean,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    AfinityModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberAfinitySheetState(),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = result.title ?: result.providerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        listOfNotNull(result.providerName, result.artist, result.album)
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SyncBadge(synced = result.synced)
                    LineCountBadge(count = result.content.lineCount)
                }
            }
            LyricLines(content = result.content, limit = null)
            Button(
                onClick = onUse,
                enabled = enabled,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.lyrics_use))
            }
        }
    }
}

@Composable
private fun ReplaceLyricsDialog(
    sidecarFileName: String?,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentWorking by rememberUpdatedState(working)
    AlertDialog(
        onDismissRequest = { if (!currentWorking) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                stringResource(R.string.lyrics_replace_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text =
                        sidecarFileName?.let {
                            stringResource(R.string.lyrics_replace_message_sidecar, it)
                        } ?: stringResource(R.string.lyrics_replace_message_server),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !working,
                colors =
                    if (sidecarFileName != null) {
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    } else ButtonDefaults.textButtonColors(),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.lyrics_replace_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
