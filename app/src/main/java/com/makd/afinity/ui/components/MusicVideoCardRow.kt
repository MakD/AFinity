package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun MusicVideoCardRow(
    videos: List<AfinityMusicVideo>,
    onVideoClick: (AfinityMusicVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = CardDimensions.landscapeCardWidth
    val rowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_LANDSCAPE,
            metadataHeight = CardDimensions.subtitleLine,
        )
    CardRow(modifier = modifier.fillMaxWidth().height(rowHeight)) {
        items(videos, key = { it.id }) { video ->
            Box(modifier = Modifier.width(cardWidth)) {
                MediaItemGridCard(item = video, onClick = { onVideoClick(video) })
            }
        }
    }
}
