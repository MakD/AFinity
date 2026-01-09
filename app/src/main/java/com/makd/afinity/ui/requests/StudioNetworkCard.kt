package com.makd.afinity.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.Network
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.rememberLandscapeCardWidth
import com.makd.afinity.util.BackdropTracker

@Composable
fun StudioCard(
    studio: Studio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1C)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OptimizedAsyncImage(
                    imageUrl = studio.getImageUrl(),
                    contentDescription = studio.name,
                    blurHash = null,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 9f / 16f,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun NetworkCard(
    network: Network,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1C)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OptimizedAsyncImage(
                    imageUrl = network.getImageUrl(),
                    contentDescription = network.name,
                    blurHash = null,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 9f / 16f,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun GenreCard(
    genre: GenreSliderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdropTracker: BackdropTracker? = null,
    isMovie: Boolean = true
) {
    val cardWidth = rememberLandscapeCardWidth()

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                genre.getDuotoneBackdropUrl(backdropTracker, isMovie)?.let { backdropUrl ->
                    OptimizedAsyncImage(
                        imageUrl = backdropUrl,
                        contentDescription = genre.name,
                        blurHash = null,
                        targetWidth = cardWidth,
                        targetHeight = cardWidth * 9f / 16f,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                )
            }
        }
    }
}