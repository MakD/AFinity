package com.makd.afinity.ui.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.SettingsSection
import com.makd.afinity.data.repository.settings.SettingsImportFailure
import com.makd.afinity.data.repository.settings.SettingsImportPreview
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val playerOffset = LocalPlayerOffset.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.backup_title),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        BackupBody(
            viewModel = viewModel,
            modifier =
                Modifier.padding(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + playerOffset + 24.dp,
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        )
        BackupBody(viewModel = viewModel, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BackupBody(viewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val payload = uiState.pendingExport
            if (uri != null && payload != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(payload.toByteArray())
                    }
                }
                    .onFailure { Timber.e(it, "Failed to write settings backup") }
            }
            viewModel.onExportDelivered()
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().decodeToString()
                }
            }
                .onFailure { Timber.e(it, "Failed to read settings backup") }
                .getOrNull()
            if (raw != null) viewModel.previewImport(raw)
        }

    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let { exportLauncher.launch(defaultBackupFileName()) }
    }

    val preview = uiState.preview

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (preview == null) {
            SettingsGroup {
                SettingsItem(
                    title = stringResource(R.string.backup_export),
                    subtitle = stringResource(R.string.backup_export_summary),
                    onClick = { viewModel.prepareExport() },
                )
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.backup_import),
                    subtitle = stringResource(R.string.backup_import_summary),
                    onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
                )
            }

            Text(
                text = stringResource(R.string.backup_excluded_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.backup_import_from_fmt, preview.appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            SettingsGroup {
                preview.sections.forEachIndexed { index, section ->
                    if (index > 0) SettingsDivider()
                    SettingsSwitchItem(
                        title = stringResource(section.labelRes),
                        subtitle = sectionSummary(section, preview),
                        checked = section in uiState.selected,
                        onCheckedChange = { viewModel.toggleSection(section) },
                    )
                }
            }

            if (preview.homePlan?.skipped?.isNotEmpty() == true) {
                Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(
                        text = stringResource(R.string.backup_import_skipped),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    preview.homePlan.skipped.forEach { entry ->
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewModel.dismiss() }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { viewModel.applyImport() },
                    enabled = uiState.selected.isNotEmpty() && !uiState.isBusy,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(text = stringResource(R.string.backup_apply))
                }
            }
        }
    }

    val failure = uiState.failure
    if (failure != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismiss() },
            title = { Text(text = stringResource(R.string.backup_import_title)) },
            text = { Text(text = stringResource(failure.labelRes())) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismiss() }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
        )
    }

    if (uiState.imported) {
        AlertDialog(
            onDismissRequest = { viewModel.dismiss() },
            title = { Text(text = stringResource(R.string.backup_import_done)) },
            text = { Text(text = stringResource(R.string.backup_import_done_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismiss() }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun sectionSummary(section: SettingsSection, preview: SettingsImportPreview): String? =
    if (section == SettingsSection.HOME) {
        val plan = preview.homePlan ?: return null
        stringResource(R.string.backup_section_home_fmt, plan.sections.size)
    } else {
        preview.prefCounts[section]?.let {
            stringResource(R.string.backup_section_settings_fmt, it)
        }
    }

private fun SettingsImportFailure.labelRes(): Int =
    when (this) {
        SettingsImportFailure.NOT_AFINITY_BACKUP -> R.string.backup_error_format
        SettingsImportFailure.NEWER_SCHEMA -> R.string.backup_error_version
        SettingsImportFailure.UNREADABLE -> R.string.backup_error_unreadable
    }

private fun defaultBackupFileName(): String =
    "afinity-settings-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"

private val BACKUP_MIME_TYPES =
    arrayOf("application/json", "text/plain", "application/octet-stream")
