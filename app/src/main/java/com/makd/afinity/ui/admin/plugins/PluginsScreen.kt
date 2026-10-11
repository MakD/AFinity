package com.makd.afinity.ui.admin.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.utils.IntentUtils
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PluginStatus

@Composable
fun PluginsScreen(
    onBackClick: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenRepositories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    PluginsContent(
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
        onUpdate = viewModel::update,
        onRestart = viewModel::restartServer,
        onOpenCatalog = onOpenCatalog,
        onOpenRepositories = onOpenRepositories,
        modifier = modifier,
    )
}

@Composable
private fun PluginsContent(
    state: PluginsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: (url: String) -> Unit,
    onSetEnabled: (plugin: AdminPluginRow, enabled: Boolean) -> Unit,
    onUninstall: (AdminPluginRow) -> Unit,
    onUpdate: (CatalogPluginRow) -> Unit,
    onRestart: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenRepositories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uninstallTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var showRestartConfirm by rememberSaveable { mutableStateOf(false) }

    state.plugins
        ?.firstOrNull { it.key == uninstallTarget }
        ?.let { plugin ->
            ConfirmDialog(
                title = stringResource(R.string.plugin_uninstall_title),
                message = stringResource(R.string.plugin_uninstall_message, plugin.name),
                confirmLabel = stringResource(R.string.plugin_action_uninstall),
                onConfirm = {
                    onUninstall(plugin)
                    uninstallTarget = null
                },
                onDismiss = { uninstallTarget = null },
            )
        }

    if (showRestartConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_restart_server_title),
            message = stringResource(R.string.dialog_restart_server_message, state.serverName),
            confirmLabel = stringResource(R.string.action_restart),
            onConfirm = {
                onRestart()
                showRestartConfirm = false
            },
            onDismiss = { showRestartConfirm = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_plugins),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.plugins,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { plugins ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.restartRequired) {
                    item(key = "restart") {
                        RestartBanner(
                            enabled = !state.isBusy,
                            onRestart = { showRestartConfirm = true },
                        )
                    }
                }
                if (state.updates.isNotEmpty()) {
                    item(key = "updates") {
                        SettingsGroup(title = stringResource(R.string.plugins_updates)) {
                            state.updates.forEachIndexed { index, update ->
                                if (index > 0) SettingsDivider()
                                SettingsItem(
                                    icon = painterResource(id = R.drawable.ic_view_module),
                                    title = update.name,
                                    subtitle =
                                        stringResource(
                                            R.string.plugin_update_versions_fmt,
                                            update.installedVersion.orEmpty(),
                                            update.latestVersion,
                                        ),
                                    trailing = {
                                        FilledTonalButton(
                                            onClick = { onUpdate(update) },
                                            enabled = !state.isBusy,
                                        ) {
                                            Text(stringResource(R.string.plugin_action_update))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "installed") {
                    SettingsGroup(title = stringResource(R.string.plugins_installed)) {
                        plugins.forEachIndexed { index, plugin ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                )
                            }
                            PluginRow(
                                plugin = plugin,
                                actionsEnabled = !state.isBusy,
                                onOpenSettings = onOpenSettings,
                                onSetEnabled = { enabled -> onSetEnabled(plugin, enabled) },
                                onUninstall = { uninstallTarget = plugin.key },
                            )
                        }
                    }
                }
                item(key = "more") {
                    SettingsGroup {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_add),
                            title = stringResource(R.string.plugins_catalog),
                            subtitle = stringResource(R.string.plugins_catalog_summary),
                            onClick = onOpenCatalog,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_database),
                            title = stringResource(R.string.title_plugin_repositories),
                            subtitle = stringResource(R.string.plugin_repositories_summary),
                            onClick = onOpenRepositories,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestartBanner(enabled: Boolean, onRestart: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_restart),
            title = stringResource(R.string.attention_restart_required),
            subtitle = stringResource(R.string.attention_restart_required_summary),
            trailing = {
                TextButton(onClick = onRestart, enabled = enabled) {
                    Text(stringResource(R.string.action_restart))
                }
            },
        )
    }
}

@Composable
private fun PluginRow(
    plugin: AdminPluginRow,
    actionsEnabled: Boolean,
    onOpenSettings: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginIcon(imageUrl = plugin.imageUrl)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = plugin.version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val pendingLabel = plugin.pendingChange?.labelRes()
            if (pendingLabel != null) {
                Text(
                    text = stringResource(pendingLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val statusLabel = plugin.status.labelRes()
        when {
            statusLabel != null ->
                AdminPill(
                    text = stringResource(statusLabel),
                    emphasised = plugin.status == PluginStatus.RESTART,
                )
            plugin.isBuiltIn -> AdminPill(text = stringResource(R.string.plugin_built_in))
        }
        if (plugin.hasActions) {
            PluginMenu(
                plugin = plugin,
                enabled = actionsEnabled,
                onOpenSettings = onOpenSettings,
                onSetEnabled = onSetEnabled,
                onUninstall = onUninstall,
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}

@Composable
private fun PluginMenu(
    plugin: AdminPluginRow,
    enabled: Boolean,
    onOpenSettings: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_dots_vertical),
                contentDescription = stringResource(R.string.cd_plugin_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (plugin.settingsUrl != null) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plugin_action_settings)) },
                    onClick = {
                        expanded = false
                        onOpenSettings(plugin.settingsUrl)
                    },
                )
            }
            if (plugin.canEnable) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plugin_action_enable)) },
                    onClick = {
                        expanded = false
                        onSetEnabled(true)
                    },
                    enabled = enabled,
                )
            }
            if (plugin.canDisable) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plugin_action_disable)) },
                    onClick = {
                        expanded = false
                        onSetEnabled(false)
                    },
                    enabled = enabled,
                )
            }
            if (plugin.canUninstall) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plugin_action_uninstall)) },
                    onClick = {
                        expanded = false
                        onUninstall()
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

private fun PendingPluginChange.labelRes(): Int =
    when (this) {
        PendingPluginChange.ENABLE -> R.string.plugin_pending_enable
        PendingPluginChange.DISABLE -> R.string.plugin_pending_disable
        PendingPluginChange.UNINSTALL -> R.string.plugin_pending_uninstall
    }
