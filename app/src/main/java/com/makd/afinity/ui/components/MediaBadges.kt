package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

private const val BADGE_WIDTH_FRACTION = 0.22f
private const val BADGE_INSET_FRACTION = 0.07f
private const val BADGE_ICON_FRACTION = 2f / 3f
private const val COUNT_TEXT_FRACTION = 11f / 24f
private const val COUNT_LINE_HEIGHT_RATIO = 16f / 11f
private const val COUNT_HORIZONTAL_PADDING_FRACTION = 6f / 24f
private const val COUNT_VERTICAL_PADDING_FRACTION = 2f / 24f
private const val COUNT_CORNER_FRACTION = 4f / 24f
private const val COUNT_DOT_FRACTION = 2f / 3f
private const val COUNT_CONTAINER_ALPHA = 0.9f
private const val STATUS_LETTER_SPACING = 0.5f

private val BadgeMinSize = 12.dp
private val BadgeMaxSize = 24.dp
private val BadgeMinInset = 3.dp
private val BadgeMaxInset = 8.dp
private val CountMinCardWidth = 66.dp
private val CountMinTextSize = 8.dp

@Immutable
private class CardBadgeMetrics(cardWidth: Dp) {
    val size: Dp = (cardWidth * BADGE_WIDTH_FRACTION).coerceIn(BadgeMinSize, BadgeMaxSize)
    val inset: Dp = (cardWidth * BADGE_INSET_FRACTION).coerceIn(BadgeMinInset, BadgeMaxInset)
    val showCount: Boolean = cardWidth >= CountMinCardWidth
}

@Composable
private fun CardBadgeSlot(modifier: Modifier, content: @Composable (CardBadgeMetrics) -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val metrics = CardBadgeMetrics(maxWidth)
        Box(modifier = Modifier.padding(metrics.inset)) { content(metrics) }
    }
}

@Composable
private fun CardIconBadge(
    painter: Painter,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    metrics: CardBadgeMetrics,
) {
    Box(
        modifier = Modifier.size(metrics.size).background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(metrics.size * BADGE_ICON_FRACTION),
        )
    }
}

@Composable
fun PlayedBadge(
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.cd_watched),
) {
    CardBadgeSlot(modifier = modifier) { metrics ->
        CardIconBadge(
            painter = painterResource(id = R.drawable.ic_check),
            contentDescription = contentDescription,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            metrics = metrics,
        )
    }
}

@Composable
fun UnavailableBadge(modifier: Modifier = Modifier) {
    CardBadgeSlot(modifier = modifier) { metrics ->
        CardIconBadge(
            painter = painterResource(id = R.drawable.ic_cloud_off),
            contentDescription = stringResource(R.string.download_unavailable_indicator),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            metrics = metrics,
        )
    }
}

@Composable
fun MediaCountBadge(text: String, modifier: Modifier = Modifier) {
    CardTextBadge(
        text = text,
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = COUNT_CONTAINER_ALPHA),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

@Composable
fun MediaStatusBadge(text: String, containerColor: Color, modifier: Modifier = Modifier) {
    CardTextBadge(
        text = text,
        containerColor = containerColor,
        contentColor = Color.White,
        modifier = modifier,
        letterSpacing = STATUS_LETTER_SPACING.sp,
    )
}

@Composable
private fun CardTextBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    CardBadgeSlot(modifier = modifier) { metrics ->
        if (metrics.showCount) {
            val textSize = max(metrics.size * COUNT_TEXT_FRACTION, CountMinTextSize)
            val density = LocalDensity.current
            Surface(
                shape = RoundedCornerShape(metrics.size * COUNT_CORNER_FRACTION),
                color = containerColor,
            ) {
                Text(
                    text = text,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = with(density) { textSize.toSp() },
                            lineHeight =
                                with(density) { (textSize * COUNT_LINE_HEIGHT_RATIO).toSp() },
                            letterSpacing = letterSpacing,
                        ),
                    color = contentColor,
                    modifier =
                        Modifier.padding(
                            horizontal = metrics.size * COUNT_HORIZONTAL_PADDING_FRACTION,
                            vertical = metrics.size * COUNT_VERTICAL_PADDING_FRACTION,
                        ),
                )
            }
        } else {
            Box(
                modifier =
                    Modifier.size(metrics.size * COUNT_DOT_FRACTION)
                        .background(containerColor, CircleShape)
                        .semantics { contentDescription = text }
            )
        }
    }
}
