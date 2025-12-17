package com.makd.afinity.ui.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.getChapterImageUrl
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.InCollectionsSection
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import java.util.UUID

@Composable
fun MovieDetailContent(
    item: AfinityMovie,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityMovie, PlaybackSelection) -> Unit,
    navController: androidx.navigation.NavController
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TaglineSection(item = item)

        OverviewSection(item = item)

        DirectorSection(item = item)

        WriterSection(item = item)

        ExternalLinksSection(item = item)

        if (item.chapters.isNotEmpty()) {
            ChaptersSection(
                chapters = item.chapters,
                itemId = item.id,
                baseUrl = baseUrl,
                onChapterClick = { startPositionMs ->
                    onPlayClick(
                        item,
                        PlaybackSelection(
                            mediaSourceId = item.sources.firstOrNull()?.id ?: "",
                            audioStreamIndex = null,
                            subtitleStreamIndex = null,
                            videoStreamIndex = item.sources.firstOrNull()
                                ?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }?.index
                                ?: 0,
                            startPositionMs = startPositionMs
                        )
                    )
                }
            )
        }

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick
        )

        InCollectionsSection(
            boxSets = containingBoxSets,
            onBoxSetClick = { boxSet ->
                val route = Destination.createItemDetailRoute(boxSet.id.toString())
                navController.navigate(route)
            }
        )

        CastSection(
            item = item,
            onPersonClick = { personId ->
                val route = Destination.createPersonRoute(personId.toString())
                navController.navigate(route)
            }
        )
    }
}

@Composable
internal fun ChaptersSection(
    chapters: List<AfinityChapter>,
    itemId: UUID,
    baseUrl: String,
    onChapterClick: (Long) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(chapters) { index, chapter ->
                ChapterCard(
                    chapter = chapter,
                    index = index,
                    itemId = itemId,
                    baseUrl = baseUrl,
                    onClick = { onChapterClick(chapter.startPosition) }
                )
            }
        }
    }
}

@Composable
internal fun ChapterCard(
    chapter: AfinityChapter,
    index: Int,
    itemId: UUID,
    baseUrl: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                OptimizedAsyncImage(
                    imageUrl = chapter.getChapterImageUrl(baseUrl, itemId),
                    contentDescription = chapter.name ?: "Chapter ${index + 1}",
                    targetWidth = 200.dp,
                    targetHeight = 112.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = formatTime(chapter.startPosition),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Text(
            text = chapter.name ?: "Chapter ${index + 1}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatTime(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}