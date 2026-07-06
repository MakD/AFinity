package com.makd.afinity.ui.audiobookshelf.libraries.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.coverUrl
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.MediaCountBadge
import com.makd.afinity.ui.components.PlayedBadge

@Composable
fun AudiobookCard(
    item: LibraryItem,
    serverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val coverUrl =
        if (serverUrl != null && item.media.coverPath != null) {
            val coverPx = (screenWidthPx / 2).coerceAtLeast(100)
            item.coverUrl(serverUrl, width = coverPx)
        } else null

    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverUrl != null) {
                    AsyncImage(
                        imageUrl = coverUrl,
                        contentDescription =
                            stringResource(
                                R.string.cd_abs_cover_fmt,
                                item.media.metadata.title ?: "",
                            ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                val progress = item.userMediaProgress
                val isPodcast = item.mediaType.equals("podcast", ignoreCase = true)
                val isFinished =
                    if (isPodcast) item.numEpisodesIncomplete == 0 else progress?.isFinished == true
                val unheardCount = item.numEpisodesIncomplete ?: 0

                when {
                    isFinished -> {
                        PlayedBadge(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            contentDescription = stringResource(R.string.cd_finished),
                        )
                    }

                    unheardCount > 0 -> {
                        MediaCountBadge(
                            text = if (unheardCount > 99) "99+ EP" else "$unheardCount EP",
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                }

                if (
                    !isFinished && progress != null && !progress.isFinished && progress.progress > 0
                ) {
                    LinearProgressIndicator(
                        progress = { progress.progress.toFloat() },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.media.metadata.title ?: stringResource(R.string.unknown_title),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        item.media.metadata.authorName?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
