package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.ui.components.AsyncImage

private val GENRE_CARD_GRADIENTS =
    listOf(
        Color(0xFFB1AADA) to Color(0xFF5A5482),
        Color(0xFFE2AE95) to Color(0xFF965243),
        Color(0xFFA4C4D6) to Color(0xFF50787A),
        Color(0xFFAED1AE) to Color(0xFF4F7E70),
        Color(0xFFD4A5C9) to Color(0xFF7B4F8C),
    )

@Composable
fun MusicPlaylistCard(
    playlist: AfinityPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicAlbumCard(
        album =
            AfinityAlbum(
                id = playlist.id,
                name = playlist.name,
                artistId = null,
                artist = null,
                artists = emptyList(),
                productionYear = null,
                songCount = playlist.songCount,
                runtimeTicks = playlist.runtimeTicks,
                genres = emptyList(),
                overview = playlist.overview,
                favorite = playlist.favorite,
                played = false,
                playCount = null,
                images = playlist.images,
            ),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun MusicAlbumCard(
    album: AfinityAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            AsyncImage(
                imageUrl = album.images.primary?.toString(),
                contentDescription = album.name,
                blurHash = album.images.primaryImageBlurHash,
                targetWidth = 160.dp,
                targetHeight = 160.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle =
                listOfNotNull(
                        album.artist ?: album.artists.firstOrNull(),
                        album.productionYear?.toString(),
                    )
                    .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
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

@Composable
fun MusicArtistCard(
    name: String,
    imageUrl: String?,
    blurHash: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 140.dp,
) {
    val imageSize = size * 0.86f
    Column(
        modifier =
            modifier
                .width(size)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = name,
            blurHash = blurHash,
            targetWidth = imageSize,
            targetHeight = imageSize,
            modifier = Modifier.size(imageSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_person_placeholder),
            error = painterResource(R.drawable.ic_person_placeholder),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MusicGenreCard(
    genre: AfinityMusicGenre,
    index: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            if (genre.imageUrl != null) {
                AsyncImage(
                    imageUrl = genre.imageUrl,
                    contentDescription = genre.name,
                    blurHash = null,
                    targetWidth = 160.dp,
                    targetHeight = 160.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val (gradStart, gradEnd) = GENRE_CARD_GRADIENTS[index % GENRE_CARD_GRADIENTS.size]
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Brush.linearGradient(listOf(gradStart, gradEnd))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_genre),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Text(
            text = genre.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
