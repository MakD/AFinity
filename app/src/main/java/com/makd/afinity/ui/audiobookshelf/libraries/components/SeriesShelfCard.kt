package com.makd.afinity.ui.audiobookshelf.libraries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.coverUrl
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.MediaCountBadge

private const val MAX_FANNED_COVERS = 5

@Composable
fun SeriesShelfCard(
    series: AudiobookshelfSeries,
    serverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val coverPx = (screenWidthPx / 2).coerceAtLeast(100)
    val coverUrls =
        if (serverUrl != null) {
            series.books.take(MAX_FANNED_COVERS).map { it.coverUrl(serverUrl, width = coverPx) }
        } else {
            emptyList()
        }
    val totalBooks = series.numBooks ?: series.books.size

    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onClick)
        ) {
            when {
                coverUrls.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                coverUrls.size == 1 -> {
                    AsyncImage(
                        imageUrl = coverUrls[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(12.dp),
                        contentScale = ContentScale.Crop,
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        FannedCover(
                            imageUrl = coverUrls[0],
                            contentDescription = series.name,
                            modifier = Modifier.size(maxHeight).align(Alignment.Center),
                        )
                    }
                }

                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val coverSize = maxHeight
                        val step = (maxWidth - coverSize) / (coverUrls.size - 1)
                        for (index in coverUrls.indices.reversed()) {
                            FannedCover(
                                imageUrl = coverUrls[index],
                                contentDescription = if (index == 0) series.name else null,
                                modifier = Modifier.size(coverSize).offset(x = step * index),
                            )
                        }
                    }
                }
            }

            if (totalBooks > 0) {
                MediaCountBadge(
                    text = "$totalBooks",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = series.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FannedCover(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val coverShape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .shadow(3.dp, coverShape)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.White.copy(alpha = 0.2f), coverShape)
    ) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}