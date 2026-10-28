package com.makd.afinity.ui.admin.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ServerBackupsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerBackupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val unconfirmedMessage = stringResource(R.string.server_backup_no_answer)

    LaunchedEffect(Unit) {
        viewModel.backupUnconfirmed.collect { snackbarHostState.showSnackbar(unconfirmedMessage) }
    }

    ServerBackupsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onCreate = viewModel::create,
        modifier = modifier,
    )
}

@Composable
private fun ServerBackupsContent(
    state: ServerBackupsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onCreate: (metadata: Boolean, subtitles: Boolean, trickplay: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    if (showCreate) {
        CreateBackupDialog(
            onConfirm = { metadata, subtitles, trickplay ->
                onCreate(metadata, subtitles, trickplay)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_server_backups),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.backups,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { backups ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "note") { BackupsNote() }
                item(key = "create") {
                    SettingsGroup {
                        if (state.isCreating) {
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_history),
                                title = stringResource(R.string.server_backup_creating),
                                subtitle = stringResource(R.string.server_backup_creating_summary),
                            )
                            LinearProgressIndicator(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                        .height(4.dp)
                            )
                        } else {
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_add),
                                title = stringResource(R.string.server_backup_create),
                                onClick = { showCreate = true },
                            )
                        }
                    }
                }
                item(key = "backups") {
                    SettingsGroup {
                        if (backups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.server_backups_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                        backups.forEachIndexed { index, backup ->
                            if (index > 0) SettingsDivider()
                            BackupRow(backup = backup)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupsNote() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.server_backups_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupRow(backup: AdminBackupRow) {
    val created =
        remember(backup.created) {
            backup.created.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            )
        }
    val contents =
        listOfNotNull(
                stringResource(R.string.server_backup_database).takeIf { backup.hasDatabase },
                stringResource(R.string.server_backup_metadata).takeIf { backup.hasMetadata },
                stringResource(R.string.server_backup_subtitles).takeIf { backup.hasSubtitles },
                stringResource(R.string.server_backup_trickplay).takeIf { backup.hasTrickplay },
            )
            .joinToString(" · ")
    SettingsItem(
        icon = painterResource(id = R.drawable.ic_database),
        title = created,
        subtitle = contents,
    )
}

@Composable
private fun CreateBackupDialog(
    onConfirm: (metadata: Boolean, subtitles: Boolean, trickplay: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var metadata by rememberSaveable { mutableStateOf(false) }
    var subtitles by rememberSaveable { mutableStateOf(false) }
    var trickplay by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.server_backup_create),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.server_backup_database_always),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.server_backup_metadata),
                    checked = metadata,
                    onCheckedChange = { metadata = it },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.server_backup_subtitles),
                    checked = subtitles,
                    onCheckedChange = { subtitles = it },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.server_backup_trickplay),
                    checked = trickplay,
                    onCheckedChange = { trickplay = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(metadata, subtitles, trickplay) }) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
