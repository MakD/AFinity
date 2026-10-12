package com.makd.afinity.ui.admin.users

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

private val USER_HEADER_AVATAR_SIZE = 96.dp
private const val DISABLED_HEADER_ALPHA = 0.5f

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
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val picturePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.setPicture(uri)
        }

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
        isPlaying = isPlaying,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenPermissions = onOpenPermissions,
        onOpenParentalControls = onOpenParentalControls,
        onChoosePicture = {
            picturePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemovePicture = viewModel::removePicture,
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
    isPlaying: Boolean,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenParentalControls: () -> Unit,
    onChoosePicture: () -> Unit,
    onRemovePicture: () -> Unit,
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
        title = stringResource(R.string.title_user),
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
                    UserHeader(
                        user = loaded,
                        isPlaying = isPlaying,
                        isUpdatingPicture = state.isUpdatingPicture,
                        enabled = !state.isBusy,
                        onChoosePicture = onChoosePicture,
                        onRemovePicture = onRemovePicture,
                    )
                    loaded.access?.let { access ->
                        UserAccessTiles(
                            access = access,
                            libraryIds = state.libraryIds,
                            ratings = state.ratings,
                            onOpenPermissions = onOpenPermissions,
                            onOpenParentalControls = onOpenParentalControls,
                        )
                    }
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
private fun UserHeader(
    user: AdminUserDetail,
    isPlaying: Boolean,
    isUpdatingPicture: Boolean,
    enabled: Boolean,
    onChoosePicture: () -> Unit,
    onRemovePicture: () -> Unit,
) {
    val context = LocalContext.current
    val showsPlaying = isPlaying && !user.isDisabled
    val status =
        when {
            user.isDisabled -> stringResource(R.string.user_disabled)
            showsPlaying -> stringResource(R.string.user_playing_now)
            user.isSelf -> stringResource(R.string.user_this_account)
            user.lastActivity != null ->
                stringResource(
                    R.string.admin_last_active_fmt,
                    formatRelativeTime(context, user.lastActivity),
                )
            else -> stringResource(R.string.admin_never_active)
        }
    val contentAlpha = if (user.isDisabled) DISABLED_HEADER_ALPHA else 1f
    val statusColor =
        if (showsPlaying) UserPlayingColor else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AdminUserAvatar(
                imageUrl = user.imageUrl,
                name = user.name,
                size = USER_HEADER_AVATAR_SIZE,
                modifier = Modifier.alpha(contentAlpha),
            )
            PictureEditButton(
                hasPicture = user.imageUrl != null,
                isUpdating = isUpdatingPicture,
                enabled = enabled,
                onChoosePicture = onChoosePicture,
                onRemovePicture = onRemovePicture,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(contentAlpha),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (showsPlaying) FontWeight.SemiBold else FontWeight.Normal,
            color = statusColor,
            modifier = Modifier.padding(top = 2.dp).alpha(contentAlpha),
        )
        if (user.isAdmin) {
            AdminPill(
                text = stringResource(R.string.role_admin),
                modifier = Modifier.padding(top = 10.dp),
                emphasised = true,
            )
        }
    }
}

@Composable
private fun UserAccessTiles(
    access: UserAccess,
    libraryIds: Set<String>?,
    ratings: List<RatingOption>?,
    onOpenPermissions: () -> Unit,
    onOpenParentalControls: () -> Unit,
) {
    val on = stringResource(R.string.state_toggle_on)
    val off = stringResource(R.string.state_toggle_off)
    val libraries =
        when {
            access.allLibraries -> stringResource(R.string.user_tile_all)
            libraryIds != null ->
                stringResource(
                    R.string.user_tile_count_of_fmt,
                    access.enabledLibraryIds.count { it in libraryIds },
                    libraryIds.size,
                )
            else -> access.enabledLibraryIds.size.toString()
        }
    val ratingScore = access.maxRatingScore
    val rating =
        when {
            ratingScore == null -> stringResource(R.string.config_none)
            else ->
                ratings
                    ?.lastOrNull {
                        it.score == ratingScore && it.subScore == access.maxRatingSubScore
                    }
                    ?.name
                    ?: ratings?.lastOrNull { it.score <= ratingScore }?.name
                    ?: ratingScore.toString()
        }
    val sessions =
        if (access.maxSessions > 0) access.maxSessions.toString()
        else stringResource(R.string.user_tile_no_limit)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccessTile(
                icon = R.drawable.ic_video_library,
                label = stringResource(R.string.libraries_title),
                value = libraries,
                isOff = !access.allLibraries && access.enabledLibraryIds.isEmpty(),
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
            AccessTile(
                icon = R.drawable.ic_lock_filled,
                label = stringResource(R.string.user_tile_rating),
                value = rating,
                isOff = ratingScore == null,
                onClick = onOpenParentalControls,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccessTile(
                icon = R.drawable.ic_world,
                label = stringResource(R.string.user_tile_remote_access),
                value = if (access.remoteAccess) on else off,
                isOff = !access.remoteAccess,
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
            AccessTile(
                icon = R.drawable.ic_movie,
                label = stringResource(R.string.user_tile_transcoding),
                value = if (access.transcoding) on else off,
                isOff = !access.transcoding,
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccessTile(
                icon = R.drawable.ic_download,
                label = stringResource(R.string.user_tile_downloads),
                value = if (access.downloads) on else off,
                isOff = !access.downloads,
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
            AccessTile(
                icon = R.drawable.ic_devices,
                label = stringResource(R.string.user_tile_sessions),
                value = sessions,
                isOff = access.maxSessions <= 0,
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccessTile(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    isOff: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isOff) FontWeight.Medium else FontWeight.SemiBold,
                color =
                    if (isOff) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PictureEditButton(
    hasPicture: Boolean,
    isUpdating: Boolean,
    enabled: Boolean,
    onChoosePicture: () -> Unit,
    onRemovePicture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            enabled = enabled && !isUpdating,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_photo_edit),
                        contentDescription = stringResource(R.string.user_picture_change),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.user_picture_choose)) },
                onClick = {
                    expanded = false
                    onChoosePicture()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_photo_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            if (hasPicture) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.user_picture_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        expanded = false
                        onRemovePicture()
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
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
