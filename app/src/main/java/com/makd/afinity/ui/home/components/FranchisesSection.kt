package com.makd.afinity.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.franchise.Franchise
import com.makd.afinity.data.models.franchise.FranchiseCard
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.LogoCleanupTransformation
import com.makd.afinity.ui.components.MediaCountBadge
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

@Composable
fun FranchisesSection(
    franchises: List<FranchiseCard>,
    onFranchiseClick: (Franchise) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_LANDSCAPE,
            titleHeight = 0.dp,
            metadataHeight = 0.dp,
        )

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(R.string.home_franchises))

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
            items(items = franchises, key = { it.franchise.id }) { card ->
                FranchiseCardItem(
                    card = card,
                    onClick = { onFranchiseClick(card.franchise) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun FranchiseCardItem(card: FranchiseCard, onClick: () -> Unit, cardWidth: Dp) {
    val cardHeight = cardWidth * 9f / 16f
    val images = card.backdropItem?.images
    val backdropUrl = images?.backdropImageUrl ?: images?.primaryImageUrl
    val blurHash =
        if (images?.backdropImageUrl != null) images.backdropBlurHash else images?.primaryBlurHash

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (backdropUrl != null) {
                    AsyncImage(
                        imageUrl = backdropUrl,
                        contentDescription = null,
                        blurHash = blurHash,
                        targetWidth = cardWidth,
                        targetHeight = cardHeight,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))
                )
                FranchiseLogo(
                    card = card,
                    logoWidth = cardWidth * LOGO_WIDTH_FRACTION,
                    logoHeight = cardHeight * LOGO_HEIGHT_FRACTION,
                    modifier = Modifier.align(Alignment.Center),
                )
                MediaCountBadge(
                    text = card.franchise.itemIds.size.toString(),
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Composable
private fun FranchiseLogo(
    card: FranchiseCard,
    logoWidth: Dp,
    logoHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val name = card.franchise.name
    var wikidataFailed by remember(card.franchise.logoUrl) { mutableStateOf(false) }
    val wikidataLogo = card.franchise.logoUrl?.takeUnless { wikidataFailed }
    val jellyfinLogo = card.logoItem?.images?.logoImageUrl

    when {
        wikidataLogo != null -> {
            val context = LocalPlatformContext.current
            val request =
                remember(wikidataLogo) {
                    ImageRequest.Builder(context)
                        .data(wikidataLogo)
                        .allowHardware(false)
                        .transformations(LogoCleanupTransformation())
                        .build()
                }
            coil3.compose.AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onError = { wikidataFailed = true },
                modifier = modifier.size(logoWidth, logoHeight),
            )
        }

        jellyfinLogo != null -> {
            AsyncImage(
                imageUrl = jellyfinLogo,
                contentDescription = name,
                targetWidth = logoWidth,
                targetHeight = logoHeight,
                contentScale = ContentScale.Fit,
                modifier = modifier.size(logoWidth, logoHeight),
            )
        }

        else -> {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.45f
private const val LOGO_WIDTH_FRACTION = 0.72f
private const val LOGO_HEIGHT_FRACTION = 0.46f
