package com.makd.afinity.ui.admin.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime

private enum class UserDialog {
    RENAME,
    PASSWORD,
    DISABLE,
    DELETE,
}

@Composable
fun UserDetailScreen(
    onBackClick: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenParentalControls: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    LaunchedEffect(Unit) { viewModel.deleted.collect { onBackClick() } }

    UserDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenPermissions = onOpenPermissions,
        onOpenParentalControls = onOpenParentalControls,
        onRename = viewModel::rename,
        onSetPassword = viewModel::setPassword,
        onSetDisabled = viewModel::setDisabled,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
private fun UserDetailContent(
    state: UserDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenParentalControls: () -> Unit,
    onRename: (String) -> Unit,
    onSetPassword: (newPassword: String, currentPassword: String?) -> Unit,
    onSetDisabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<UserDialog?>(null) }
    val user = state.user

    if (user != null) {
        when (dialog) {
            UserDialog.RENAME ->
                RenameUserDialog(
                    currentName = user.name,
                    onConfirm = { name ->
                        onRename(name)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            UserDialog.PASSWORD ->
                SetPasswordDialog(
                    needsCurrentPassword = user.isSelf,
                    onConfirm = { newPassword, currentPassword ->
                        onSetPassword(newPassword, currentPassword)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            UserDialog.DISABLE ->
                ConfirmDialog(
                    title = stringResource(R.string.user_disable_title),
                    message = stringResource(R.string.user_disable_message, user.name),
                    confirmLabel = stringResource(R.string.user_disable),
                    onConfirm = {
                        onSetDisabled(true)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            UserDialog.DELETE ->
                ConfirmDialog(
                    title = stringResource(R.string.user_delete_title),
                    message = stringResource(R.string.user_delete_message, user.name),
                    confirmLabel = stringResource(R.string.action_delete),
                    onConfirm = {
                        onDelete()
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            null -> Unit
        }
    }

    AdminScaffold(
        title = user?.name ?: stringResource(R.string.title_users),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        if (state.missing) {
            FullScreenEmpty(
                message = stringResource(R.string.user_not_found),
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            AdminLoadContent(
                data = user,
                failed = state.failed,
                onRetry = onRetry,
                contentPadding = contentPadding,
            ) { loaded ->
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    UserSummaryGroup(user = loaded)
                    SettingsGroup {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_security),
                            title = stringResource(R.string.title_user_permissions),
                            subtitle = stringResource(R.string.user_permissions_summary),
                            onClick = onOpenPermissions,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_lock_filled),
                            title = stringResource(R.string.title_user_parental),
                            subtitle = stringResource(R.string.user_parental_summary),
                            onClick = onOpenParentalControls,
                        )
                    }
                    UserAccountGroup(
                        user = loaded,
                        enabled = !state.isBusy,
                        onRename = { dialog = UserDialog.RENAME },
                        onSetPassword = { dialog = UserDialog.PASSWORD },
                        onDisable = { dialog = UserDialog.DISABLE },
                        onEnable = { onSetDisabled(false) },
                        onDelete = { dialog = UserDialog.DELETE },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSummaryGroup(user: AdminUserDetail) {
    val context = LocalContext.current
    val lastActive =
        if (user.lastActivity != null) {
            stringResource(
                R.string.admin_last_active_fmt,
                formatRelativeTime(context, user.lastActivity),
            )
        } else {
            stringResource(R.string.admin_never_active)
        }
    SettingsGroup {
        SettingsItem(
            title = user.name,
            subtitle = if (user.isSelf) stringResource(R.string.user_this_account) else lastActive,
            leading = { UserAvatar(imageUrl = user.imageUrl) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (user.isAdmin) {
                        AdminPill(text = stringResource(R.string.role_admin), emphasised = true)
                    }
                    if (user.isDisabled) {
                        AdminPill(text = stringResource(R.string.user_disabled))
                    }
                }
            },
        )
    }
}

@Composable
private fun UserAccountGroup(
    user: AdminUserDetail,
    enabled: Boolean,
    onRename: () -> Unit,
    onSetPassword: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.section_user_account)) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_edit),
            title = stringResource(R.string.action_rename),
            onClick = onRename,
            enabled = enabled,
        )
        SettingsDivider()
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_lock_filled),
            title = stringResource(R.string.user_set_password),
            subtitle =
                if (user.isSelf) stringResource(R.string.user_set_password_summary_self)
                else stringResource(R.string.user_set_password_summary),
            onClick = onSetPassword,
            enabled = enabled,
        )
        SettingsDivider()
        if (user.isDisabled) {
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_circle_check_outline),
                title = stringResource(R.string.user_enable),
                subtitle = stringResource(R.string.user_enable_summary),
                onClick = onEnable,
                enabled = enabled,
            )
        } else {
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_close),
                title = stringResource(R.string.user_disable),
                subtitle =
                    if (user.canDisable) stringResource(R.string.user_disable_summary)
                    else stringResource(R.string.user_disable_admin_summary),
                onClick = onDisable,
                enabled = enabled && user.canDisable,
            )
        }
        SettingsDivider()
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_delete),
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.user_delete),
            subtitle =
                when {
                    user.isSelf -> stringResource(R.string.user_delete_self_summary)
                    user.isOnlyAdmin -> stringResource(R.string.user_delete_last_admin_summary)
                    else -> stringResource(R.string.user_delete_summary)
                },
            onClick = onDelete,
            enabled = enabled && user.canDelete,
        )
    }
}

@Composable
private fun RenameUserDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(text = stringResource(R.string.user_rename_title)) },
        text = {
            AfinityTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.user_name_label),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
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
private fun SetPasswordDialog(
    needsCurrentPassword: Boolean,
    onConfirm: (newPassword: String, currentPassword: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val differ = repeated.isNotEmpty() && repeated != newPassword
    val valid =
        newPassword.isNotEmpty() &&
            repeated == newPassword &&
            (!needsCurrentPassword || currentPassword.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(text = stringResource(R.string.user_set_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (needsCurrentPassword) {
                    PasswordField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = stringResource(R.string.user_current_password_label),
                    )
                }
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.user_new_password_label),
                )
                PasswordField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = stringResource(R.string.user_confirm_password_label),
                    errorText =
                        if (differ) stringResource(R.string.user_passwords_differ) else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(newPassword, currentPassword.takeIf { needsCurrentPassword })
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.action_save))
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
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String? = null,
) {
    AfinityTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        isError = errorText != null,
        supportingText = errorText,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
        title = { DialogTitle(text = title) },
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
