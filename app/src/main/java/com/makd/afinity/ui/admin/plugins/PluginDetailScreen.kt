package com.makd.afinity.ui.admin.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsGroupTitle
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.utils.IntentUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private val VALUE_MAX_WIDTH = 200.dp

@Composable
fun PluginDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    PluginDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onOpenSettings = { url ->
            if (!IntentUtils.openCustomTab(context, url)) {
                scope.launch { snackbarHostState.showSnackbar(commandFailedMessage) }
            }
        },
        onSetEnabled = viewModel::setEnabled,
        onUninstall = viewModel::uninstall,
        onInstall = viewModel::install,
        modifier = modifier,
    )
}

@Composable
private fun PluginDetailContent(
    state: PluginDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUninstallConfirm by rememberSaveable { mutableStateOf(false) }
    var showInstallConfirm by rememberSaveable { mutableStateOf(false) }

    if (showUninstallConfirm) {
        PluginConfirmDialog(
            title = stringResource(R.string.plugin_uninstall_title),
            message = stringResource(R.string.plugin_uninstall_message, state.name),
            confirmLabel = stringResource(R.string.plugin_action_uninstall),
            onConfirm = {
                onUninstall()
                showUninstallConfirm = false
            },
            onDismiss = { showUninstallConfirm = false },
        )
    }

    val installable = state.installable
    if (showInstallConfirm && installable != null) {
        PluginInstallDialog(
            plugin = installable,
            onConfirm = {
                onInstall()
                showInstallConfirm = false
            },
            onDismiss = { showInstallConfirm = false },
        )
    }

    AdminScaffold(
        title = state.name.ifEmpty { stringResource(R.string.title_plugins) },
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.takeIf { it.isLoaded },
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { plugin ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "cover") {
                    PluginCover(
                        imageUrl = plugin.imageUrl,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        cornerRadius = 24.dp,
                        placeholderSize = 56.dp,
                    )
                }
                if (plugin.description.isNotBlank()) {
                    item(key = "description") {
                        Text(
                            text = plugin.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                item(key = "actions") {
                    PluginActions(
                        plugin = plugin,
                        onInstall = { showInstallConfirm = true },
                        onSetEnabled = onSetEnabled,
                        onOpenSettings = onOpenSettings,
                        onUninstall = { showUninstallConfirm = true },
                    )
                }
                item(key = "details") { PluginDetails(plugin = plugin) }
                val versions = plugin.catalog?.versions.orEmpty()
                if (versions.isNotEmpty()) {
                    item(key = "history") {
                        PluginHistory(
                            versions = versions,
                            installedVersion = plugin.installed?.version,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginActions(
    plugin: PluginDetailUiState,
    onInstall: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onOpenSettings: (String) -> Unit,
    onUninstall: () -> Unit,
) {
    val installed = plugin.installed
    val installable = plugin.installable
    val settingsUrl = installed?.settingsUrl
    val rows =
        buildList<@Composable () -> Unit> {
            if (installable != null) {
                add {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_download_arrow),
                        iconTint = MaterialTheme.colorScheme.primary,
                        title =
                            stringResource(
                                if (installable.hasUpdate) R.string.plugin_action_update
                                else R.string.plugin_action_install
                            ),
                        subtitle =
                            when {
                                plugin.isInstalling -> stringResource(R.string.plugin_installing)
                                installable.hasUpdate ->
                                    stringResource(
                                        R.string.plugin_update_versions_fmt,
                                        installable.installedVersion.orEmpty(),
                                        installable.latestVersion,
                                    )
                                else ->
                                    stringResource(
                                        R.string.plugin_catalog_version_fmt,
                                        installable.latestVersion,
                                        installable.repositoryName,
                                    )
                            },
                        onClick = onInstall,
                        enabled = !plugin.isBusy,
                        trailing =
                            if (plugin.isInstalling) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
            if (installed != null && plugin.canToggle) {
                add {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_power),
                        title = stringResource(R.string.plugin_detail_enable),
                        subtitle = installed.pendingChange?.let { stringResource(it.labelRes()) },
                        checked = plugin.isEnabled,
                        onCheckedChange = onSetEnabled,
                        enabled = !plugin.isBusy,
                    )
                }
            }
            if (settingsUrl != null) {
                add {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_settings),
                        title = stringResource(R.string.plugin_action_settings),
                        subtitle = stringResource(R.string.plugin_settings_summary),
                        onClick = { onOpenSettings(settingsUrl) },
                    )
                }
            }
            if (installed != null && installed.canUninstall) {
                add {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_delete),
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.plugin_action_uninstall),
                        onClick = onUninstall,
                        enabled = !plugin.isBusy,
                    )
                }
            }
        }
    if (rows.isEmpty()) return

    SettingsGroup {
        rows.forEachIndexed { index, row ->
            if (index > 0) SettingsDivider()
            row()
        }
    }
}

@Composable
private fun PluginDetails(plugin: PluginDetailUiState) {
    val installed = plugin.installed
    val entry = plugin.catalog
    val rows = buildList {
        if (installed != null) {
            add(
                Triple(
                    R.drawable.ic_circle_check_outline,
                    stringResource(R.string.plugin_detail_status),
                    stringResource(installed.status.labelRes() ?: R.string.plugin_status_active),
                )
            )
        }
        add(
            Triple(
                R.drawable.ic_versions,
                stringResource(R.string.plugin_detail_version),
                installed?.version ?: entry?.latestVersion.orEmpty(),
            )
        )
        if (entry != null && entry.owner.isNotBlank()) {
            add(
                Triple(
                    R.drawable.ic_user,
                    stringResource(R.string.plugin_detail_developer),
                    entry.owner,
                )
            )
        }
        if (entry != null && entry.repositoryName.isNotBlank()) {
            add(
                Triple(
                    R.drawable.ic_database,
                    stringResource(R.string.plugin_detail_repository),
                    entry.repositoryName,
                )
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.plugin_detail_details)) {
        rows.forEachIndexed { index, (iconRes, label, value) ->
            if (index > 0) SettingsDivider()
            SettingsItem(
                icon = painterResource(id = iconRes),
                title = label,
                trailing = {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
                    )
                },
            )
        }
    }
}

@Composable
private fun PluginHistory(versions: List<PluginVersionRow>, installedVersion: String?) {
    var expandedVersion by rememberSaveable { mutableStateOf(versions.firstOrNull()?.version) }
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 32.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SettingsGroupTitle(
                title = stringResource(R.string.plugin_detail_history),
                modifier = Modifier.weight(1f),
            )
            AdminPill(text = versions.size.toString())
        }
        SettingsGroup {
            versions.forEachIndexed { index, version ->
                if (index > 0) SettingsDivider()
                val hasChangelog = version.changelog.isNotEmpty()
                val isExpanded = hasChangelog && expandedVersion == version.version
                SettingsItem(
                    icon =
                        painterResource(
                            id =
                                if (isExpanded) R.drawable.ic_keyboard_arrow_up
                                else R.drawable.ic_keyboard_arrow_down
                        ),
                    iconTint = if (hasChangelog) null else MaterialTheme.colorScheme.outlineVariant,
                    title = version.version,
                    subtitle = version.releasedAt?.let { dateFormatter.format(it) },
                    onClick =
                        if (hasChangelog) {
                            { expandedVersion = if (isExpanded) null else version.version }
                        } else {
                            null
                        },
                    trailing =
                        if (version.version == installedVersion) {
                            {
                                AdminPill(
                                    text = stringResource(R.string.plugin_installed),
                                    emphasised = true,
                                )
                            }
                        } else {
                            null
                        },
                )
                if (isExpanded) {
                    Text(
                        text = version.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
        }
    }
}

private fun PendingPluginChange.labelRes(): Int =
    when (this) {
        PendingPluginChange.ENABLE -> R.string.plugin_pending_enable
        PendingPluginChange.DISABLE -> R.string.plugin_pending_disable
        PendingPluginChange.UNINSTALL -> R.string.plugin_pending_uninstall
    }
