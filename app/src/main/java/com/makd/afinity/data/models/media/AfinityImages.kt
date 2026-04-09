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
