package com.makd.afinity.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.music.components.MusicAlbumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicCarouselSection(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    imageUrl: (Int) -> String?,
    itemTitle: (Int) -> String,
    itemSubtitle: (Int) -> String?,
    onItemClick: (Int) -> Unit,
) {
    if (count == 0) return

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val portraitWidth = ((configuration.screenWidthDp - 92) / 3f).dp
    val preferredItemWidth =
        if (isLandscape) (configuration.screenWidthDp * 0.25f).dp else portraitWidth
    val carouselHeight = if (isLandscape) (configuration.screenHeightDp * 0.50f).dp else 195.dp
    val state = rememberCarouselState { count }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier.height(carouselHeight).padding(horizontal = 14.dp),
            itemSpacing = 8.dp,
        ) { index ->
            Box(
                modifier =
                    Modifier.height(carouselHeight)
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable { onItemClick(index) }
            ) {
                val textAlpha by
                    animateFloatAsState(
                        targetValue = if (index == state.currentItem) 1f else 0f,
                        label = "carousel_text_alpha",
                    )

                AsyncImage(
                    imageUrl = imageUrl(index),
                    contentDescription = itemTitle(index),
                    targetWidth = carouselHeight,
                    targetHeight = carouselHeight,
                    modifier =
                        Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge).drawWithCache {
                            val gradient =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.72f),
                                        ),
                                    startY = size.height * 0.45f,
                                    endY = size.height,
                                )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    alpha = textAlpha,
                                )
                                drawRect(brush = gradient, alpha = textAlpha)
                            }
                        },
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = itemTitle(index),
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = itemSubtitle(index)
                    if (!sub.isNullOrEmpty()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LatestAlbumsSection(
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recently Added",
) {
    MusicCarouselSection(
        title = title,
        count = albums.size,
        modifier = modifier,
        imageUrl = { albums[it].images.primary?.toString() },
        itemTitle = { albums[it].name },
        itemSubtitle = {
            listOfNotNull(
                    albums[it].artist ?: albums[it].artists.firstOrNull(),
                    albums[it].productionYear?.toString(),
                )
                .joinToString(" · ")
                .takeIf { s -> s.isNotEmpty() }
        },
        onItemClick = { onAlbumClick(albums[it]) },
    )
}

@Composable
fun MusicGenreHomeSection(
    name: String,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicCarouselSection(
        title = name,
        count = albums.size,
        modifier = modifier,
        imageUrl = { albums[it].images.primary?.toString() },
        itemTitle = { albums[it].name },
        itemSubtitle = {
            listOfNotNull(
                    albums[it].artist ?: albums[it].artists.firstOrNull(),
                    albums[it].productionYear?.toString(),
                )
                .joinToString(" · ")
                .takeIf { s -> s.isNotEmpty() }
        },
        onItemClick = { onAlbumClick(albums[it]) },
    )
}

@Composable
fun MostPlayedAlbumsSection(
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return

    val isLandscape =
        LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = if (isLandscape) 175.dp else 140.dp
    val rowHeight = cardWidth + 12.dp + 8.dp + 20.dp + 18.dp

    Column(modifier = modifier) {
        Text(
            text = "Most Played",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.height(rowHeight).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 10.dp),
        ) {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                MostPlayedAlbumCard(
                    album = album,
                    ranking = index + 1,
                    cardWidth = cardWidth,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

@Composable
private fun MostPlayedAlbumCard(
    album: AfinityAlbum,
    ranking: Int,
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(cardWidth)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Card(
                onClick = onClick,
                modifier = Modifier.fillMaxSize().offset(x = 16.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                AsyncImage(
                    imageUrl = album.images.primary?.toString(),
                    contentDescription = album.name,
                    blurHash = album.images.primaryImageBlurHash,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = ranking.toString(),
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                    ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomStart).offset(y = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.offset(x = 16.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            album.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MusicAlbumRowSection(
    title: String,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 14.dp,
) {
    if (albums.isEmpty()) return
    val isLandscape =
        LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = if (isLandscape) 175.dp else 140.dp
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = horizontalPadding, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                MusicAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsCarousel(
    artist: AfinityArtist,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val portraitWidth = (configuration.screenWidthDp - 76).dp
    val preferredItemWidth =
        if (isLandscape) (configuration.screenWidthDp * 0.58f).dp else portraitWidth
    val carouselHeight = if (isLandscape) (configuration.screenHeightDp * 0.55f).dp else 200.dp
    val state = rememberCarouselState { albums.size }

    Column(modifier = modifier) {
        Text(
            text = "More From ${artist.name}",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier.height(carouselHeight).padding(horizontal = 14.dp),
            itemSpacing = 8.dp,
        ) { index ->
            val album = albums[index]
            Box(
                modifier =
                    Modifier.height(carouselHeight)
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable { onAlbumClick(album) }
            ) {
                AsyncImage(
                    imageUrl = (album.images.backdrop ?: album.images.primary)?.toString(),
                    contentDescription = album.name,
                    targetWidth = preferredItemWidth,
                    targetHeight = carouselHeight,
                    modifier =
                        Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge).drawWithCache {
                            val gradient =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f),
                                        ),
                                    startY = size.height * 0.4f,
                                    endY = size.height,
                                )
                            onDrawWithContent {
                                drawContent()
                                drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(gradient)
                            }
                        },
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val logoUrl = artist.images.logo?.toString()
                    if (logoUrl != null) {
                        AsyncImage(
                            imageUrl = logoUrl,
                            contentDescription = artist.name,
                            blurHash = null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            targetWidth = LocalConfiguration.current.screenWidthDp.dp,
                            targetHeight = 48.dp,
                        )
                    } else {
                        Text(
                            text = artist.name,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text =
                            listOfNotNull(album.name, album.productionYear?.toString())
                                .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
