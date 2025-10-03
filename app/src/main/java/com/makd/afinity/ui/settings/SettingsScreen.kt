package com.makd.afinity.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.BuildConfig
import com.makd.afinity.core.AppConstants
import com.makd.afinity.ui.components.OptimizedAsyncImage
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

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
                            imageVector = Icons.Default.ArrowBack,
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
                item {
                    ProfileHeader(
                        userName = uiState.currentUser?.name ?: "Unknown User",
                        serverInfo = uiState.serverInfo,
                        userProfileImageUrl = uiState.userProfileImageUrl
                    )
                }

                item {
                    AccountSection(
                        userName = uiState.currentUser?.name ?: "Unknown User",
                        serverId = uiState.currentUser?.serverId ?: "Unknown Server",
                        onLogoutClick = { showLogoutDialog = true },
                        isLoggingOut = uiState.isLoggingOut
                    )
                }

                item {
                    AppearanceSection(
                        darkTheme = uiState.darkTheme,
                        dynamicColors = uiState.dynamicColors,
                        onDarkThemeToggle = viewModel::toggleDarkTheme,
                        onDynamicColorsToggle = viewModel::toggleDynamicColors
                    )
                }

                item {
                    PlaybackSection(
                        autoPlay = uiState.autoPlay,
                        skipIntroEnabled = uiState.skipIntroEnabled,
                        skipOutroEnabled = uiState.skipOutroEnabled,
                        useExoPlayer = uiState.useExoPlayer,
                        onAutoPlayToggle = viewModel::toggleAutoPlay,
                        onSkipIntroToggle = viewModel::toggleSkipIntro,
                        onSkipOutroToggle = viewModel::toggleSkipOutro,
                        onUseExoPlayerToggle = viewModel::toggleUseExoPlayer
                    )
                }

                item {
                    AboutSection(
                        appVersion = AppConstants.VERSION_NAME
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
    serverInfo: String?,
    userProfileImageUrl: String?,
    modifier: Modifier = Modifier
) {
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
                    OptimizedAsyncImage(
                        imageUrl = userProfileImageUrl,
                        contentDescription = "Profile Picture",
                        targetWidth = 64.dp,
                        targetHeight = 64.dp,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
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
                Text(
                    text = serverInfo ?: "Jellyfin User",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AccountSection(
    userName: String,
    serverId: String,
    onLogoutClick: () -> Unit,
    isLoggingOut: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Account",
        icon = Icons.Outlined.AccountCircle,
        modifier = modifier
    ) {
        SettingsItem(
            icon = Icons.Outlined.Person,
            title = "Username",
            subtitle = userName,
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "Server ID",
            subtitle = serverId,
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                    imageVector = Icons.Outlined.Logout,
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
    darkTheme: Boolean,
    dynamicColors: Boolean,
    onDarkThemeToggle: (Boolean) -> Unit,
    onDynamicColorsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Appearance",
        icon = Icons.Outlined.Palette,
        modifier = modifier
    ) {
        SettingsSwitchItem(
            icon = Icons.Outlined.DarkMode,
            title = "Dark Theme",
            subtitle = "Use dark color scheme",
            checked = darkTheme,
            onCheckedChange = onDarkThemeToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsSwitchItem(
            icon = Icons.Outlined.Colorize,
            title = "Dynamic Colors",
            subtitle = "Use colors from wallpaper",
            checked = dynamicColors,
            onCheckedChange = onDynamicColorsToggle
        )
    }
}

@Composable
private fun PlaybackSection(
    autoPlay: Boolean,
    skipIntroEnabled: Boolean,
    skipOutroEnabled: Boolean,
    useExoPlayer: Boolean,
    onAutoPlayToggle: (Boolean) -> Unit,
    onSkipIntroToggle: (Boolean) -> Unit,
    onSkipOutroToggle: (Boolean) -> Unit,
    onUseExoPlayerToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "Playback",
        icon = Icons.Outlined.PlayCircle,
        modifier = modifier
    ) {
        SettingsSwitchItem(
            icon = Icons.Outlined.Videocam,
            title = "Use ExoPlayer",
            subtitle = "Restart required. Uses LibMPV when disabled",
            checked = useExoPlayer,
            onCheckedChange = onUseExoPlayerToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        SettingsSwitchItem(
            icon = Icons.Outlined.PlayArrow,
            title = "Auto-play",
            subtitle = "Automatically play next episode",
            checked = autoPlay,
            onCheckedChange = onAutoPlayToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsSwitchItem(
            icon = Icons.Outlined.SkipNext,
            title = "Skip Intro",
            subtitle = "Automatically skip intros",
            checked = skipIntroEnabled,
            onCheckedChange = onSkipIntroToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsSwitchItem(
            icon = Icons.Outlined.FastForward,
            title = "Skip Outro",
            subtitle = "Automatically skip outros",
            checked = skipOutroEnabled,
            onCheckedChange = onSkipOutroToggle
        )
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = "About",
        icon = Icons.Outlined.Info,
        modifier = modifier
    ) {
        SettingsItem(
            icon = Icons.Outlined.AppSettingsAlt,
            title = "App Version",
            subtitle = appVersion,
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = Icons.Outlined.Code,
            title = "Build Type",
            subtitle = if (AppConstants.IS_DEBUG) "Debug" else "Release",
            onClick = null
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "Version Code",
            subtitle = AppConstants.VERSION_CODE.toString(),
            onClick = null
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
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
                    imageVector = icon,
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
    icon: ImageVector,
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
            imageVector = icon,
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
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
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
                imageVector = Icons.Outlined.Logout,
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