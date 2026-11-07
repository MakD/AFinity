package com.makd.afinity.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object LogoCleanup {

    private const val ALPHA_MASK = -0x1000000
    private const val WHITE_RGB = 0x00FFFFFF
    private const val OPAQUE_ALPHA = 128
    private const val MIN_TRANSPARENT_SHARE = 0.05
    private const val BORDER_UNIFORM_SHARE = 0.9
    private const val BACKGROUND_DISTANCE = 40
    private const val FRINGE_DISTANCE = 120
    private const val MAX_REMOVED_SHARE = 0.97
    private const val KNOCKOUT_HOLE_SHARE = 0.01
    private const val DARK_LUMINANCE = 90.0
    private const val DARK_SATURATION = 0.3
    private const val DARK_INK_SHARE = 0.9
    private const val SAMPLE_TARGET = 20_000

    fun process(argb: IntArray, width: Int, height: Int): Boolean {
        if (width < 2 || height < 2 || argb.size != width * height) return false
        var changed = false
        if (transparentShare(argb) < MIN_TRANSPARENT_SHARE) {
            changed = removeBackground(argb, width, height)
        }
        if (isDarkInk(argb)) {
            for (i in argb.indices) argb[i] = (argb[i] and ALPHA_MASK) or WHITE_RGB
            changed = true
        }
        return changed
    }

    private fun transparentShare(argb: IntArray): Double {
        val step = max(1, argb.size / SAMPLE_TARGET)
        var sampled = 0
        var transparent = 0
        for (i in argb.indices step step) {
            sampled++
            if (alpha(argb[i]) < OPAQUE_ALPHA) transparent++
        }
        return transparent.toDouble() / sampled
    }

    private fun removeBackground(argb: IntArray, width: Int, height: Int): Boolean {
        val edge = IntArray(2 * width + 2 * height)
        var n = 0
        fun sample(pixel: Int) {
            if (alpha(pixel) >= OPAQUE_ALPHA) edge[n++] = pixel
        }
        for (x in 0 until width) {
            sample(argb[x])
            sample(argb[(height - 1) * width + x])
        }
        for (y in 0 until height) {
            sample(argb[y * width])
            sample(argb[y * width + width - 1])
        }
        if (n < edge.size / 2) return false
        val border = edge.copyOf(n)
        val background = medianColor(border)
        val uniform = border.count { distance(it, background) <= BACKGROUND_DISTANCE }
        if (uniform < border.size * BORDER_UNIFORM_SHARE) return false

        val removedMask = BooleanArray(argb.size)
        var removed = 0
        var holes = 0
        for (i in argb.indices) {
            val hole = alpha(argb[i]) < OPAQUE_ALPHA
            if (hole) holes++
            if (hole || distance(argb[i], background) <= BACKGROUND_DISTANCE) {
                removedMask[i] = true
                removed++
            }
        }
        if (removed == 0) return false
        if (removed > argb.size * MAX_REMOVED_SHARE) {
            if (holes < argb.size * KNOCKOUT_HOLE_SHARE) return false
            val ink = background and WHITE_RGB
            for (i in argb.indices) argb[i] = ((255 - alpha(argb[i])) shl 24) or ink
            return true
        }

        val out = argb.copyOf()
        for (i in argb.indices) {
            if (removedMask[i]) {
                out[i] = argb[i] and WHITE_RGB
                continue
            }
            val pixel = argb[i]
            val d = distance(pixel, background)
            if (d >= FRINGE_DISTANCE || !touchesRemoved(removedMask, i, width, height)) continue
            val keep = (d - BACKGROUND_DISTANCE).toFloat() / (FRINGE_DISTANCE - BACKGROUND_DISTANCE)
            val r = unblend(red(pixel), red(background), keep)
            val g = unblend(green(pixel), green(background), keep)
            val b = unblend(blue(pixel), blue(background), keep)
            out[i] = ((alpha(pixel) * keep).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.copyInto(argb)
        return true
    }

    private fun touchesRemoved(mask: BooleanArray, index: Int, width: Int, height: Int): Boolean {
        val x = index % width
        val y = index / width
        return (x > 0 && mask[index - 1]) ||
            (x < width - 1 && mask[index + 1]) ||
            (y > 0 && mask[index - width]) ||
            (y < height - 1 && mask[index + width])
    }

    private fun unblend(channel: Int, background: Int, keep: Float): Int =
        ((channel - background * (1f - keep)) / keep).roundToInt().coerceIn(0, 255)

    private fun isDarkInk(argb: IntArray): Boolean {
        val step = max(1, argb.size / SAMPLE_TARGET)
        var sampled = 0
        var opaque = 0
        var dark = 0
        for (i in argb.indices step step) {
            sampled++
            val pixel = argb[i]
            if (alpha(pixel) < OPAQUE_ALPHA) continue
            opaque++
            val r = red(pixel)
            val g = green(pixel)
            val b = blue(pixel)
            val high = max(r, max(g, b))
            val low = min(r, min(g, b))
            val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val saturation = if (high == 0) 0.0 else (high - low).toDouble() / high
            if (luminance < DARK_LUMINANCE && saturation < DARK_SATURATION) dark++
        }
        if (opaque == 0) return false
        val transparent = 1.0 - opaque.toDouble() / sampled
        return transparent >= MIN_TRANSPARENT_SHARE && dark >= opaque * DARK_INK_SHARE
    }

    private fun medianColor(colors: IntArray): Int {
        fun median(channel: (Int) -> Int): Int {
            val values = IntArray(colors.size) { channel(colors[it]) }
            values.sort()
            return values[values.size / 2]
        }
        return (0xFF shl 24) or (median(::red) shl 16) or (median(::green) shl 8) or median(::blue)
    }

    private fun distance(a: Int, b: Int): Int =
        max(abs(red(a) - red(b)), max(abs(green(a) - green(b)), abs(blue(a) - blue(b))))

    private fun alpha(pixel: Int): Int = pixel ushr 24

    private fun red(pixel: Int): Int = (pixel shr 16) and 0xFF

    private fun green(pixel: Int): Int = (pixel shr 8) and 0xFF

    private fun blue(pixel: Int): Int = pixel and 0xFF
}
