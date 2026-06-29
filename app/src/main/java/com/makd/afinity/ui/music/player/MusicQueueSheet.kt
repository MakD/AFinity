package com.makd.afinity.ui.music.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityTrack
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQueueSheet(
    onDismiss: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTrack = queue.getOrNull(currentIndex)
    val nextTracks =
        if (currentIndex + 1 < queue.size) queue.subList(currentIndex + 1, queue.size)
        else emptyList()

    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val absoluteFrom = currentIndex + 1 + from.index
            val absoluteTo = currentIndex + 1 + to.index
            viewModel.moveInQueue(absoluteFrom, absoluteTo)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        onDismiss()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (currentTrack != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                    Text(
                        text = "Now Playing",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                    QueueTrackRow(
                        track = currentTrack,
                        isCurrentTrack = true,
                        onClick = { onDismiss() },
                    )
                }
            }

            if (nextTracks.isNotEmpty()) {
                Text(
                    text = "Next In Queue",
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(nextTracks, key = { index, _ -> currentIndex + 1 + index }) {
                        localIndex,
                        track ->
                        val absoluteIndex = currentIndex + 1 + localIndex
                        ReorderableItem(reorderState, key = currentIndex + 1 + localIndex) {
                            isDragging ->
                            val elevation by
                                animateDpAsState(
                                    if (isDragging) 8.dp else 0.dp,
                                    label = "drag_elev",
                                )

                            val dismissState = rememberSwipeToDismissBoxState()

                            LaunchedEffect(dismissState.currentValue) {
                                if (
                                    dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                                ) {
                                    viewModel.removeFromQueue(absoluteIndex)
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val color by
                                        animateColorAsState(
                                            targetValue =
                                                when (dismissState.targetValue) {
                                                    SwipeToDismissBoxValue.EndToStart ->
                                                        MaterialTheme.colorScheme.errorContainer
                                                            .copy(alpha = 0.6f)
                                                    else -> Color.Transparent
                                                },
                                            label = "dismiss_color",
                                        )
                                    val iconAlpha by
                                        animateFloatAsState(
                                            targetValue =
                                                if (
                                                    dismissState.targetValue ==
                                                        SwipeToDismissBoxValue.EndToStart
                                                )
                                                    1f
                                                else 0f,
                                            label = "dismiss_alpha",
                                        )

                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .background(color)
                                                .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = "Remove",
                                            tint =
                                                MaterialTheme.colorScheme.onErrorContainer.copy(
                                                    alpha = iconAlpha
                                                ),
                                        )
                                    }
                                },
                            ) {
                                QueueTrackRow(
                                    track = track,
                                    isCurrentTrack = false,
                                    onClick = { viewModel.skipToIndex(absoluteIndex) },
                                    dragHandleModifier = Modifier.draggableHandle(),
                                    elevation = elevation,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: AfinityTrack,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        color =
            if (elevation > 0.dp) MaterialTheme.colorScheme.surfaceContainerHigh
            else Color.Transparent,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                com.makd.afinity.ui.components.AsyncImage(
                    imageUrl = track.images.primary?.toString(),
                    contentDescription = null,
                    blurHash = track.images.primaryImageBlurHash,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().aspectRatio(1f),
                    targetWidth = 48.dp,
                    targetHeight = 48.dp,
                )
                if (isCurrentTrack) {
                    Box(
                        modifier =
                            Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_volume_up),
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium
                        ),
                    color =
                        if (isCurrentTrack) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val subtitle =
                    listOfNotNull(track.artist ?: track.artists.firstOrNull(), track.album)
                        .joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!isCurrentTrack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrows_sort),
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier =
                        dragHandleModifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            .size(24.dp),
                )
            }
        }
    }
}
