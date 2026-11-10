package com.makd.afinity.ui.components

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

internal enum class HeroTextTone {
    LIGHT,
    DARK,
}

internal class HeroBackdropSample(val width: Int, val height: Int, val pixels: IntArray)

internal data class HeroTextRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val heroWidth: Float,
    val heroHeight: Float,
)

internal class HeroToneReading(
    val brightLuminance: Float,
    val darkLuminance: Float,
    val lightTextContrast: Float,
    val darkTextContrast: Float,
    val tone: HeroTextTone,
)

private const val HeroToneBrightPercentile = 0.85f
private const val HeroToneDarkPercentile = 0.15f
private const val HeroToneDarkTextMargin = 1.25f

private fun srgbToLinear(channel: Float): Float =
    if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun relativeLuminance(red: Float, green: Float, blue: Float): Float =
    0.2126f * srgbToLinear(red) + 0.7152f * srgbToLinear(green) + 0.0722f * srgbToLinear(blue)

private fun argbLuminance(argb: Int): Float =
    relativeLuminance(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
    )

private fun fadeAlphaAt(y: Float, fadeStart: Float, height: Float, stops: FloatArray): Float {
    if (y <= fadeStart || stops.size < 2 || height <= fadeStart) return 1f
    val last = stops.size - 1
    val position = ((y - fadeStart) / (height - fadeStart)).coerceIn(0f, 1f) * last
    val index = position.toInt().coerceAtMost(last - 1)
    return stops[index] + (stops[index + 1] - stops[index]) * (position - index)
}

internal fun measureHeroTextTone(
    sample: HeroBackdropSample,
    region: HeroTextRegion,
    fadeStart: Float,
    fadeStops: FloatArray,
    dimAlpha: Float,
    pageArgb: Int,
    darkTextArgb: Int,
): HeroToneReading? {
    if (sample.width <= 0 || sample.height <= 0) return null
    if (region.heroWidth <= 0f || region.heroHeight <= 0f) return null
    if (region.right - region.left < 1f || region.bottom - region.top < 1f) return null

    val scale = max(region.heroWidth / sample.width, region.heroHeight / sample.height)
    val offsetX = (region.heroWidth - sample.width * scale) / 2f
    val offsetY = (region.heroHeight - sample.height * scale) / 2f

    val firstColumn = floor((region.left - offsetX) / scale).toInt().coerceIn(0, sample.width - 1)
    val lastColumn =
        (ceil((region.right - offsetX) / scale).toInt() - 1).coerceIn(firstColumn, sample.width - 1)
    val firstRow = floor((region.top - offsetY) / scale).toInt().coerceIn(0, sample.height - 1)
    val lastRow =
        (ceil((region.bottom - offsetY) / scale).toInt() - 1).coerceIn(firstRow, sample.height - 1)

    val pageRed = ((pageArgb shr 16) and 0xFF) / 255f
    val pageGreen = ((pageArgb shr 8) and 0xFF) / 255f
    val pageBlue = (pageArgb and 0xFF) / 255f

    val luminances = FloatArray((lastColumn - firstColumn + 1) * (lastRow - firstRow + 1))
    var count = 0
    for (row in firstRow..lastRow) {
        val y = (offsetY + (row + 0.5f) * scale).coerceIn(region.top, region.bottom)
        val imageAlpha = fadeAlphaAt(y, fadeStart, region.heroHeight, fadeStops)
        val imageWeight = imageAlpha * (1f - dimAlpha)
        val pageWeight = 1f - imageAlpha
        for (column in firstColumn..lastColumn) {
            val pixel = sample.pixels[row * sample.width + column]
            val red = ((pixel shr 16) and 0xFF) / 255f * imageWeight + pageRed * pageWeight
            val green = ((pixel shr 8) and 0xFF) / 255f * imageWeight + pageGreen * pageWeight
            val blue = (pixel and 0xFF) / 255f * imageWeight + pageBlue * pageWeight
            luminances[count++] = relativeLuminance(red, green, blue)
        }
    }
    if (count == 0) return null

    luminances.sort()
    val bright = luminances[((count - 1) * HeroToneBrightPercentile).toInt()]
    val dark = luminances[((count - 1) * HeroToneDarkPercentile).toInt()]
    val lightTextContrast = 1.05f / (bright + 0.05f)
    val darkTextContrast = (dark + 0.05f) / (argbLuminance(darkTextArgb) + 0.05f)
    val tone =
        if (darkTextContrast > lightTextContrast * HeroToneDarkTextMargin) HeroTextTone.DARK
        else HeroTextTone.LIGHT
    return HeroToneReading(bright, dark, lightTextContrast, darkTextContrast, tone)
}
