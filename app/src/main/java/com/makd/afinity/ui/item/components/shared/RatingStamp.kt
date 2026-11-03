package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.toUpperCase

@Immutable
data class RatingStampColors(
    val fill: Color,
    val band: Color,
    val ink: Color,
    val text: Color,
    val tileLabel: Color,
)

val CertifiedFreshStamp =
    RatingStampColors(
        fill = Color(0xFFFA320A),
        band = Color(0xFFA61F05),
        ink = Color.White,
        text = Color.White,
        tileLabel = Color(0xFFE7D7D3),
    )

val VerifiedHotStamp =
    RatingStampColors(
        fill = Color(0xFF4CBFB4),
        band = Color(0xFF1F6F68),
        ink = Color(0xFF0F2E2B),
        text = Color.White,
        tileLabel = Color(0xFFD3E6E3),
    )

@Composable
fun RatingStamp(text: String, colors: RatingStampColors, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val label = text.toUpperCase(LocaleList.current)
    Spacer(
        modifier =
            modifier.drawWithCache {
                val unit = size.minDimension / 64f
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseStyle =
                    TextStyle(
                        color = colors.text,
                        fontSize = (10.6f * unit).toSp(),
                        fontWeight = FontWeight.Black,
                        letterSpacing = (0.2f * unit).toSp(),
                    )
                val maxTextWidth = 54f * unit
                val measured =
                    textMeasurer.measure(label, baseStyle, maxLines = 1, softWrap = false)
                val layout =
                    if (measured.size.width <= maxTextWidth) measured
                    else
                        textMeasurer.measure(
                            label,
                            baseStyle.copy(
                                fontSize = baseStyle.fontSize * (maxTextWidth / measured.size.width)
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                onDrawBehind {
                    drawCircle(colors.fill, radius = 31f * unit, center = center)
                    drawCircle(
                        colors.ink,
                        radius = 27.5f * unit,
                        center = center,
                        style = Stroke(width = 1.8f * unit),
                    )
                    drawCircle(
                        colors.ink,
                        radius = 24.5f * unit,
                        center = center,
                        style = Stroke(width = 0.9f * unit),
                    )
                    drawRect(
                        colors.band,
                        topLeft = Offset(center.x - 31f * unit, center.y - 8f * unit),
                        size = Size(62f * unit, 16f * unit),
                    )
                    drawCircle(
                        colors.ink,
                        radius = 2.4f * unit,
                        center = Offset(center.x, center.y - 18f * unit),
                    )
                    drawCircle(
                        colors.ink,
                        radius = 2.4f * unit,
                        center = Offset(center.x, center.y + 18f * unit),
                    )
                    drawText(
                        layout,
                        topLeft =
                            Offset(
                                center.x - layout.size.width / 2f,
                                center.y - layout.size.height / 2f,
                            ),
                    )
                }
            }
    )
}
