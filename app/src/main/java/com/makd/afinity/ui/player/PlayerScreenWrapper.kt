package com.makd.afinity.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.R
import java.util.UUID

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerScreenWrapper(
    itemId: UUID,
    mediaSourceId: String,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
    startPositionMs: Long = 0L,
    seasonId: UUID? = null,
    shuffle: Boolean = false,
    isLiveChannel: Boolean = false,
    channelName: String? = null,
    liveStreamUrl: String? = null,
    navController: androidx.navigation.NavController? = null,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerWrapperViewModel = hiltViewModel()
) {
    val item by viewModel.item.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val fetchedStreamUrl by viewModel.liveStreamUrl.collectAsState()
    val streamError by viewModel.streamError.collectAsState()

    val defaultChannelName = stringResource(R.string.channel_default_name)
    LaunchedEffect(itemId, isLiveChannel, defaultChannelName) {
        if (isLiveChannel) {
            viewModel.loadLiveChannel(itemId, channelName ?: defaultChannelName)
        } else {
            viewModel.loadItem(itemId)
        }
    }

    val effectiveStreamUrl = if (isLiveChannel) fetchedStreamUrl else liveStreamUrl

    when {
        isLoading || (isLiveChannel && effectiveStreamUrl == null && streamError == null) -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    if (isLiveChannel && item != null) {
                        Text(
                            text = stringResource(R.string.player_live_tuning_fmt, item?.name ?: channelName ?: ""),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        streamError != null -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = streamError ?: stringResource(R.string.player_error_stream_load),
                    color = Color.White
                )
            }
        }

        item != null -> {
            PlayerScreen(
                item = item!!,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = startPositionMs,
                seasonId = seasonId,
                shuffle = shuffle,
                isLiveChannel = isLiveChannel,
                liveStreamUrl = effectiveStreamUrl,
                navController = navController,
                onBackPressed = onBackPressed,
                modifier = modifier
            )
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.player_error_media_load),
                    color = Color.White
                )
            }
        }
    }
}