package com.makd.afinity.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.makd.afinity.util.LogoCleanup

class LogoCleanupTransformation : Transformation() {

    override val cacheKey: String = "logo_cleanup_v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        val source =
            if (input.config == Bitmap.Config.ARGB_8888) input
            else input.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        if (!LogoCleanup.process(pixels, width, height)) return input
        return createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
