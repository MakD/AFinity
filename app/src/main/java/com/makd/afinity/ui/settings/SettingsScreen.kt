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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.core.AppConstants
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.settings.update.UpdateSection
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    onLicensesClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPlayerOptionsClick: () -> Unit,
    onAppearanceOptionsClick: () -> Unit,
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val combineLibrarySections by viewModel.combineLibrarySections.collectAsStateWithLifecycle()
    val homeSortByDateAdded by viewModel.homeSortByDateAdded.collectAsStateWithLifecycle()
    val manualOfflineMode by viewModel.manualOfflineMode.collectAsStateWithLifecycle()
    val effectiveOfflineMode by viewModel.effectiveOfflineMode.collectAsStateWithLifecycle()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showJellyseerrLogoutDialog by remember { mutableStateOf(false) }
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    var showSessionSwitcherSheet by remember { mutableStateOf(false) }
    val jellyseerrSheetState = androidx.compose.material3.rememberModalBottomSheetState()
    val sessionSwitcherSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout(onLogoutComplete)
            },
            onDismiss = {
                showLogoutDialog = false
            }
        )
    }

    if (showJellyseerrLogoutDialog) {
        JellyseerrLogoutConfirmationDialog(
            onConfirm = {
                showJellyseerrLogoutDialog = false
                viewModel.logoutFromJellyseerr()
            },
            onDismiss = {
                showJellyseerrLogoutDialog = false
            }
        )
    }

    if (showJellyseerrBottomSheet) {
        JellyseerrBottomSheet(
            onDismiss = { showJellyseerrBottomSheet = false },
            sheetState = jellyseerrSheetState
        )
    }

    if (showSessionSwitcherSheet) {
        SessionSwitcherBottomSheet(
            onDismiss = { showSessionSwitcherSheet = false },
            sheetState = sessionSwitcherSheetState
        )
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            Timber.e("Settings error: $error")
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "profile_${uiState.currentUser?.id}_${uiState.serverName}") {
                    ProfileHeader(
                        userName = uiState.currentUser?.name ?: "Unknown User",
                        serverName = uiState.serverName,
                        serverUrl = uiState.serverUrl,
                        userProfileImageUrl = uiState.userProfileImageUrl
                    )
                }

                item {
                    AccountSection(
                        onSwitchSessionClick = { showSessionSwitcherSheet = true }
                    )
                }

                item {
                    GeneralSection(
                        manualOfflineMode = manualOfflineMode,
                        effectiveOfflineMode = effectiveOfflineMode,
                        isNetworkAvailable = isNetworkAvailable,
                        isJellyseerrAuthenticated = isJellyseerrAuthenticated,
                        onOfflineModeToggle = viewModel::toggleOfflineMode,
                        onJellyseerrToggle = { enabled ->
                            if (enabled) {
                                showJellyseerrBottomSheet = true
                            } else {
                                showJellyseerrLogoutDialog = true
                            }
                        },
                        onDownloadClick = onDownloadClick,
                        onLogoutClick = { showLogoutDialog = true },
                        isLoggingOut = uiState.isLoggingOut
                    )
                }

                item {
                    AppearanceSection(
                        onAppearanceOptionsClick = onAppearanceOptionsClick
                    )
                }

                item {
                    PlaybackSection(
                        onPlayerOptionsClick = onPlayerOptionsClick
                    )
                }

                item {
                    UpdateSection()
                }

                item {
                    ServerManagementSection(
                        onServerManagementClick = onServerManagementClick
                    )
                }

                item {
                    AboutSection(
                        appVersion = AppConstants.VERSION_NAME,
                        onLicensesClick = onLicensesClick
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    serverName: String?,
    serverUrl: String?,
    userProfileImageUrl: String?,
    modifier: Modifier = Modifier
) {
    Timber.d("ProfileHeader recomposing: userName=$userName, serverName=$serverName, serverUrl=$serverUrl, profileImageUrl=${userProfileImageUrl?.take(50)}")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (userProfileImageUrl != null) {
                    AsyncImage(
                        imageUrl = userProfileImageUrl,
                        contentDescription = "Profile Picture",
                        targetWidth = 64.dp,
                        targetHeight = 64.dp,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = serverName ?: "Jellyfin Server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (serverUrl != null) {
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSection(
    manualOfflineMode: Boolean,
    effectiveOfflineMode: Boolean,
    isNetworkAvailable: Boolean,
    isJellyseerrAuthenticated: Boolean,
    onOfflineModeToggle: (Boolean) -> Unit,
    onJellyseerrToggle: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onLogoutClick: () -> Unit,
    isLoggingOut: Boolean,
    modifier: Modifier = Modifier
) {
    val offlineSubtitle = when {
        !isNetworkAvailable -> "Offline (No network connection)"
        manualOfflineMode -> "Manually enabled"
        else -> "Manually enable offline mode"
    }

    val jellyseerrSubtitle = if (isJellyseerrAuthenticated) {
        "Connected - Tap to logout"
    } else {
        "Connect to request content"
    }

    SettingsSection(
        title = "General",
        icon = painterResource(id = R.drawable.ic_settings),
        modifier = modifier
    ) {
        SettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_cloud_off),
            title = "Offline Mode",
            subtitle = offlineSubtitle,
            checked = effectiveOfflineMode,
            onCheckedChange = onOfflineModeToggle,
            enabled = isNetworkAvailable
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_request_seerr_dark),
            title = "Seerr",
            subtitle = jellyseerrSubtitle,
            checked = isJellyseerrAuthenticated,
            onCheckedChange = onJellyseerrToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = painterResource(id = R.drawable.ic_download),
            title = "Downloads",
            subtitle = "Manage downloads and offline content",
            onClick = onDownloadClick
        )

        Button(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoggingOut
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logout),
                    contentDescription = "Logout",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Logout",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    onAppearanceOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_color_swatch),
            title = "Appearance",
            subtitle = "Configure theme, colors, and library layout",
            onClick = onAppearanceOptionsClick
        )
    }
}

@Composable
private fun PlaybackSection(
    onPlayerOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_playback_settings),
            title = "Player Options",
            subtitle = "Configure playback and player settings",
            onClick = onPlayerOptionsClick
        )
    }
}

@Composable
private fun ServerManagementSection(
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_server),
            title = "Manage Servers",
            subtitle = "Add, edit, and manage multiple Jellyfin servers",
            onClick = onServerManagementClick
        )
    }
}

@Composable
private fun AccountSection(
    onSwitchSessionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_user),
            title = "Switch Session",
            subtitle = "Switch between servers and users",
            onClick = onSwitchSessionClick
        )
    }
}

@Composable
private fun JellyseerrLogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_request_seerr_dark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Logout from Seerr",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                text = "Are you sure you want to logout from Seerr? You will need to login again to request content.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Logout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AboutSection(
    appVersion: String,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "About",
        icon = painterResource(id = R.drawable.ic_info_outlined),
        modifier = modifier
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_versions),
            title = "App Version",
            subtitle = appVersion,
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = painterResource(id = R.drawable.ic_code),
            title = "Build Type",
            subtitle = if (AppConstants.IS_DEBUG) "Debug" else "Release",
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = painterResource(id = R.drawable.ic_source_code),
            title = "Open Source Licenses",
            subtitle = "View licenses for open source libraries",
            onClick = onLicensesClick
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            content()
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
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
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
    enabled: Boolean = true
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = 0.5f
                    )
                )
            )
        }
    )
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_logout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Logout",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                text = "Are you sure you want to logout? You will need to login again to access your content.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Logout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}