package com.makd.afinity.ui.settings.servers.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.settings.servers.AudiobookshelfStats
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.components.ActiveConnectionCard
import com.makd.afinity.ui.settings.servers.components.AddAddressField
import com.makd.afinity.ui.settings.servers.components.LoadingState
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.ui.settings.servers.components.ServiceAddressItem
import com.makd.afinity.ui.settings.servers.components.StatChip
import com.makd.afinity.ui.settings.servers.utils.formatListeningDuration
import java.util.UUID

@Composable
internal fun AudiobookshelfTabContent(
    serverWithCount: ServerWithUserCount,
    absStats: AudiobookshelfStats?,
    statsLoading: Boolean,
    onManageClick: () -> Unit,
) {
    if (statsLoading) {
        LoadingState()
    } else if (absStats != null) {
        val hours = (absStats.totalListeningTimeSeconds / 3600).toInt()
        val minutes = ((absStats.totalListeningTimeSeconds % 3600) / 60).toInt()

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.abs_total_listening_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hours",
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black
                            ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.abs_hours_unit),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp, end = 8.dp),
                    )
                    Text(
                        text = "$minutes",
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black
                            ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.abs_minutes_unit),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatChip(
                label = stringResource(R.string.time_today),
                value = formatListeningDuration(absStats.todaySeconds),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = stringResource(R.string.time_this_week),
                value = formatListeningDuration(absStats.weekSeconds),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = stringResource(R.string.time_this_month),
                value = formatListeningDuration(absStats.monthSeconds),
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_activity))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_current_streak),
                    value = "${absStats.currentStreak}${stringResource(R.string.abs_days_unit)}",
                    iconRes = R.drawable.ic_bolt,
                    iconTint = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_best_streak),
                    value = "${absStats.longestStreak}${stringResource(R.string.abs_days_unit)}",
                    iconRes = R.drawable.ic_star,
                    iconTint = Color(0xFFFFC107),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.abs_finished),
                    value = absStats.finishedCount.toString(),
                    iconRes = R.drawable.ic_circle_check,
                    iconTint = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_days_active),
                    value = absStats.activeDays.toString(),
                    iconRes = R.drawable.ic_calendar,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_library_header))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_items),
                    value = absStats.totalItems.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_content),
                    value =
                        stringResource(R.string.duration_hours_fmt, absStats.totalDurationHours),
                    modifier = Modifier.weight(1f),
                )
            }

            val audiobookCount =
                absStats.libraries
                    .filter { it.mediaType == "book" }
                    .sumOf { it.stats?.totalItems ?: 0 }
            val podcastCount =
                absStats.libraries
                    .filter { it.mediaType == "podcast" }
                    .sumOf { it.stats?.totalItems ?: 0 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_audiobooks),
                    value = audiobookCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_podcasts),
                    value = podcastCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_in_progress),
                    value = absStats.inProgressCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_libraries),
                    value = absStats.libraries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.section_network))
        val activeAddress =
            serverWithCount.audiobookshelfConnectionUrl
                ?: serverWithCount.audiobookshelfAddresses.firstOrNull()?.address
                ?: stringResource(R.string.server_default_proxy)
        val total = serverWithCount.audiobookshelfAddresses.size
        ActiveConnectionCard(
            activeAddress = activeAddress,
            totalAddresses = total,
            onManageClick = onManageClick,
        )
    }
}

@Composable
internal fun AudiobookshelfManageAddresses(
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onAddAddress: (String) -> Unit,
) {
    if (serverWithCount.audiobookshelfAddresses.isNotEmpty()) {
        serverWithCount.audiobookshelfAddresses.forEach { address ->
            ServiceAddressItem(
                address = address.address,
                onDelete = { onDeleteAddress(address.id) },
            )
        }
    } else {
        Text(
            text = stringResource(R.string.server_no_alternate_addresses),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    AddAddressField(
        placeholder = stringResource(R.string.abs_placeholder_server_url),
        onAdd = onAddAddress,
    )
}
