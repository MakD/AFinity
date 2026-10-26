package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.utils.bottomOverlap

private val ImageHeroMaxBackdropHeight = 560.dp
private const val ImageHeroBackdropFraction = 0.5f
private const val ImageHeroPhotoFraction = 0.6f
private val ImageHeroOverlap = 110.dp
private val CoverHeroTopOffset = 60.dp

enum class ImageHeroKind {
    Backdrop,
    Photo,
}

sealed interface PortraitHero {
    data class Image(
        val kind: ImageHeroKind,
        val backdrop: List<Pair<String, String?>>,
        val contentDescription: String? = null,
    ) : PortraitHero

    data class Cover(val backdrop: List<Pair<String, String?>>) : PortraitHero
}

@Composable
fun PortraitDetailLayout(
    hero: PortraitHero,
    listState: LazyListState,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    listVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    items: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = detailListBottomPadding()),
        verticalArrangement = listVerticalArrangement,
    ) {
        when (hero) {
            is PortraitHero.Image -> {
                item(key = "hero") { ImageHero(hero) }
                item(key = "header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DetailGutter),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = header,
                    )
                }
            }
            is PortraitHero.Cover -> item(key = "header") { CoverHero(hero, header) }
        }
        items()
    }
}

@Composable
private fun ImageHero(hero: PortraitHero.Image) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val heroHeight =
        when (hero.kind) {
            ImageHeroKind.Backdrop ->
                (screenHeight * ImageHeroBackdropFraction).coerceAtMost(ImageHeroMaxBackdropHeight)
            ImageHeroKind.Photo -> screenHeight * ImageHeroPhotoFraction
        }

    var urlIndex by remember(hero.backdrop) { mutableIntStateOf(0) }
    val current = hero.backdrop.getOrNull(urlIndex)

    Box(modifier = Modifier.bottomOverlap(ImageHeroOverlap).fillMaxWidth().height(heroHeight)) {
        AsyncImage(
            imageUrl = current?.first,
            contentDescription = hero.contentDescription,
            blurHash = current?.second,
            targetWidth = screenWidth,
            targetHeight = heroHeight,
            useLowResPlaceholder = true,
            onError = {
                if (urlIndex < hero.backdrop.lastIndex) {
                    urlIndex++
                }
            },
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val fadeStart =
                            (size.height - HeroFadeHeight.toPx()).coerceAtLeast(size.height * 0.35f)
                        val gradient =
                            Brush.verticalGradient(
                                colors = HeroFadeStops,
                                startY = fadeStart,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    },
            contentScale = ContentScale.Crop,
            alignment =
                when (hero.kind) {
                    ImageHeroKind.Backdrop -> Alignment.TopCenter
                    ImageHeroKind.Photo -> Alignment.Center
                },
        )
    }
}

@Composable
fun DetailCoverImage(imageUrl: String?, contentDescription: String?, blurHash: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    if (imageUrl != null) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            blurHash = blurHash,
            targetWidth = DetailCoverSize,
            targetHeight = DetailCoverSize,
            modifier = Modifier.size(DetailCoverSize).shadow(12.dp, shape).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Card(
            modifier = Modifier.size(DetailCoverSize),
            shape = shape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
        ) {}
    }
}

@Composable
fun DetailCoverTitle(title: String, subtitle: String? = null) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    if (subtitle != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CoverHero(hero: PortraitHero.Cover, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.matchParentSize()) {
            DetailBackdrop(BackdropStyle.BlurredCover, hero.backdrop)
        }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = DetailGutter)
                    .statusBarsPadding()
                    .padding(top = CoverHeroTopOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
