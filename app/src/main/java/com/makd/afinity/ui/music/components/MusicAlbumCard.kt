package com.makd.afinity.ui.music.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.ui.components.AsyncImage

@Composable
fun MusicAlbumCard(
    album: AfinityAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(8.dp),
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
        modifier = modifier.width(size).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
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
