package com.makd.afinity.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    navController: androidx.navigation.NavController? = null,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerWrapperViewModel = hiltViewModel()
) {
    val item by viewModel.item.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    when {
        isLoading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                androidx.compose.material3.Text(
                    text = "Failed to load media",
                    color = Color.White
                )
            }
        }
    }
}