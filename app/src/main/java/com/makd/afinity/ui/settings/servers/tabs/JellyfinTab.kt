package com.makd.afinity.ui.settings.servers.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ServerStorage
import com.makd.afinity.data.models.server.StorageDevice
import com.makd.afinity.data.models.server.StorageFolder
import com.makd.afinity.data.models.server.StorageFolderKind
import com.makd.afinity.ui.settings.servers.JellyfinStats
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.components.LoadingState
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.ui.settings.servers.components.SelectableAddressRow
import com.makd.afinity.ui.settings.servers.components.StatChip
import com.makd.afinity.ui.settings.servers.components.UserServiceRow
import com.makd.afinity.util.formatFileSize
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun JellyfinTabContent(
    serverWithCount: ServerWithUserCount,
    jellyfinStats: JellyfinStats?,
    statsLoading: Boolean,
    serverStorage: ServerStorage?,
    isAdmin: Boolean?,
    onControlPanelClick: () -> Unit,
) {
    val counts = jellyfinStats?.displayCounts().orEmpty()

    if (statsLoading) {
        LoadingState()
    } else if (counts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_library_overview))
            counts.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { (labelRes, value) ->
                        StatChip(
                            label = stringResource(labelRes),
                            value = value.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
                        text =
                            pluralStringResource(
                                R.plurals.server_more_users,
                                otherCount,
                                otherCount,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (isAdmin == true && serverStorage != null) {
        ServerStorageSection(storage = serverStorage)
    }

    if (isAdmin == true) {
        val isActive = serverWithCount.isActiveServer
        val contentAlpha = if (isActive) 1f else 0.38f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.section_administration))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isActive) Modifier.clickable { onControlPanelClick() } else Modifier
                        ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_admin_panel_settings),
                            contentDescription = stringResource(R.string.cd_control_panel),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.action_open_control_panel),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                color =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            )
                            if (!isActive) {
                                Text(
                                    text = stringResource(R.string.control_panel_active_only),
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.6f
                                        ),
                                )
                            }
                        }
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun JellyfinStats.displayCounts(): List<Pair<Int, Int>> =
    listOf(
            R.string.stat_movies to movieCount,
            R.string.stat_series to seriesCount,
            R.string.stat_episodes to episodeCount,
            R.string.stat_collections to boxsetCount,
            R.string.stat_albums to albumCount,
            R.string.stat_songs to songCount,
            R.string.stat_artists to artistCount,
            R.string.stat_music_videos to musicVideoCount,
            R.string.stat_books to bookCount,
            R.string.stat_trailers to trailerCount,
            R.string.stat_programs to programCount,
            R.string.stat_all_items to itemCount,
        )
        .filter { (_, count) -> count > 0 }

@Composable
private fun ServerStorageSection(storage: ServerStorage) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.section_server_storage))
        storage.devices.forEach { device -> StorageDeviceCard(device = device) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageDeviceCard(device: StorageDevice) {
    val context = LocalContext.current
    val fraction = device.usedFraction
    val isTight = fraction >= 0.9f
    val barColor =
        if (isTight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = device.label,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.storage_percent_used_fmt,
                                (fraction * 100).roundToInt(),
                            ),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color =
                            if (isTight) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.storage_used_fmt,
                                formatFileSize(context, device.usedSpace),
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.storage_free_fmt,
                                formatFileSize(context, device.freeSpace),
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                device.libraries.forEach { library ->
                    StorageChip(label = library, emphasised = true)
                }
                device.folders.forEach { folder ->
                    StorageChip(label = folder.kindLabel(), emphasised = false)
                }
            }
        }
    }
}

@Composable
private fun StorageChip(label: String, emphasised: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (emphasised) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (emphasised) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun StorageFolder.kindLabel(): String =
    when (kind) {
        StorageFolderKind.PROGRAM_DATA -> stringResource(R.string.storage_folder_program_data)
        StorageFolderKind.METADATA -> stringResource(R.string.storage_folder_metadata)
        StorageFolderKind.TRANSCODING_TEMP ->
            stringResource(R.string.storage_folder_transcoding_temp)
        StorageFolderKind.CACHE -> stringResource(R.string.storage_folder_cache)
        StorageFolderKind.IMAGE_CACHE -> stringResource(R.string.storage_folder_image_cache)
        StorageFolderKind.LOGS -> stringResource(R.string.storage_folder_logs)
        StorageFolderKind.WEB -> stringResource(R.string.storage_folder_web)
        null -> path
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
