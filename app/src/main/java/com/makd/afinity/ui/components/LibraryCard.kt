package com.makd.afinity.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun LibraryCard(
    library: AfinityCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    targetWidth: Dp? = null,
    targetHeight: Dp? = null,
    titleMaxLines: Int = 2,
) {
    Column(
        modifier =
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                AsyncImage(
                    imageUrl = library.images.backdropImageUrl ?: library.images.primaryImageUrl,
                    contentDescription = library.name,
                    blurHash = library.images.backdropBlurHash ?: library.images.primaryBlurHash,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            if (library.type != CollectionType.Unknown) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                    Icon(
                        painter = libraryTypeIcon(library.type),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        Text(
            text = library.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun libraryTypeIcon(type: CollectionType): Painter =
    when (type) {
        CollectionType.Movies -> painterResource(id = R.drawable.ic_movie)
        CollectionType.TvShows -> painterResource(id = R.drawable.ic_tv)
        CollectionType.Music -> painterResource(id = R.drawable.ic_music)
        CollectionType.Books -> painterResource(id = R.drawable.ic_books)
        CollectionType.HomeVideos -> painterResource(id = R.drawable.ic_music_video)
        CollectionType.Playlists -> painterResource(id = R.drawable.ic_playlist)
        CollectionType.LiveTv -> painterResource(id = R.drawable.ic_live_tv_nav)
        CollectionType.BoxSets -> painterResource(id = R.drawable.ic_collection)
        CollectionType.Mixed -> painterResource(id = R.drawable.ic_mixed)
        CollectionType.Unknown -> painterResource(id = R.drawable.ic_folder)
    }