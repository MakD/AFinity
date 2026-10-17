package com.makd.afinity.ui.admin.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
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
import com.makd.afinity.ui.admin.users.AdminUserAvatar
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime

private val DEVICE_TILE_SIZE = 40.dp
private val DEVICE_AVATAR_SIZE = 28.dp
private val EXPANDED_START_PADDING = 72.dp

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
        onSelectFilter = viewModel::setFilter,
        onRename = viewModel::rename,
        onSignOut = viewModel::signOut,
        onSignOutGroup = viewModel::signOutGroup,
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
    onSelectFilter: (DeviceFilter) -> Unit,
    onRename: (groupKey: String, name: String) -> Unit,
    onSignOut: (id: String) -> Unit,
    onSignOutGroup: (groupKey: String) -> Unit,
    onSignOutIdle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var signOutTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var signOutGroupTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSignOutIdle by rememberSaveable { mutableStateOf(false) }

    val groups = state.groups.orEmpty()

    groups
        .firstOrNull { it.key == renameTarget }
        ?.let { group ->
            RenameDeviceDialog(
                currentName = group.name,
                onConfirm = { name ->
                    onRename(group.key, name)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        }

    groups
        .firstOrNull { group -> group.entries.any { it.id == signOutTarget } }
        ?.let { group ->
            ConfirmDialog(
                title = stringResource(R.string.device_sign_out_title),
                message = stringResource(R.string.device_sign_out_message, group.name),
                confirmLabel = stringResource(R.string.device_sign_out),
                onConfirm = {
                    signOutTarget?.let(onSignOut)
                    signOutTarget = null
                },
                onDismiss = { signOutTarget = null },
            )
        }

    groups
        .firstOrNull { it.key == signOutGroupTarget }
        ?.let { group ->
            ConfirmDialog(
                title = stringResource(R.string.device_sign_out_title),
                message = stringResource(R.string.device_sign_out_message, group.name),
                confirmLabel =
                    stringResource(R.string.device_sign_out_all_fmt, group.removableIds.size),
                onConfirm = {
                    onSignOutGroup(group.key)
                    signOutGroupTarget = null
                },
                onDismiss = { signOutGroupTarget = null },
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
            data = state.groups,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { shown ->
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                DeviceFilters(state = state, onSelectFilter = onSelectFilter)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.devices_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            )
                        }
                    } else {
                        item(key = "devices") {
                            SettingsGroup {
                                shown.forEachIndexed { index, group ->
                                    if (index > 0) DeviceDivider()
                                    DeviceGroupRow(
                                        group = group,
                                        expanded = group.key == expandedKey,
                                        actionsEnabled = !state.isBusy,
                                        onToggle = {
                                            expandedKey =
                                                if (expandedKey == group.key) null else group.key
                                        },
                                        onRename = { renameTarget = group.key },
                                        onSignOut = { id -> signOutTarget = id },
                                        onSignOutAll = { signOutGroupTarget = group.key },
                                    )
                                }
                                val canSignOutIdle =
                                    state.filter == DeviceFilter.IDLE &&
                                        shown.any { it.removableIds.isNotEmpty() }
                                if (canSignOutIdle) {
                                    DeviceDivider()
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
    }
}

@Composable
private fun DeviceFilters(state: DevicesUiState, onSelectFilter: (DeviceFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AfinityToggleChip(
            label = stringResource(R.string.devices_filter_all_fmt, state.totalCount),
            selected = state.filter == DeviceFilter.ALL,
            onSelectedChange = { onSelectFilter(DeviceFilter.ALL) },
        )
        AfinityToggleChip(
            label = stringResource(R.string.devices_filter_week_fmt, state.weekCount),
            selected = state.filter == DeviceFilter.THIS_WEEK,
            onSelectedChange = { onSelectFilter(DeviceFilter.THIS_WEEK) },
        )
        AfinityToggleChip(
            label = stringResource(R.string.devices_filter_idle_fmt, state.idleCount),
            selected = state.filter == DeviceFilter.IDLE,
            onSelectedChange = { onSelectFilter(DeviceFilter.IDLE) },
        )
    }
}

@Composable
private fun DeviceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = EXPANDED_START_PADDING, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
    )
}

@Composable
private fun DeviceGroupRow(
    group: AdminDeviceGroup,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onSignOut: (String) -> Unit,
    onSignOutAll: () -> Unit,
) {
    val context = LocalContext.current
    val isFolded = group.entries.size > 1
    val summary =
        listOfNotNull(
                if (isFolded) {
                    group.app.ifBlank { null }
                } else {
                    listOf(group.app, group.entries.first().version)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { null }
                },
                group.lastActivity?.let { formatRelativeTime(context, it) },
                stringResource(R.string.device_this_device).takeIf { group.isThisDevice },
            )
            .joinToString(" · ")

    SettingsItem(
        leading = { DeviceTile(group = group) },
        title = group.name,
        subtitle = summary,
        subtitleMaxLines = 1,
        onClick = onToggle,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isFolded) AdminPill(text = group.entries.size.toString())
                AdminUserAvatar(
                    imageUrl = group.userImageUrl,
                    name = group.userName.orEmpty(),
                    size = DEVICE_AVATAR_SIZE,
                )
            }
        },
    )

    if (expanded) {
        DeviceEntries(
            group = group,
            actionsEnabled = actionsEnabled,
            onRename = onRename,
            onSignOut = onSignOut,
            onSignOutAll = onSignOutAll,
        )
    }
}

