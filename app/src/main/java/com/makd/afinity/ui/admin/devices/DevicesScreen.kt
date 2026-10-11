package com.makd.afinity.ui.admin.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime

@Composable
fun DevicesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    DevicesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onRename = viewModel::rename,
        onSignOut = viewModel::signOut,
        onSignOutIdle = viewModel::signOutIdle,
        modifier = modifier,
    )
}

@Composable
private fun DevicesContent(
    state: DevicesUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onSignOut: (id: String) -> Unit,
    onSignOutIdle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var signOutTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSignOutIdle by rememberSaveable { mutableStateOf(false) }

    val allDevices = state.devices?.let { it.recent + it.idle }.orEmpty()

    allDevices
        .firstOrNull { it.id == renameTarget }
        ?.let { device ->
            RenameDeviceDialog(
                currentName = device.name,
                onConfirm = { name ->
                    onRename(device.id, name)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        }

    allDevices
        .firstOrNull { it.id == signOutTarget }
        ?.let { device ->
            ConfirmDialog(
                title = stringResource(R.string.device_sign_out_title),
                message = stringResource(R.string.device_sign_out_message, device.name),
                confirmLabel = stringResource(R.string.device_sign_out),
                onConfirm = {
                    onSignOut(device.id)
                    signOutTarget = null
                },
                onDismiss = { signOutTarget = null },
            )
        }

    if (confirmSignOutIdle) {
        ConfirmDialog(
            title = stringResource(R.string.device_sign_out_idle_title),
            message = stringResource(R.string.device_sign_out_idle_message),
            confirmLabel = stringResource(R.string.device_sign_out),
            onConfirm = {
                onSignOutIdle()
                confirmSignOutIdle = false
            },
            onDismiss = { confirmSignOutIdle = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_devices),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.devices,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { devices ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (devices.recent.isNotEmpty()) {
                    item(key = "recent") {
                        SettingsGroup(title = stringResource(R.string.devices_recent)) {
                            DeviceRows(
                                devices = devices.recent,
                                actionsEnabled = !state.isBusy,
                                onRename = { renameTarget = it },
                                onSignOut = { signOutTarget = it },
                            )
                        }
                    }
                }
                if (devices.idle.isNotEmpty()) {
                    item(key = "idle") {
                        SettingsGroup(title = stringResource(R.string.devices_idle)) {
                            DeviceRows(
                                devices = devices.idle,
                                actionsEnabled = !state.isBusy,
                                onRename = { renameTarget = it },
                                onSignOut = { signOutTarget = it },
                            )
                            SettingsDivider()
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_logout),
                                title = stringResource(R.string.device_sign_out_idle),
                                onClick = { confirmSignOutIdle = true },
                                enabled = !state.isBusy,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRows(
    devices: List<AdminDeviceRow>,
    actionsEnabled: Boolean,
    onRename: (String) -> Unit,
    onSignOut: (String) -> Unit,
) {
    devices.forEachIndexed { index, device ->
        if (index > 0) SettingsDivider()
        DeviceRow(
            device = device,
            actionsEnabled = actionsEnabled,
            onRename = { onRename(device.id) },
            onSignOut = { onSignOut(device.id) },
        )
    }
}

@Composable
private fun DeviceRow(
    device: AdminDeviceRow,
    actionsEnabled: Boolean,
    onRename: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val summary =
        listOfNotNull(
                device.app.ifBlank { null },
                device.lastUser,
                device.lastActivity?.let { formatRelativeTime(context, it) },
            )
            .joinToString(" · ")
    SettingsItem(
        icon = painterResource(id = R.drawable.ic_devices),
        title = device.name,
        subtitle = summary,
        trailing = {
            if (device.isThisDevice) {
                AdminPill(text = stringResource(R.string.device_this_device), emphasised = true)
            } else {
                DeviceMenu(enabled = actionsEnabled, onRename = onRename, onSignOut = onSignOut)
            }
        },
    )
}

@Composable
private fun DeviceMenu(enabled: Boolean, onRename: () -> Unit, onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                painter = painterResource(id = R.drawable.ic_dots_vertical),
                contentDescription = stringResource(R.string.cd_device_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.device_sign_out)) },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.device_rename_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AfinityTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.device_name_label),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
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
