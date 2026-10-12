package com.makd.afinity.ui.admin.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime
import java.util.UUID

private val USER_CARD_MIN_WIDTH = 148.dp
private val USER_CARD_AVATAR_SIZE = 72.dp
private const val DISABLED_USER_ALPHA = 0.5f

@Composable
fun UsersScreen(
    onBackClick: () -> Unit,
    onOpenUser: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playingUserIds by viewModel.playingUserIds.collectAsStateWithLifecycle()
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
        playingUserIds = playingUserIds,
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
    playingUserIds: Set<UUID>,
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
        floatingActionButton = {
            if (state.users != null) {
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
                    Text(stringResource(R.string.user_add))
                }
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.users,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { users ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = USER_CARD_MIN_WIDTH),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding =
                    PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "counts", span = { GridItemSpan(maxLineSpan) }) {
                    UserCounts(
                        total = users.size,
                        admins = state.adminCount,
                        disabled = state.disabledCount,
                    )
                }
                items(items = users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        isPlaying = user.id in playingUserIds,
                        onClick = { onOpenUser(user.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserCounts(total: Int, admins: Int, disabled: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdminPill(text = stringResource(R.string.users_count_fmt, total))
        AdminPill(text = stringResource(R.string.users_admins_fmt, admins), emphasised = true)
        if (disabled > 0) {
            AdminPill(text = stringResource(R.string.users_disabled_fmt, disabled))
        }
    }
}

@Composable
private fun UserCard(user: AdminUserRow, isPlaying: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val status =
        when {
            user.isDisabled -> stringResource(R.string.user_disabled)
            isPlaying -> stringResource(R.string.user_playing_now)
            user.lastActivity != null ->
                stringResource(
                    R.string.admin_last_active_fmt,
                    formatRelativeTime(context, user.lastActivity),
                )
            else -> stringResource(R.string.admin_never_active)
        }
    val contentAlpha = if (user.isDisabled) DISABLED_USER_ALPHA else 1f
    val showsPlaying = isPlaying && !user.isDisabled

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 12.dp, top = 20.dp, end = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AdminUserAvatar(
                    imageUrl = user.imageUrl,
                    name = user.name,
                    size = USER_CARD_AVATAR_SIZE,
                    modifier = Modifier.alpha(contentAlpha),
                    isPlaying = showsPlaying,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = user.name,
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(contentAlpha),
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (showsPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (showsPlaying) UserPlayingColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).alpha(contentAlpha),
                )
            }
            if (user.isAdmin) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield_check_filled),
                    contentDescription = stringResource(R.string.role_admin),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp),
                )
            }
        }
    }
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
