package com.makd.afinity.ui.audiobookshelf.libraries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.coverUrl

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
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
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
                    if (isPodcast) item.numEpisodesIncomplete == 0
                    else progress?.isFinished == true
                val unheardCount = item.numEpisodesIncomplete ?: 0

                when {
                    isFinished -> {
                        Box(
                            modifier =
                                Modifier.align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = stringResource(R.string.cd_finished),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    unheardCount > 0 -> {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = if (unheardCount > 99) "99+ EP" else "$unheardCount EP",
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (!isFinished && progress != null && !progress.isFinished && progress.progress > 0) {
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
