package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.getChapterImageUrl
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale
import java.util.UUID

private val ChapterCardWidth = 220.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChapterSwitcher(
    chapters: List<AfinityChapter>,
    currentPosition: Long,
    itemId: UUID,
    baseUrl: String,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChapterIndex =
        remember(chapters, currentPosition) {
            chapters.indexOfLast { it.startPosition <= currentPosition }.coerceAtLeast(0)
        }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeChapterIndex)

    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(activeChapterIndex) {
        listState.animateScrollToItem(activeChapterIndex)
    }

    Box(
        modifier =
            modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.systemBarsIgnoringVisibility.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)) {
                    LazyRow(
                        state = listState,
                        flingBehavior = flingBehavior,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        itemsIndexed(
                            items = chapters,
                            key = { _, chapter -> chapter.startPosition },
                        ) { index, chapter ->
                            ChapterSwitcherCard(
                                chapter = chapter,
                                index = index,
                                itemId = itemId,
                                baseUrl = baseUrl,
                                isCurrent = index == activeChapterIndex,
                                onClick = { onChapterClick(chapter.startPosition) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterSwitcherCard(
    chapter: AfinityChapter,
    index: Int,
    itemId: UUID,
    baseUrl: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val title = chapter.name ?: stringResource(R.string.chapter_number_fmt, index + 1)
    val imageUrl = chapter.getChapterImageUrl(baseUrl, itemId)

    Column(
        modifier = Modifier.width(ChapterCardWidth).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = if (isCurrent) 3.dp else 0.dp,
                        color =
                            if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = ChapterCardWidth,
                    targetHeight = 124.dp,
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                    ),
                                startY = 60f,
                            )
                        )
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = formatChapterTime(chapter.startPosition),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

private fun formatChapterTime(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
