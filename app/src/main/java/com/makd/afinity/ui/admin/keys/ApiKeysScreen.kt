package com.makd.afinity.ui.admin.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.SecureWindowEffect
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.util.logging.LogClipboard
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private const val MASKED_KEY_PREFIX = "•••• •••• •••• "
private const val VISIBLE_KEY_SUFFIX_LENGTH = 4
private const val KEY_GROUP_LENGTH = 4
private const val DISABLED_ALPHA = 0.38f

private val ROW_ACTION_SIZE = 40.dp
private val ROW_ACTION_ICON_SIZE = 22.dp

@Composable
fun ApiKeysScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ApiKeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val copiedMessage = stringResource(R.string.api_key_copied)
    val clipboardLabel = stringResource(R.string.title_api_keys)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    ApiKeysContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onToggleRevealed = viewModel::toggleRevealed,
        onCopy = { token ->
            scope.launch {
                LogClipboard.copy(context, clipboardLabel, token)
                snackbarHostState.showSnackbar(copiedMessage)
            }
        },
        onCreate = viewModel::create,
        onDismissCreated = viewModel::dismissCreated,
        onRevoke = viewModel::revoke,
        modifier = modifier,
    )
}

@Composable
private fun ApiKeysContent(
    state: ApiKeysUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onToggleRevealed: (rowId: String) -> Unit,
    onCopy: (token: String) -> Unit,
    onCreate: (appName: String) -> Unit,
    onDismissCreated: () -> Unit,
    onRevoke: (rowId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var revokeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val createdKey = state.keys?.firstOrNull { it.rowId == state.createdRowId }

    SecureWindowEffect(enabled = state.revealedRowIds.isNotEmpty() || createdKey != null)

    if (createdKey != null) {
        KeyCreatedDialog(
            key = createdKey,
            onCopy = {
                onCopy(createdKey.token)
                onDismissCreated()
            },
            onDismiss = onDismissCreated,
        )
    }

    if (showCreate) {
        CreateKeyDialog(
            onConfirm = { appName ->
                onCreate(appName)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    state.keys
        ?.firstOrNull { it.rowId == revokeTarget }
        ?.let { key ->
            RevokeKeyDialog(
                appName = key.appName,
                onConfirm = {
                    onRevoke(key.rowId)
                    revokeTarget = null
                },
                onDismiss = { revokeTarget = null },
            )
        }

    AdminScaffold(
        title = stringResource(R.string.title_api_keys),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.keys != null) {
                ExtendedFloatingActionButton(
                    onClick = { if (!state.isBusy) showCreate = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.api_key_new))
                }
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.keys,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { keys ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding =
                    PaddingValues(
                        top = 16.dp,
                        bottom = adminContentBottomPadding(hasFloatingButton = true),
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "note") { KeysNote() }
                item(key = "keys") {
                    SettingsGroup {
                        if (keys.isEmpty()) {
                            Text(
                                text = stringResource(R.string.api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                        keys.forEachIndexed { index, key ->
                            if (index > 0) SettingsDivider()
                            ApiKeyRow(
                                key = key,
                                revealed = key.rowId in state.revealedRowIds,
                                actionsEnabled = !state.isBusy,
                                onToggleRevealed = { onToggleRevealed(key.rowId) },
                                onCopy = { onCopy(key.token) },
                                onRevoke = { revokeTarget = key.rowId },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeysNote() {
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
                text = stringResource(R.string.api_keys_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyRow(
    key: AdminApiKeyRow,
    revealed: Boolean,
    actionsEnabled: Boolean,
    onToggleRevealed: () -> Unit,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    val created =
        remember(key.created) {
            key.created.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }

    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lock_filled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = key.appName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.api_key_created_fmt, created),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text =
                    if (revealed) key.token
                    else MASKED_KEY_PREFIX + key.token.takeLast(VISIBLE_KEY_SUFFIX_LENGTH),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onToggleRevealed, modifier = Modifier.size(ROW_ACTION_SIZE)) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    ),
                contentDescription =
                    if (revealed) stringResource(R.string.cd_api_key_hide)
                    else stringResource(R.string.cd_api_key_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ROW_ACTION_ICON_SIZE),
            )
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(ROW_ACTION_SIZE)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_clipboard),
                contentDescription = stringResource(R.string.api_key_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ROW_ACTION_ICON_SIZE),
            )
        }
        IconButton(
            onClick = onRevoke,
            enabled = actionsEnabled,
            modifier = Modifier.size(ROW_ACTION_SIZE),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(R.string.api_key_revoke),
                tint =
                    MaterialTheme.colorScheme.error.copy(
                        alpha = if (actionsEnabled) 1f else DISABLED_ALPHA
                    ),
                modifier = Modifier.size(ROW_ACTION_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun KeyCreatedDialog(key: AdminApiKeyRow, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.api_key_created_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.api_key_created_message, key.appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = key.token.chunked(KEY_GROUP_LENGTH).joinToString(" "),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun CreateKeyDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var appName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.api_key_new_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AfinityTextField(
                value = appName,
                onValueChange = { appName = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.api_key_app_name_label),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(appName) }, enabled = appName.isNotBlank()) {
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

@Composable
private fun RevokeKeyDialog(appName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.api_key_revoke_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.api_key_revoke_message, appName),
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
                Text(stringResource(R.string.api_key_revoke))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
