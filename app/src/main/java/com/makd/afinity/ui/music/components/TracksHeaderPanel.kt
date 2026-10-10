package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.music.library.MusicSortField

private val CenterCoverSize = 112.dp
private val InnerCoverSize = 88.dp
private val OuterCoverSize = 68.dp
private val InnerCoverStep = 60.dp
private val OuterCoverStep = 40.dp
private val CenterCoverShape = RoundedCornerShape(18.dp)
private val InnerCoverShape = RoundedCornerShape(14.dp)
private val OuterCoverShape = RoundedCornerShape(12.dp)
private val CoverBorderWidth = 3.dp
private val SingleRowMinWidth = 600.dp
private const val SINGLE_ROW_STACK_SCALE = 0.86f
private const val MAX_STACK_COVERS = 5
private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
fun TracksHeaderPanel(
    coverUrls: List<String>,
    title: String,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingActions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val isLandscape = isLandscapeWindow()
        val covers = coverUrls.take(MAX_STACK_COVERS)

        BoxWithConstraints {
            if (isLandscape && maxWidth >= SingleRowMinWidth) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackCoverStack(coverUrls = covers, maxScale = SINGLE_ROW_STACK_SCALE)
                    TracksHeaderText(
                        title = title,
                        subtitle = subtitle,
                        centered = false,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = leadingActions,
                        )
                        MusicDetailActionRow(
                            onShuffle = onShuffleAll,
                            onPlay = onPlayAll,
                            leadingActions = {},
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TrackCoverStack(
                        coverUrls = covers,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    TracksHeaderText(
                        title = title,
                        subtitle = subtitle,
                        centered = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    MusicDetailActionRow(
                        onShuffle = onShuffleAll,
                        onPlay = onPlayAll,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp),
                        leadingActions = leadingActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun TracksHeaderText(
    title: String,
    subtitle: String?,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
            )
        }
    }
}

@Composable
fun SortFilterHeaderActions(
    onSortClick: () -> Unit,
    onFilterClick: () -> Unit,
    filterActive: Boolean,
) {
    IconButton(onClick = onSortClick) {
        Icon(
            painter = painterResource(R.drawable.ic_arrows_sort),
            contentDescription = stringResource(R.string.cd_sort_fab),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = onFilterClick) {
        Icon(
            painter =
                painterResource(
                    if (filterActive) R.drawable.ic_filter_active else R.drawable.ic_filter
                ),
            contentDescription = stringResource(R.string.cd_filter_fab),
            tint =
                if (filterActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun musicSortSummary(field: MusicSortField, descending: Boolean): String {
    val label = stringResource(field.labelRes)
    if (field == MusicSortField.Random) return label
    val direction =
        stringResource(if (descending) R.string.sort_descending else R.string.sort_ascending)
    return "$label · $direction"
}

@Composable
fun musicTotalRuntimeLabel(runtimeTicks: Long): String {
    val totalMinutes = runtimeTicks / TICKS_PER_MINUTE
    return if (totalMinutes >= 60) {
        stringResource(
            R.string.meta_runtime_hours_minutes,
            (totalMinutes / 60).toInt(),
            (totalMinutes % 60).toInt(),
        )
    } else {
        stringResource(R.string.meta_runtime_minutes, totalMinutes.toInt())
    }
}

@Composable
fun musicFiltersSummary(filters: MusicFilters): String? {
    if (!filters.isActive) return null
    val parts = buildList {
        if (filters.favoritesOnly) add(stringResource(R.string.filter_favorites))
        if (filters.unplayedOnly) add(stringResource(R.string.filter_unplayed))
        if (filters.playedOnly) add(stringResource(R.string.filter_played))
        addAll(filters.genres.sorted())
        addAll(filters.years.sortedDescending().map { it.toString() })
    }
    return parts.joinToString(", ")
}

@Composable
private fun TrackCoverStack(
    coverUrls: List<String>,
    modifier: Modifier = Modifier,
    maxScale: Float = 1f,
) {
    if (coverUrls.isEmpty()) {
        Box(
            modifier =
                Modifier.size(CenterCoverSize * maxScale)
                    .clip(CenterCoverShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        return
    }

    val hasRightInner = coverUrls.size >= 2
    val hasLeftInner = coverUrls.size >= 3
    val hasRightOuter = coverUrls.size >= 4
    val hasLeftOuter = coverUrls.size >= 5
    val leftExtent =
        (if (hasLeftInner) InnerCoverStep else 0.dp) + (if (hasLeftOuter) OuterCoverStep else 0.dp)
    val rightExtent =
        (if (hasRightInner) InnerCoverStep else 0.dp) +
            (if (hasRightOuter) OuterCoverStep else 0.dp)
    val fullWidth = leftExtent + CenterCoverSize + rightExtent

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = (maxWidth / fullWidth).coerceAtMost(maxScale)

        Box(modifier = Modifier.size(width = fullWidth * scale, height = CenterCoverSize * scale)) {
            if (hasLeftOuter) {
                StackCover(
                    url = coverUrls[4],
                    size = OuterCoverSize * scale,
                    shape = OuterCoverShape,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            if (hasRightOuter) {
                StackCover(
                    url = coverUrls[3],
                    size = OuterCoverSize * scale,
                    shape = OuterCoverShape,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            if (hasLeftInner) {
                StackCover(
                    url = coverUrls[2],
                    size = InnerCoverSize * scale,
                    shape = InnerCoverShape,
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .offset(x = (leftExtent - InnerCoverStep) * scale),
                )
            }
            if (hasRightInner) {
                StackCover(
                    url = coverUrls[1],
                    size = InnerCoverSize * scale,
                    shape = InnerCoverShape,
                    modifier =
                        Modifier.align(Alignment.CenterEnd)
                            .offset(x = (InnerCoverStep - rightExtent) * scale),
                )
            }
            StackCover(
                url = coverUrls[0],
                size = CenterCoverSize * scale,
                shape = CenterCoverShape,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = leftExtent * scale),
            )
        }
    }
}

@Composable
private fun StackCover(url: String, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    AsyncImage(
        imageUrl = url,
        contentDescription = null,
        targetWidth = size,
        targetHeight = size,
        modifier =
            modifier
                .size(size)
                .border(CoverBorderWidth, MaterialTheme.colorScheme.surfaceContainer, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
}
