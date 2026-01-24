package com.makd.afinity.ui.livetv.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.ui.livetv.LiveTvUiState
import com.makd.afinity.ui.livetv.components.EpgChannelCell
import com.makd.afinity.ui.livetv.components.EpgProgramRow
import com.makd.afinity.ui.livetv.components.EpgTimeHeader
import java.time.format.DateTimeFormatter

@Composable
fun LiveTvGuideTab(
    uiState: LiveTvUiState,
    onChannelClick: (AfinityChannel) -> Unit,
    onJumpToNow: () -> Unit,
    onNavigateTime: (Int) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val channelCellWidth: Dp = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 100.dp
        WindowWidthSizeClass.Medium -> 120.dp
        WindowWidthSizeClass.Expanded -> 150.dp
        else -> 100.dp
    }

    val hourWidth: Dp = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 180.dp
        WindowWidthSizeClass.Medium -> 200.dp
        WindowWidthSizeClass.Expanded -> 240.dp
        else -> 180.dp
    }

    val rowHeight: Dp = 70.dp
    val headerHeight: Dp = 40.dp

    val horizontalScrollState = rememberScrollState()
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    if (uiState.isEpgLoading && uiState.epgChannels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onNavigateTime(-3) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left),
                    contentDescription = "Previous"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.epgStartTime.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.epgStartTime.hour}:00 - ${
                        uiState.epgStartTime.plusHours(
                            uiState.epgVisibleHours.toLong()
                        ).hour
                    }:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onJumpToNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Now")
            }

            IconButton(onClick = { onNavigateTime(3) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = "Next"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.width(channelCellWidth)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .width(channelCellWidth)
                            .height(headerHeight)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }

                items(
                    items = uiState.epgChannels,
                    key = { it.id }
                ) { channel ->
                    EpgChannelCell(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        cellWidth = channelCellWidth,
                        cellHeight = rowHeight
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                EpgTimeHeader(
                    startTime = uiState.epgStartTime,
                    visibleHours = uiState.epgVisibleHours,
                    hourWidth = hourWidth
                )

                LazyColumn {
                    items(
                        items = uiState.epgChannels,
                        key = { it.id }
                    ) { channel ->
                        val channelPrograms = uiState.epgPrograms[channel.id] ?: emptyList()
                        EpgProgramRow(
                            channel = channel,
                            programs = channelPrograms,
                            epgStartTime = uiState.epgStartTime,
                            visibleHours = uiState.epgVisibleHours,
                            hourWidth = hourWidth,
                            cellHeight = rowHeight,
                            onProgramClick = {
                                onChannelClick(channel)
                            }
                        )
                    }
                }
            }
        }
    }
}