package com.makd.afinity.ui.audiobookshelf.player.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColor(url: String?, defaultColor: Color): Color {
    var dominantColor by remember { mutableStateOf(defaultColor) }
    val context = LocalPlatformContext.current

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()

            val result = loader.execute(request)

            if (result is SuccessResult) {
                val image = result.image
                val drawable = image.asDrawable(context.resources)
                val bitmap = drawable.toBitmap()

                val palette = Palette.from(bitmap).generate()
                val swatch =
                    palette.vibrantSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.dominantSwatch

                swatch?.let { dominantColor = Color(it.rgb) }
            }
        }
    }
    return dominantColor
}
