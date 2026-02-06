package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

@Composable
fun SpecialFeaturesSection(
    specialFeatures: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.landscapeWidth

    if (specialFeatures.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.special_features_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                items(specialFeatures) { feature ->
                    SpecialFeatureCard(
                        feature = feature,
                        onClick = { onItemClick(feature) },
                        cardWidth = cardWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialFeatureCard(feature: AfinityItem, onClick: () -> Unit, cardWidth: Dp) {
    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = feature.images.primaryImageUrl,
                    contentDescription = feature.name,
                    blurHash = feature.images.primaryBlurHash,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 9f / 16f,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = feature.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}
