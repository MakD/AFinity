package com.makd.afinity.ui.item.components.shared

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showPrimaryBlurHash
import com.makd.afinity.data.models.extensions.showPrimaryImageUrl
import com.makd.afinity.data.models.extensions.showThumbBlurHash
import com.makd.afinity.data.models.extensions.showThumbImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow

@Composable
fun rememberItemBackdropChain(item: AfinityItem): List<Pair<String, String?>> =
    remember(item.id) {
        when (item) {
            is AfinityShow ->
                listOfNotNull(
                    item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                    item.images.thumbImageUrl?.let { it to item.images.thumbBlurHash },
                    item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                )
            is AfinitySeason ->
                listOfNotNull(
                    item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                    item.images.showBackdropImageUrl?.let {
                        it to item.images.showBackdropBlurHash
                    },
                    item.images.thumbImageUrl?.let { it to item.images.thumbBlurHash },
                    item.images.showThumbImageUrl?.let { it to item.images.showThumbBlurHash },
                    item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                    item.images.showPrimaryImageUrl?.let { it to item.images.showPrimaryBlurHash },
                )
            else ->
                listOfNotNull(
                    item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                    item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                )
        }
    }

val LocalDetailPageColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

@Composable
fun LandscapeDetailTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDetailPageColorScheme provides MaterialTheme.colorScheme) {
        MaterialTheme(colorScheme = rememberLandscapeDetailColorScheme(), content = content)
    }
}

@Composable
private fun rememberLandscapeDetailColorScheme(): ColorScheme {
    val baseColorScheme = MaterialTheme.colorScheme
    return remember(baseColorScheme) {
        baseColorScheme.copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            outline = Color.White.copy(alpha = 0.5f),
        )
    }
}
