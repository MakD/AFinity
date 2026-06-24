package com.makd.afinity.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
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
            preferredItemWidth = carouselHeight,
            modifier = Modifier.height(carouselHeight).padding(horizontal = 14.dp),
            itemSpacing = 8.dp,
        ) { index ->
            Box(
                modifier =
                    Modifier.height(carouselHeight)
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable { onItemClick(index) }
            ) {
                AsyncImage(
                    imageUrl = imageUrl(index),
                    contentDescription = itemTitle(index),
                    targetWidth = carouselHeight,
                    targetHeight = carouselHeight,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                val textAlpha by
                    animateFloatAsState(
                        targetValue = if (index == state.currentItem) 1f else 0f,
                        label = "carousel_text_alpha",
                    )
                Box(
                    modifier =
                        Modifier.fillMaxSize().alpha(textAlpha).drawWithCache {
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
                                drawRect(Color.Black.copy(alpha = 0.4f))
                                drawRect(gradient)
                                drawContent()
                            }
                        }
                ) {
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = itemTitle(index),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
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
}

@Composable
fun ContinueListeningSection(
    tracks: List<AfinityTrack>,
    onTrackClick: (AfinityTrack) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Continue Listening",
) {
    MusicCarouselSection(
        title = title,
        count = tracks.size,
        modifier = modifier,
        imageUrl = { tracks[it].images.primary?.toString() },
        itemTitle = { tracks[it].name },
        itemSubtitle = { tracks[it].artist ?: tracks[it].artists.firstOrNull() },
        onItemClick = { onTrackClick(tracks[it]) },
    )
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
fun MusicArtistHomeSection(
    artist: AfinityArtist,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicCarouselSection(
        title = "More from ${artist.name}",
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
fun MusicAlbumRowSection(
    title: String,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
) {
    if (albums.isEmpty()) return
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
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
fun MusicTrackRowSection(
    title: String,
    tracks: List<AfinityTrack>,
    onTrackClick: (AfinityTrack) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 140.dp,
) {
    if (tracks.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                Column(
                    modifier = Modifier.width(cardWidth).clickable { onTrackClick(track) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        AsyncImage(
                            imageUrl = track.images.primary?.toString(),
                            contentDescription = track.name,
                            blurHash = track.images.primaryImageBlurHash,
                            targetWidth = cardWidth,
                            targetHeight = cardWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(modifier = Modifier.padding(horizontal = 2.dp)) {
                        Text(
                            text = track.name,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = track.artist ?: track.artists.firstOrNull()
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
    val preferredItemWidth = (configuration.screenWidthDp * if (isLandscape) 0.58f else 0.75f).dp
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier =
                        Modifier.fillMaxSize().drawWithCache {
                            val gradient =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                    startY = size.height * 0.4f,
                                    endY = size.height,
                                )
                            onDrawWithContent {
                                drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(gradient)
                                drawContent()
                            }
                        }
                ) {
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
}
