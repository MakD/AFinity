package com.makd.afinity.ui.admin.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup

private val MAX_DIALOG_TEXT_HEIGHT = 320.dp

@Composable
fun PluginCatalogScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    PluginCatalogContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onQueryChange = viewModel::setQuery,
        onInstall = viewModel::install,
        modifier = modifier,
    )
}

@Composable
private fun PluginCatalogContent(
    state: PluginCatalogUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onInstall: (CatalogPluginRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var installTarget by rememberSaveable { mutableStateOf<String?>(null) }

    state.sections
        ?.flatMap { it.plugins }
        ?.firstOrNull { it.id.toString() == installTarget }
        ?.let { plugin ->
            InstallDialog(
                plugin = plugin,
                onConfirm = {
                    onInstall(plugin)
                    installTarget = null
                },
                onDismiss = { installTarget = null },
            )
        }

    AdminScaffold(
        title = stringResource(R.string.title_plugin_catalog),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.sections,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { sections ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "search") {
                    AfinityTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = stringResource(R.string.plugin_catalog_search),
                        leadingIcon = painterResource(id = R.drawable.ic_search),
                    )
                }
                if (sections.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.plugin_catalog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                }
                items(items = sections, key = { it.category }) { section ->
                    val title =
                        pluginCategoryLabel(section.category)?.let { stringResource(it) }
                            ?: section.category
                    SettingsGroup(title = title) {
                        section.plugins.forEachIndexed { index, plugin ->
                            if (index > 0) SettingsDivider()
                            CatalogRow(
                                plugin = plugin,
                                actionsEnabled = !state.isBusy,
                                isInstalling = plugin.id == state.installingId,
                                onInstall = { installTarget = plugin.id.toString() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    plugin: CatalogPluginRow,
    actionsEnabled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
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
            if (plugin.summary.isNotBlank()) {
                Text(
                    text = plugin.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.plugin_catalog_version_fmt,
                        plugin.latestVersion,
                        plugin.repositoryName,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            isInstalling -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plugin_installing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            plugin.hasUpdate ->
                TextButton(onClick = onInstall, enabled = actionsEnabled) {
                    Text(stringResource(R.string.plugin_action_update))
                }
            plugin.isInstalled -> {
                AdminPill(text = stringResource(R.string.plugin_installed))
                Spacer(modifier = Modifier.width(16.dp))
            }
            else ->
                TextButton(onClick = onInstall, enabled = actionsEnabled) {
                    Text(stringResource(R.string.plugin_action_install))
                }
        }
    }
}

@Composable
private fun InstallDialog(plugin: CatalogPluginRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.plugin_install_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.heightIn(max = MAX_DIALOG_TEXT_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.plugin_install_message,
                            plugin.name,
                            plugin.latestVersion,
                            plugin.repositoryName,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (plugin.changelog.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_changelog_title),
                        style =
                            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = plugin.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (plugin.hasUpdate) R.string.plugin_action_update
                        else R.string.plugin_action_install
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
