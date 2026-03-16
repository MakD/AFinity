package com.makd.afinity.data.models.media

import android.net.Uri

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

