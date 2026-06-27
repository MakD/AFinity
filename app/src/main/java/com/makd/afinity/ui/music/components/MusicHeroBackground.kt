package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun MusicHeroBackground(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(radius = 30.dp).background(Color.Black),
                alpha = 0.6f,
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                startY = 100f,
                            )
                        )
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    }
}