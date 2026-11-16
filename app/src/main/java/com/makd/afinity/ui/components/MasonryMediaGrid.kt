package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityFolder
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.media.AfinityPhoto
import com.makd.afinity.data.models.media.AfinityPhotoAlbum
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.ui.theme.CardDimensions

private const val MIN_TILE_RATIO = 0.5f
private const val MAX_TILE_RATIO = 2f

@Composable
fun MasonryMediaGrid(
    items: LazyPagingItems<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalStaggeredGrid(
        columns = CardDimensions.staggeredGridCells(),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        verticalItemSpacing = CardDimensions.RowGap,
        modifier = modifier,
    ) {
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            items[index]?.let { item -> GalleryTile(item = item, onClick = { onItemClick(item) }) }
        }

        when {
            items.loadState.append is LoadState.Loading -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
            items.loadState.append is LoadState.Error -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(onClick = { items.retry() }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MasonryItemGrid(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalStaggeredGrid(
        columns = CardDimensions.staggeredGridCells(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        verticalItemSpacing = CardDimensions.RowGap,
        modifier = modifier,
    ) {
        items(items, key = { it.id }) { item ->
            GalleryTile(item = item, onClick = { onItemClick(item) })
        }
    }
}

fun galleryTileRatio(item: AfinityItem): Float {
    val ratio =
        when (item) {
            is AfinityPhoto -> item.aspectRatio
            is AfinityPhotoAlbum -> item.aspectRatio
            is AfinityVideo,
            is AfinityMusicVideo -> CardDimensions.ASPECT_RATIO_LANDSCAPE
            else -> null
        } ?: CardDimensions.ASPECT_RATIO_SQUARE
    return ratio.coerceIn(MIN_TILE_RATIO, MAX_TILE_RATIO)
}

@Composable
fun GalleryTile(
    item: AfinityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixedRatio: Float? = null,
    galleryStyle: Boolean = true,
    showTitle: Boolean = !galleryStyle && item !is AfinityPhoto,
) {
    val ratio = fixedRatio ?: galleryTileRatio(item)
    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageUrl = item.images.primaryImageUrl
                if (imageUrl != null) {
                    AsyncImage(
                        imageUrl = imageUrl,
                        contentDescription = item.name,
                        blurHash = item.images.primaryBlurHash,
                        targetWidth = 200.dp,
                        targetHeight = 200.dp / ratio,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (item is AfinityPhotoAlbum || item is AfinityFolder) {
                                        R.drawable.ic_folder
                                    } else {
                                        R.drawable.ic_photo_search
                                    },
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    )
                }

                when (item) {
                    is AfinityVideo,
                    is AfinityMusicVideo ->
                        if (galleryStyle) GalleryVideoOverlay(item) else VideoTileOverlay(item)
                    is AfinityPhotoAlbum ->
                        if (galleryStyle) {
                            GalleryNameOverlay(name = item.name, count = item.childCount)
                        } else {
                            item.childCount
                                ?.takeIf { it > 0 }
                                ?.let { count ->
                                    MediaCountBadge(
                                        text = "$count",
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                        }
                    is AfinityFolder -> {
                        if (galleryStyle) GalleryNameOverlay(name = item.name, count = null)
                        item.unplayedItemCount
                            ?.takeIf { it > 0 }
                            ?.let { count ->
                                MediaCountBadge(
                                    text = "$count",
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                    }
                    else -> Unit
                }

                if (item.favorite && item !is AfinityVideo && item !is AfinityMusicVideo) {
                    FavoriteMark(modifier = Modifier.align(Alignment.TopStart))
                }
            }
        }

        if (showTitle) {
            Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.VideoTileOverlay(item: AfinityItem) {
    Surface(
        modifier = Modifier.align(Alignment.Center),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_player_play_filled),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(8.dp).size(20.dp),
        )
    }

    if (item.played) {
        PlayedBadge(modifier = Modifier.align(Alignment.TopEnd))
    } else if (item.playbackPositionTicks > 0 && item.runtimeTicks > 0) {
        LinearProgressIndicator(
            progress = {
                (item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat()).coerceIn(
                    0f,
                    1f,
                )
            },
            modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.3f),
        )
    }

    if (item.favorite) {
        FavoriteMark(modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun BoxScope.GalleryVideoOverlay(item: AfinityItem) {
    val showProgress = !item.played && item.playbackPositionTicks > 0 && item.runtimeTicks > 0

    if (item.runtimeTicks > 0) {
        Surface(
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = if (showProgress) 12.dp else 8.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
                Text(
                    text = formatClockTicks(item.runtimeTicks),
                    style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
        }
    }

    if (item.played) {
        PlayedBadge(modifier = Modifier.align(Alignment.TopEnd))
    } else if (showProgress) {
        LinearProgressIndicator(
            progress = {
                (item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat()).coerceIn(
                    0f,
                    1f,
                )
            },
            modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.3f),
        )
    }

    if (item.favorite) {
        FavoriteMark(modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun BoxScope.GalleryNameOverlay(name: String, count: Int?) {
    Column(
        modifier =
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        count
            ?.takeIf { it > 0 }
            ?.let {
                Text(
                    text = pluralStringResource(R.plurals.gallery_item_count, it, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
    }
}

@Composable
private fun FavoriteMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_favorite_filled),
            contentDescription = stringResource(R.string.filter_favorites),
            tint = Color.White,
            modifier = Modifier.padding(5.dp).size(14.dp),
        )
    }
}
