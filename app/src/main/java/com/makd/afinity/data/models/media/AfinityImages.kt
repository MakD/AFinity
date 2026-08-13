package com.makd.afinity.data.models.media

import android.net.Uri
import androidx.core.net.toUri

data class AfinityImages(
    val primary: Uri? = null,
    val backdrop: Uri? = null,
    val thumb: Uri? = null,
    val logo: Uri? = null,
    val showPrimary: Uri? = null,
    val showBackdrop: Uri? = null,
    val showThumb: Uri? = null,
    val showLogo: Uri? = null,
    val primaryImageBlurHash: String? = null,
    val backdropImageBlurHash: String? = null,
    val thumbImageBlurHash: String? = null,
    val logoImageBlurHash: String? = null,
    val showPrimaryImageBlurHash: String? = null,
    val showBackdropImageBlurHash: String? = null,
    val showThumbImageBlurHash: String? = null,
    val showLogoImageBlurHash: String? = null,
)

fun AfinityItem.withImages(newImages: AfinityImages): AfinityItem =
    when (this) {
        is AfinityMovie -> copy(images = newImages)
        is AfinityShow -> copy(images = newImages)
        is AfinityEpisode -> copy(images = newImages)
        is AfinitySeason -> copy(images = newImages)
        is AfinityBoxSet -> copy(images = newImages)
        is AfinityVideo -> copy(images = newImages)
        else -> this
    }

fun AfinityImages.withShowImagesFrom(showImages: AfinityImages): AfinityImages =
    copy(
        showPrimary = showImages.primary,
        showBackdrop = showImages.backdrop,
        showThumb = showImages.thumb,
        showLogo = showImages.logo,
        showPrimaryImageBlurHash = showImages.primaryImageBlurHash,
        showBackdropImageBlurHash = showImages.backdropImageBlurHash,
        showThumbImageBlurHash = showImages.thumbImageBlurHash,
        showLogoImageBlurHash = showImages.logoImageBlurHash,
    )

fun AfinityItem.patchedImagesFrom(source: AfinityItem): AfinityItem =
    when {
        id == source.id -> if (images == source.images) this else withImages(source.images)
        source is AfinityShow && this is AfinityEpisode && seriesId == source.id -> {
            val merged = images.withShowImagesFrom(source.images)
            if (merged == images) this else copy(images = merged)
        }
        else -> this
    }

fun <T : AfinityItem> List<T>.withPatchedImages(source: AfinityItem): List<T> {
    var changed = false
    val patched = map { item ->
        @Suppress("UNCHECKED_CAST") val next = item.patchedImagesFrom(source) as T
        if (next !== item) changed = true
        next
    }
    return if (changed) patched else this
}

fun AfinityImages.withBaseUrl(newBaseUrl: String): AfinityImages {
    val base = newBaseUrl.trimEnd('/').toUri()
    fun Uri?.patch(): Uri? =
        this?.buildUpon()?.scheme(base.scheme)?.encodedAuthority(base.encodedAuthority)?.build()
    return copy(
        primary = primary.patch(),
        backdrop = backdrop.patch(),
        thumb = thumb.patch(),
        logo = logo.patch(),
        showPrimary = showPrimary.patch(),
        showBackdrop = showBackdrop.patch(),
        showThumb = showThumb.patch(),
        showLogo = showLogo.patch(),
    )
}
