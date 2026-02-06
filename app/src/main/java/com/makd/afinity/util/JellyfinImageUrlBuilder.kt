package com.makd.afinity.util

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinImageUrlBuilder @Inject constructor() {

    fun buildOptimizedImageUrl(
        baseUrl: String,
        itemId: String,
        imageType: String = "Primary",
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int = 90,
        format: String = "webp",
        endpoint: String = "Items",
    ): String {
        return buildString {
            append(baseUrl.trimEnd('/'))
            append("/$endpoint/$itemId/Images/$imageType")

            val params = mutableListOf<String>()

            tag?.let { params.add("tag=$it") }
            maxWidth?.let { params.add("maxWidth=$it") }
            maxHeight?.let { params.add("maxHeight=$it") }
            params.add("quality=$quality")
            params.add("format=$format")

            if (params.isNotEmpty()) {
                append("?${params.joinToString("&")}")
            }
        }
    }

    fun buildPrimaryImageUrl(
        baseUrl: String,
        itemId: String,
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int = 90,
    ): String =
        buildOptimizedImageUrl(
            baseUrl = baseUrl,
            itemId = itemId,
            imageType = "Primary",
            tag = tag,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            quality = quality,
            endpoint = "Items",
        )

    fun buildUserPrimaryImageUrl(
        baseUrl: String,
        userId: String,
        tag: String? = null,
        maxWidth: Int? = null,
        quality: Int = 90,
    ): String =
        buildOptimizedImageUrl(
            baseUrl = baseUrl,
            itemId = userId,
            imageType = "Primary",
            tag = tag,
            maxWidth = maxWidth,
            quality = quality,
            endpoint = "Users",
        )

    fun buildBackdropUrl(
        baseUrl: String,
        itemId: String,
        tag: String? = null,
        maxWidth: Int = 1920,
        quality: Int = 85,
    ): String =
        buildOptimizedImageUrl(
            baseUrl = baseUrl,
            itemId = itemId,
            imageType = "Backdrop",
            tag = tag,
            maxWidth = maxWidth,
            quality = quality,
            endpoint = "Items",
        )

    fun dpToPx(dp: Dp, density: Density): Int {
        return with(density) { (dp.toPx() * 1.2f).toInt() }
    }
}
