package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.BuildConfig
import com.makd.afinity.R
import com.makd.afinity.R.drawable.ic_launcher_monochrome
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.Destination

@Composable
fun AppNavigationDrawerContent(
    currentRoute: String?,
    userName: String?,
    userProfileImageUrl: String?,
    serverName: String?,
    connectionType: ConnectionType,
    isAdmin: Boolean,
    isOffline: Boolean,
    favoritesCount: Int,
    watchlistCount: Int,
    isJellyseerrAuthenticated: Boolean,
    isAudiobookshelfAuthenticated: Boolean,
    hasLiveTvAccess: Boolean,
    librariesInDrawer: Boolean,
    onDestinationClick: (Destination) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        windowInsets = WindowInsets(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(280.dp)
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.surfaceContainerLow,
                                        )
                                )
                        )
            )

            Column(
                modifier =
                    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier =
                            Modifier.size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (userProfileImageUrl != null) {
                            AsyncImage(
                                imageUrl = userProfileImageUrl,
                                contentDescription = stringResource(R.string.cd_profile_icon),
                                targetWidth = 64.dp,
                                targetHeight = 64.dp,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (!userName.isNullOrBlank()) {
                            Text(
                                text = userName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user_circle),
                                contentDescription = stringResource(R.string.cd_profile_icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = ic_launcher_monochrome),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = userName ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.drawer_admin_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = serverName ?: stringResource(R.string.app_name),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alignByBaseline(),
                                )
                                Text(
                                    text = connectionLabel(connectionType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.alignByBaseline(),
                                )
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier =
                                    Modifier.size(10.dp)
                                        .background(
                                            connectionIndicatorColor(connectionType),
                                            CircleShape,
                                        )
                            )
                        },
                        border = null,
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Destination.entries.forEach { destination ->
                    val visible =
                        when {
                            isOffline && destination != Destination.HOME -> false
                            destination == Destination.LIBRARIES -> librariesInDrawer && !isOffline
                            destination == Destination.FAVORITES -> favoritesCount > 0
                            destination == Destination.WATCHLIST -> watchlistCount > 0
                            destination == Destination.REQUESTS -> isJellyseerrAuthenticated
                            destination == Destination.AUDIOBOOKS -> isAudiobookshelfAuthenticated
                            destination == Destination.LIVE_TV -> hasLiveTvAccess
                            else -> true
                        }
                    if (!visible) return@forEach

                    val selected = currentRoute == destination.route
                    val badgeCount =
                        when (destination) {
                            Destination.FAVORITES -> favoritesCount
                            Destination.WATCHLIST -> watchlistCount
                            else -> 0
                        }

                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        selected = selected,
                        onClick = { onDestinationClick(destination) },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (selected) destination.selectedIconRes
                                            else destination.unselectedIconRes
                                    ),
                                contentDescription = destination.title,
                            )
                        },
                        badge = {
                            if (badgeCount > 0) {
                                Text(
                                    text = badgeCount.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    selected = false,
                    onClick = onSettingsClick,
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.drawer_app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun connectionLabel(connectionType: ConnectionType): String =
    when (connectionType) {
        ConnectionType.LOCAL -> stringResource(R.string.connection_label_local)
        ConnectionType.TAILSCALE -> stringResource(R.string.connection_label_tailscale)
        ConnectionType.REMOTE -> stringResource(R.string.connection_label_remote)
        ConnectionType.OFFLINE -> stringResource(R.string.connection_label_offline)
    }
