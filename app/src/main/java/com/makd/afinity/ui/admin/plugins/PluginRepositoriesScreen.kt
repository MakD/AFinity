package com.makd.afinity.ui.admin.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import org.jellyfin.sdk.model.api.RepositoryInfo

@Composable
fun PluginRepositoriesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginRepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    PluginRepositoriesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onSetEnabled = viewModel::setEnabled,
        modifier = modifier,
    )
}

@Composable
private fun PluginRepositoriesContent(
    state: PluginRepositoriesUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    onRemove: (index: Int, repository: RepositoryInfo) -> Unit,
    onSetEnabled: (index: Int, repository: RepositoryInfo, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var removeIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    if (showAdd) {
        AddRepositoryDialog(
            onConfirm = { name, url ->
                onAdd(name, url)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    val removeTarget = removeIndex?.let { index -> state.repositories?.getOrNull(index) }
    val removeTargetIndex = removeIndex
    if (removeTarget != null && removeTargetIndex != null) {
        RemoveRepositoryDialog(
            name = removeTarget.name.orEmpty().ifBlank { removeTarget.url.orEmpty() },
            onConfirm = {
                onRemove(removeTargetIndex, removeTarget)
                removeIndex = null
            },
            onDismiss = { removeIndex = null },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_plugin_repositories),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.repositories,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { repositories ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "repositories") {
                    SettingsGroup {
                        if (repositories.isEmpty()) {
                            Text(
                                text = stringResource(R.string.plugin_repositories_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                            SettingsDivider()
                        }
                        val removeLabel = stringResource(R.string.cd_plugin_repository_remove)
                        repositories.forEachIndexed { index, repository ->
                            val url = repository.url.orEmpty()
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_database),
                                title = repository.name.orEmpty().ifBlank { url },
                                subtitle = url,
                                trailing = {
                                    AfinitySwitch(
                                        checked = repository.enabled,
                                        onCheckedChange = { enabled ->
                                            onSetEnabled(index, repository, enabled)
                                        },
                                        enabled = !state.isBusy,
                                    )
                                    AdminRemoveButton(
                                        contentDescription = removeLabel,
                                        onClick = { removeIndex = index },
                                        enabled = !state.isBusy,
                                    )
                                },
                            )
                            SettingsDivider()
                        }
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_add),
                            title = stringResource(R.string.plugin_repository_add),
                            onClick = { showAdd = true },
                            enabled = !state.isBusy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val urlValid = isValidRepositoryUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.plugin_repository_add),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AfinityTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.plugin_repository_name_label),
                )
                AfinityTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.plugin_repository_url_label),
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = stringResource(R.string.plugin_repository_url_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && urlValid,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun RemoveRepositoryDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.plugin_repository_remove_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.plugin_repository_remove_message, name),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
