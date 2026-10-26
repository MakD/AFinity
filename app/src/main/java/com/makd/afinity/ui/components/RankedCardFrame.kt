package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.makd.afinity.ui.theme.CardDimensions

private const val DIGIT_CAP_HEIGHT_RATIO = 0.72f
private const val DIGIT_STROKE_FRACTION = 0.03f
private const val DIGIT_ALPHA = 0.7f

@Composable
fun RankedCardFrame(
    ranking: Int,
    cardWidth: Dp,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    details: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    val numberZone = CardDimensions.rankedNumberZone(cardWidth)
    val cardHeight = CardDimensions.calculateHeight(cardWidth, aspectRatio)

    Column(modifier = modifier.width(numberZone + cardWidth)) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight)) {
            RankDigits(
                ranking = ranking,
                digitHeight = CardDimensions.rankedDigitHeight(cardHeight),
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Box(modifier = Modifier.align(Alignment.TopEnd).size(cardWidth, cardHeight)) { card() }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Box(modifier = Modifier.padding(start = numberZone)) { details() }
    }
}

@Composable
private fun RankDigits(ranking: Int, digitHeight: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val fontSize = with(density) { (digitHeight / DIGIT_CAP_HEIGHT_RATIO).toSp() }
    val strokeWidth = with(density) { (digitHeight * DIGIT_STROKE_FRACTION).toPx() }

    Text(
        text = ranking.toString(),
        style =
            MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                lineHeight = TextUnit.Unspecified,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = strokeWidth),
            ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = DIGIT_ALPHA),
        overflow = TextOverflow.Visible,
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier.layout { measurable, constraints ->
                val placeable =
                    measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                    )
                val baseline = placeable[FirstBaseline]
                layout(placeable.width, baseline) { placeable.place(0, 0) }
            },
    )
}
