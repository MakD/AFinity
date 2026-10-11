package com.makd.afinity.ui.admin.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime
import java.util.UUID

@Composable
fun UsersScreen(
    onBackClick: () -> Unit,
    onOpenUser: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsersViewModel = hiltViewModel(),
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

    LaunchedEffect(Unit) { viewModel.userCreated.collect { id -> onOpenUser(id) } }

    UsersContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenUser = onOpenUser,
        onCreate = viewModel::create,
        modifier = modifier,
    )
}

@Composable
private fun UsersContent(
    state: UsersUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (UUID) -> Unit,
    onCreate: (name: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    if (showCreate) {
        CreateUserDialog(
            onConfirm = { name, password ->
                onCreate(name, password)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_users),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.users,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { users ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                item(key = "users") {
                    SettingsGroup {
                        users.forEach { user ->
                            UserRow(user = user, onClick = { onOpenUser(user.id) })
                            SettingsDivider()
                        }
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_add),
                            title = stringResource(R.string.user_add),
                            onClick = { showCreate = true },
                            enabled = !state.isBusy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: AdminUserRow, onClick: () -> Unit) {
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
    val pills: (@Composable () -> Unit)? =
        if (user.isAdmin || user.isDisabled) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (user.isAdmin) {
                        AdminPill(text = stringResource(R.string.role_admin), emphasised = true)
                    }
                    if (user.isDisabled) {
                        AdminPill(text = stringResource(R.string.user_disabled))
                    }
                }
            }
        } else {
            null
        }
    SettingsItem(
        title = user.name,
        subtitle = lastActive,
        onClick = onClick,
        leading = { UserAvatar(imageUrl = user.imageUrl) },
        trailing = pills,
    )
}

@Composable
private fun CreateUserDialog(
    onConfirm: (name: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.user_add),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AfinityTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.user_name_label),
                )
                AfinityTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.user_password_optional_label),
                    supportingText = stringResource(R.string.user_password_optional_hint),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, password) }, enabled = name.isNotBlank()) {
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
