package com.makd.afinity.ui.preview

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.CardRowContainer
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.theme.LocalCardSize
import com.makd.afinity.ui.theme.ThemeMode
import kotlin.math.absoluteValue

class CardSizePreviewProvider : PreviewParameterProvider<CardSize> {
    override val values: Sequence<CardSize> =
        sequenceOf(CardSize.DEFAULT, CardSize.SMALL, CardSize.EXTRA_SMALL)

    override fun getDisplayName(index: Int): String =
        values.elementAt(index).name.lowercase().replace('_', ' ')
}

private fun previewImage(data: Any?): coil3.Image {
    val hue = (data.hashCode().absoluteValue % 360).toFloat()
    val top = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.8f))
    val bottom = android.graphics.Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.65f, 0.3f))
    return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        .asImage()
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun PreviewHost(
    modifier: Modifier = Modifier,
    cardSize: CardSize = CardSize.DEFAULT,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    playerOffset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val imageHandler = remember {
        AsyncImagePreviewHandler { request -> previewImage(request.data) }
    }
    AFinityTheme(themeMode = themeMode.name, dynamicColor = false) {
        CompositionLocalProvider(
            LocalCardSize provides cardSize,
            LocalPlayerOffset provides playerOffset,
            LocalAsyncImagePreviewHandler provides imageHandler,
        ) {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
                CardRowContainer(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
fun previewWidthSizeClass(): WindowWidthSizeClass {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return when {
        width < 600.dp -> WindowWidthSizeClass.Compact
        width < 840.dp -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
}

@Composable
fun PreviewCardRow(
    landscape: Boolean = false,
    content: @Composable RowScope.(cardWidth: Dp) -> Unit,
) {
    val sizeClass = previewWidthSizeClass()
    val cardWidth = if (landscape) sizeClass.landscapeWidth else sizeClass.portraitWidth
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier =
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = CardDimensions.RowGutter),
            horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        ) {
            content(cardWidth)
        }
    }
}
