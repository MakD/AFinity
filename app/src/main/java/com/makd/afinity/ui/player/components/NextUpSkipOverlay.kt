package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale

@Composable
fun NextUpSkipOverlay(
    nextItem: AfinityItem?,
    segment: AfinitySegment,
    skipButtonText: String,
    onSkipClick: (AfinitySegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextEpisode = nextItem as? AfinityEpisode
    val overlayColor = Color.Black.copy(alpha = 0.9f)

    if (nextEpisode != null && segment.type == AfinitySegmentType.OUTRO) {
        Card(
            modifier = modifier.widthIn(max = 540.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = overlayColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.width(200.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    val primaryImageUrl = nextEpisode.images.primaryImageUrl
                    if (primaryImageUrl != null) {
                        AsyncImage(
                            imageUrl = primaryImageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            targetWidth = 200.dp,
                            targetHeight = 112.dp,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                "${nextEpisode.seriesName} • S${nextEpisode.parentIndexNumber}E${nextEpisode.indexNumber}",
                            style =
                                MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        if (LocalShowRatings.current) {
                            nextEpisode.communityRating?.let { rating ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_imdb_logo),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(Locale.ROOT, "%.1f", rating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = nextEpisode.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (nextEpisode.overview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextEpisode.overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SkipButton(
                            segment = segment,
                            skipButtonText = skipButtonText,
                            onClick = onSkipClick,
                        )
                    }
                }
            }
        }
    } else {
        SkipButton(
            segment = segment,
            skipButtonText = skipButtonText,
            onClick = onSkipClick,
            modifier = modifier,
        )
    }
}
