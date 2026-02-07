package com.makd.afinity.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.makd.afinity.R
import com.makd.afinity.core.AppConstants
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.settings.update.UpdateSection
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    onLicensesClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPlayerOptionsClick: () -> Unit,
    onAppearanceOptionsClick: () -> Unit,
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val combineLibrarySections by viewModel.combineLibrarySections.collectAsStateWithLifecycle()
    val homeSortByDateAdded by viewModel.homeSortByDateAdded.collectAsStateWithLifecycle()
    val manualOfflineMode by viewModel.manualOfflineMode.collectAsStateWithLifecycle()
    val effectiveOfflineMode by viewModel.effectiveOfflineMode.collectAsStateWithLifecycle()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by
        viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()
    val isAudiobookshelfAuthenticated by
        viewModel.isAudiobookshelfAuthenticated.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showJellyseerrLogoutDialog by remember { mutableStateOf(false) }
    var showAudiobookshelfLogoutDialog by remember { mutableStateOf(false) }
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    var showAudiobookshelfBottomSheet by remember { mutableStateOf(false) }
    var showSessionSwitcherSheet by remember { mutableStateOf(false) }
    val jellyseerrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audiobookshelfSheetState = rememberModalBottomSheetState()
    val sessionSwitcherSheetState = rememberModalBottomSheetState()

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout(onLogoutComplete)
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    if (showJellyseerrLogoutDialog) {
        JellyseerrLogoutConfirmationDialog(
            onConfirm = {
                showJellyseerrLogoutDialog = false
                viewModel.logoutFromJellyseerr()
            },
            onDismiss = { showJellyseerrLogoutDialog = false },
        )
    }

    if (showAudiobookshelfLogoutDialog) {
        AudiobookshelfLogoutConfirmationDialog(
            onConfirm = {
                showAudiobookshelfLogoutDialog = false
                viewModel.logoutFromAudiobookshelf()
            },
            onDismiss = { showAudiobookshelfLogoutDialog = false },
        )
    }

    if (showJellyseerrBottomSheet) {
        JellyseerrBottomSheet(
            onDismiss = { showJellyseerrBottomSheet = false },
            sheetState = jellyseerrSheetState,
        )
    }

    if (showAudiobookshelfBottomSheet) {
        AudiobookshelfBottomSheet(
            onDismiss = { showAudiobookshelfBottomSheet = false },
            sheetState = audiobookshelfSheetState,
        )
    }

    if (showSessionSwitcherSheet) {
        SessionSwitcherBottomSheet(
            onDismiss = { showSessionSwitcherSheet = false },
            onAddAccountClick = { server ->
                showSessionSwitcherSheet = false
                navController.navigate(Destination.createLoginRoute(serverUrl = server.address))
            },
            sheetState = sessionSwitcherSheetState,
        )
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) { Timber.e("Settings error: $error") }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.dialog_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "profile") {
                    ProfileHeader(
                        userName =
                            uiState.currentUser?.name ?: stringResource(R.string.unknown_user),
                        serverName = uiState.serverName,
                        serverUrl = uiState.serverUrl,
                        userProfileImageUrl = uiState.userProfileImageUrl,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                item {
                    SettingsGroup(title = stringResource(R.string.pref_group_general)) {
                        SettingsSwitchItem(
                            icon = painterResource(id = R.drawable.ic_cloud_off),
                            title = stringResource(R.string.pref_offline_mode),
                            subtitle =
                                if (!isNetworkAvailable)
                                    stringResource(R.string.offline_mode_no_connection)
                                else if (manualOfflineMode)
                                    stringResource(R.string.offline_mode_manual)
                                else stringResource(R.string.offline_mode_force),
                            checked = effectiveOfflineMode,
                            onCheckedChange = viewModel::toggleOfflineMode,
                            enabled = isNetworkAvailable,
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = painterResource(id = R.drawable.ic_seerr_logo),
                            title = stringResource(R.string.pref_discovery_requests),
                            subtitle =
                                if (isJellyseerrAuthenticated)
                                    stringResource(R.string.discovery_connected)
                                else stringResource(R.string.discovery_connect),
                            checked = isJellyseerrAuthenticated,
                            onCheckedChange = { enabled ->
                                if (enabled) showJellyseerrBottomSheet = true
                                else showJellyseerrLogoutDialog = true
                            },
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            icon = painterResource(id = R.drawable.ic_audiobookshelf_light),
                            title = stringResource(R.string.pref_audiobookshelf),
                            subtitle =
                                if (isAudiobookshelfAuthenticated)
                                    stringResource(R.string.audiobookshelf_connected)
                                else stringResource(R.string.audiobookshelf_connect),
                            checked = isAudiobookshelfAuthenticated,
                            onCheckedChange = { enabled ->
                                if (enabled) showAudiobookshelfBottomSheet = true
                                else showAudiobookshelfLogoutDialog = true
                            },
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_download),
                            title = stringResource(R.string.pref_downloads),
                            subtitle = stringResource(R.string.pref_downloads_summary),
                            onClick = onDownloadClick,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_user),
                            title = stringResource(R.string.pref_switch_session),
                            subtitle = stringResource(R.string.pref_switch_session_summary),
                            onClick = { showSessionSwitcherSheet = true },
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.pref_group_connections)) {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_server),
                            title = stringResource(R.string.pref_manage_servers),
                            subtitle = stringResource(R.string.pref_manage_servers_summary),
                            onClick = onServerManagementClick,
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.pref_group_preferences)) {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_color_swatch),
                            title = stringResource(R.string.pref_appearance),
                            subtitle = stringResource(R.string.pref_appearance_summary),
                            onClick = onAppearanceOptionsClick,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_playback_settings),
                            title = stringResource(R.string.pref_playback),
                            subtitle = stringResource(R.string.pref_playback_summary),
                            onClick = onPlayerOptionsClick,
                        )
                    }
                }

                item { UpdateSection() }

                item {
                    SettingsGroup(title = stringResource(R.string.pref_group_about)) {
                        val buildType =
                            if (AppConstants.IS_DEBUG) stringResource(R.string.build_debug)
                            else stringResource(R.string.build_release)
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_versions),
                            title = stringResource(R.string.pref_version),
                            subtitle =
                                stringResource(
                                    R.string.version_fmt,
                                    AppConstants.VERSION_NAME,
                                    buildType,
                                ),
                            onClick = null,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_source_code),
                            title = stringResource(R.string.pref_licenses),
                            subtitle = stringResource(R.string.pref_licenses_summary),
                            onClick = onLicensesClick,
                        )
                    }
                }

                item {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 32.dp)
                    ) {
                        Button(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !uiState.isLoggingOut,
                        ) {
                            if (uiState.isLoggingOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_logout),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_logout),
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
    )
}

@Composable
fun ProfileHeader(
    userName: String,
    serverName: String?,
    serverUrl: String?,
    userProfileImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (userProfileImageUrl != null) {
                AsyncImage(
                    imageUrl = userProfileImageUrl,
                    contentDescription = stringResource(R.string.cd_profile_picture),
                    targetWidth = 96.dp,
                    targetHeight = 96.dp,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = userName.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = serverName ?: stringResource(R.string.unknown_server),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (serverUrl != null) {
                    VerticalDivider(modifier = Modifier.height(12.dp))
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick =
            if (enabled) {
                { onCheckedChange(!checked) }
            } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                modifier = Modifier.scale(0.8f),
            )
        },
    )
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_logout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_logout_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_logout_message),
                style = MaterialTheme.typography.bodyMedium,
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
                Text(stringResource(R.string.action_logout))
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
private fun AudiobookshelfLogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_audiobookshelf_light),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_disconnect_audiobookshelf_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_disconnect_audiobookshelf_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_disconnect)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun JellyseerrLogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_seerr_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_disconnect_seerr_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_disconnect_seerr_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_disconnect)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
