package com.makd.afinity.ui.settings.servers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ServerStorage
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.settings.servers.tabs.AudiobookshelfTabContent
import com.makd.afinity.ui.settings.servers.tabs.JellyfinTabContent
import com.makd.afinity.ui.settings.servers.tabs.JellyseerrTabContent

private enum class DetailTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val activeIconRes: Int,
    @param:DrawableRes val inactiveIconRes: Int,
    val isBrandColored: Boolean,
) {
    JELLYFIN(R.string.tab_jellyfin, R.drawable.ic_jellyfin, R.drawable.ic_jellyfin_light, true),
    JELLYSEERR(
        R.string.tab_seerr,
        R.drawable.ic_seerr_logo_colored,
        R.drawable.ic_seerr_logo,
        true,
    ),
    AUDIOBOOKSHELF(
        R.string.tab_abs,
        R.drawable.ic_audiobookshelf_colored,
        R.drawable.ic_audiobookshelf_light,
        true,
    ),
}

@Composable
internal fun ServerDetailContent(
    serverWithCount: ServerWithUserCount,
    stats: ServerDetailStats?,
    statsLoading: Boolean,
    serverStorage: ServerStorage?,
    isAdmin: Boolean?,
    onDismiss: () -> Unit,
    onControlPanelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = serverWithCount.currentUserServiceStatus
    val tabs = buildList {
        add(DetailTab.JELLYFIN)
        if (status.jellyseerrConfigured) add(DetailTab.JELLYSEERR)
        if (status.audiobookshelfConfigured) add(DetailTab.AUDIOBOOKSHELF)
    }

    var selectedTabIndex by remember(serverWithCount.server.id) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(14.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverWithCount.server.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (serverWithCount.server.version != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.server_version_fmt,
                                serverWithCount.server.version,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (tabs.size > 1) {
            SegmentedTabBar(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Crossfade(
            targetState = tabs.getOrNull(selectedTabIndex) ?: DetailTab.JELLYFIN,
            animationSpec = tween(300),
            modifier = Modifier.weight(1f),
            label = "TabContentCrossfade",
        ) { currentTab ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (currentTab) {
                    DetailTab.JELLYFIN ->
                        JellyfinTabContent(
                            serverWithCount = serverWithCount,
                            jellyfinStats = stats?.jellyfinStats,
                            statsLoading = statsLoading,
                            serverStorage = serverStorage,
                            isAdmin = isAdmin,
                            onControlPanelClick = onControlPanelClick,
                        )
                    DetailTab.JELLYSEERR ->
                        JellyseerrTabContent(
                            serverWithCount = serverWithCount,
                            jellyseerrStats = stats?.jellyseerrStats,
                            statsLoading = statsLoading,
                        )
                    DetailTab.AUDIOBOOKSHELF ->
                        AudiobookshelfTabContent(
                            serverWithCount = serverWithCount,
                            absStats = stats?.audiobookshelfStats,
                            statsLoading = statsLoading,
                        )
                }
            }
        }
    }
}

@Composable
internal fun ServerDetailDialog(
    serverWithCount: ServerWithUserCount,
    stats: ServerDetailStats?,
    statsLoading: Boolean,
    serverStorage: ServerStorage?,
    isAdmin: Boolean?,
    onDismiss: () -> Unit,
    onControlPanelClick: () -> Unit,
) {
    val dialogHeight = minOf(750.dp, windowHeightDp() * 0.92f)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth().widthIn(max = 560.dp).height(dialogHeight).padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            ServerDetailContent(
                serverWithCount = serverWithCount,
                stats = stats,
                statsLoading = statsLoading,
                serverStorage = serverStorage,
                isAdmin = isAdmin,
                onDismiss = onDismiss,
                onControlPanelClick = onControlPanelClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SegmentedTabBar(
    tabs: List<DetailTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTabIndex
                val animatedBgColor by
                    animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        label = "tabBgColor",
                    )
                val animatedContentColor by
                    animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabContentColor",
                    )
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(animatedBgColor)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id = if (isSelected) tab.activeIconRes else tab.inactiveIconRes
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint =
                                if (tab.isBrandColored && isSelected) Color.Unspecified
                                else animatedContentColor,
                        )
                        Text(
                            text = stringResource(tab.labelRes),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = animatedContentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
