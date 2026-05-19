package com.makd.afinity.ui.settings.servers.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.settings.servers.JellyfinStats
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.components.ActiveConnectionCard
import com.makd.afinity.ui.settings.servers.components.LoadingState
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.ui.settings.servers.components.SelectableAddressRow
import com.makd.afinity.ui.settings.servers.components.StatChip
import com.makd.afinity.ui.settings.servers.components.UserServiceRow
import java.util.UUID

@Composable
internal fun JellyfinTabContent(
    serverWithCount: ServerWithUserCount,
    jellyfinStats: JellyfinStats?,
    statsLoading: Boolean,
    isAdmin: Boolean?,
    onManageClick: () -> Unit,
    onControlPanelClick: () -> Unit,
) {
    val primaryAddress =
        serverWithCount.currentConnectionUrl.ifBlank { serverWithCount.server.address }
    val allAddressesCount = serverWithCount.addresses.size + 1

    if (statsLoading) {
        LoadingState()
    } else if (jellyfinStats != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_library_overview))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_movies),
                    value = jellyfinStats.movieCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_series),
                    value = jellyfinStats.seriesCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_episodes),
                    value = jellyfinStats.episodeCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_collections),
                    value = jellyfinStats.boxsetCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (serverWithCount.userServices.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_active_services))
            val currentUser =
                serverWithCount.userServices.find { it.userId == serverWithCount.currentUserId }
                    ?: serverWithCount.userServices.first()
            UserServiceRow(userInfo = currentUser)

            val otherCount = serverWithCount.userServices.size - 1
            if (otherCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "+$otherCount more user${if (otherCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (isAdmin == true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Administration")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        onControlPanelClick()
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_admin_panel_settings),
                            contentDescription = "Control Panel",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Open Control Panel",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.section_network))
        ActiveConnectionCard(
            activeAddress = primaryAddress,
            totalAddresses = allAddressesCount,
            onManageClick = onManageClick,
        )
    }
}

@Composable
internal fun JellyfinManageAddresses(
    allAddresses: List<String>,
    primaryAddress: String,
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onSetPrimary: (String) -> Unit,
) {
    allAddresses.forEach { address ->
        SelectableAddressRow(
            address = address,
            isPrimary = address == primaryAddress,
            onSelect = { if (address != primaryAddress) onSetPrimary(address) },
            onDelete =
                if (address != primaryAddress) {
                    val serverAddr = serverWithCount.addresses.find { it.address == address }
                    serverAddr?.let { { onDeleteAddress(it.id) } }
                } else null,
        )
    }
}