@Composable
private fun DeviceTile(group: AdminDeviceGroup) {
    val imageUrl = group.iconUrl ?: group.logoUrl
    val isLogo = group.iconUrl == null
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val showsImage = imageUrl != null && !imageFailed
    val contentColor =
        if (group.isThisDevice) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            Modifier.size(DEVICE_TILE_SIZE)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (group.isThisDevice) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (showsImage) {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier =
                    if (isLogo) Modifier.size(22.dp)
                    else Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
                colorFilter = if (isLogo) ColorFilter.tint(contentColor) else null,
                onError = { imageFailed = true },
            )
        } else {
            Icon(
                painter = painterResource(id = group.kind.iconRes()),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DeviceEntries(
    group: AdminDeviceGroup,
    actionsEnabled: Boolean,
    onRename: () -> Unit,
    onSignOut: (String) -> Unit,
    onSignOutAll: () -> Unit,
) {
    val context = LocalContext.current
    val removable = group.removableIds.size

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = EXPANDED_START_PADDING, end = 8.dp, bottom = 8.dp)
    ) {
        group.entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.version.ifBlank { group.app },
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                entry.lastActivity?.let { time ->
                    Text(
                        text = formatRelativeTime(context, time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (entry.isThisDevice) {
                    AdminPill(
                        text = stringResource(R.string.device_this_device),
                        emphasised = true,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                } else {
                    IconButton(
                        onClick = { onSignOut(entry.id) },
                        enabled = actionsEnabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logout),
                            contentDescription = stringResource(R.string.device_sign_out),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onRename,
                enabled = actionsEnabled,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.action_rename))
            }
            if (removable > 1) {
                TextButton(
                    onClick = onSignOutAll,
                    enabled = actionsEnabled,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(stringResource(R.string.device_sign_out_all_fmt, removable))
                }
            }
        }
    }
}

private fun DeviceKind.iconRes(): Int =
    when (this) {
        DeviceKind.PHONE -> R.drawable.ic_device_mobile
        DeviceKind.TABLET -> R.drawable.ic_device_tablet
        DeviceKind.TV -> R.drawable.ic_tv
        DeviceKind.BROWSER -> R.drawable.ic_world
        DeviceKind.MUSIC -> R.drawable.ic_headphones
        DeviceKind.SERVICE -> R.drawable.ic_server
        DeviceKind.OTHER -> R.drawable.ic_devices
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
