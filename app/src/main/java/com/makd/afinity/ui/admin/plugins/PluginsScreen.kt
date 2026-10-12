package com.makd.afinity.ui.admin.plugins

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.AdminToggleChip
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.logs.SearchField
import java.util.UUID
import org.jellyfin.sdk.model.api.PluginStatus

private val PLUGIN_CARD_MIN_WIDTH = 156.dp

@Composable
fun PluginsScreen(
    onBackClick: () -> Unit,
    onOpenPlugin: (UUID) -> Unit,
    onOpenRepositories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    PluginsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onSelectTab = viewModel::selectTab,
        onSelectCategory = viewModel::selectCategory,
        onQueryChange = viewModel::setQuery,
        onSearchActiveChange = viewModel::setSearchActive,
        onInstall = viewModel::install,
        onRestart = viewModel::restartServer,
        onOpenPlugin = onOpenPlugin,
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
    onSelectTab: (PluginTab) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onInstall: (CatalogPluginRow) -> Unit,
    onRestart: () -> Unit,
    onOpenPlugin: (UUID) -> Unit,
    onOpenRepositories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var installTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var showRestartConfirm by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.searchActive) { onSearchActiveChange(false) }

    state.cards
        ?.firstOrNull { it.id.toString() == installTarget }
        ?.installable
        ?.let { plugin ->
            PluginInstallDialog(
                plugin = plugin,
                onConfirm = {
                    onInstall(plugin)
                    installTarget = null
                },
                onDismiss = { installTarget = null },
            )
        }

    if (showRestartConfirm) {
        PluginConfirmDialog(
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
        onBackClick = { if (state.searchActive) onSearchActiveChange(false) else onBackClick() },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        titleContent =
            if (state.searchActive) {
                {
                    SearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.plugin_catalog_search),
                    )
                }
            } else {
                null
            },
        actions = {
            if (state.searchActive) {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
            } else {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = stringResource(R.string.plugin_catalog_search),
                    )
                }
                IconButton(onClick = onOpenRepositories) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_database),
                        contentDescription = stringResource(R.string.title_plugin_repositories),
                    )
                }
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.cards,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { cards ->
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                PluginFilters(
                    state = state,
                    onSelectTab = onSelectTab,
                    onSelectCategory = onSelectCategory,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = PLUGIN_CARD_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (state.restartRequired) {
                        item(key = "restart", span = { GridItemSpan(maxLineSpan) }) {
                            RestartBanner(
                                enabled = !state.isBusy,
                                onRestart = { showRestartConfirm = true },
                            )
                        }
                    }
                    if (cards.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyPlugins(state = state, onRetry = onRetry)
                        }
                    }
                    items(items = cards, key = { it.id.toString() }) { card ->
                        PluginCard(
                            card = card,
                            isInstalling = card.id == state.installingId,
                            actionsEnabled = !state.isBusy,
                            onClick = { onOpenPlugin(card.id) },
                            onInstall = { installTarget = card.id.toString() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginFilters(
    state: PluginsUiState,
    onSelectTab: (PluginTab) -> Unit,
    onSelectCategory: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdminToggleChip(
            label = stringResource(R.string.plugins_tab_installed_fmt, state.installedCount),
            selected = state.tab == PluginTab.INSTALLED,
            onSelectedChange = { onSelectTab(PluginTab.INSTALLED) },
        )
        AdminToggleChip(
            label =
                state.availableCount?.let { count ->
                    stringResource(R.string.plugins_tab_available_fmt, count)
                } ?: stringResource(R.string.plugins_tab_available),
            selected = state.tab == PluginTab.AVAILABLE,
            onSelectedChange = { onSelectTab(PluginTab.AVAILABLE) },
        )
    }
    if (state.categories.isNotEmpty()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdminToggleChip(
                label = stringResource(R.string.plugins_category_all),
                selected = state.category == null,
                onSelectedChange = { onSelectCategory(null) },
            )
            state.categories.forEach { category ->
                AdminToggleChip(
                    label = pluginCategoryLabel(category)?.let { stringResource(it) } ?: category,
                    selected = state.category == category,
                    onSelectedChange = { onSelectCategory(category) },
                )
            }
        }
    }
}

@Composable
private fun EmptyPlugins(state: PluginsUiState, onRetry: () -> Unit) {
    val showsCatalog = state.tab == PluginTab.AVAILABLE
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            showsCatalog && state.isCatalogLoading ->
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            showsCatalog && state.catalogFailed -> {
                Text(
                    text = stringResource(R.string.error_load_content_generic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            else ->
                Text(
                    text = stringResource(R.string.plugin_catalog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun RestartBanner(enabled: Boolean, onRestart: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
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
private fun PluginCard(
    card: PluginCardRow,
    isInstalling: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
) {
    val statusLabel = card.status?.labelRes()
    val detail =
        when {
            !card.isInstalled ->
                stringResource(
                    R.string.plugin_catalog_version_fmt,
                    card.version,
                    card.catalog?.repositoryName.orEmpty(),
                )
            card.hasUpdate ->
                stringResource(
                    R.string.plugin_catalog_version_fmt,
                    card.version,
                    stringResource(R.string.plugin_update_available),
                )
            statusLabel != null ->
                stringResource(
                    R.string.plugin_catalog_version_fmt,
                    card.version,
                    stringResource(statusLabel),
                )
            card.isBuiltIn ->
                stringResource(
                    R.string.plugin_catalog_version_fmt,
                    card.version,
                    stringResource(R.string.plugin_built_in),
                )
            else -> card.version
        }
    val isHighlighted = card.hasUpdate || card.status == PluginStatus.RESTART
    val detailColor =
        when {
            card.status?.isProblem == true -> MaterialTheme.colorScheme.error
            isHighlighted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Column(
        modifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PluginCover(imageUrl = card.imageUrl)
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style =
                        if (isHighlighted) {
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                    color = detailColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                isInstalling ->
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                card.installable != null ->
                    IconButton(
                        onClick = onInstall,
                        modifier = Modifier.size(36.dp),
                        enabled = actionsEnabled,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download_arrow),
                            contentDescription =
                                stringResource(
                                    if (card.hasUpdate) R.string.plugin_action_update
                                    else R.string.plugin_action_install
                                ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
            }
        }
    }
}
