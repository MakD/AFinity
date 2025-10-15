package com.makd.afinity.data.models.extensions

import com.makd.afinity.data.models.media.AfinityImages

val AfinityImages.primaryImageUrl: String? get() = primary?.toString()
val AfinityImages.backdropImageUrl: String? get() = backdrop?.toString()
val AfinityImages.thumbImageUrl: String? get() = thumb?.toString()
val AfinityImages.logoImageUrl: String? get() = logo?.toString()
val AfinityImages.showPrimaryImageUrl: String? get() = showPrimary?.toString()
val AfinityImages.showBackdropImageUrl: String? get() = showBackdrop?.toString()
val AfinityImages.showLogoImageUrl: String? get() = showLogo?.toString()

val AfinityImages.primaryBlurHash: String? get() = primaryImageBlurHash
val AfinityImages.backdropBlurHash: String? get() = backdropImageBlurHash
val AfinityImages.thumbBlurHash: String? get() = thumbImageBlurHash
val AfinityImages.logoBlurHash: String? get() = logoImageBlurHash
val AfinityImages.showBackdropBlurHash: String? get() = showBackdropImageBlurHash
val AfinityImages.showLogoBlurHash: String? get() = showLogoImageBlurHash
val AfinityImages.logoImageUrlWithTransparency: String?
    get() = logoImageUrl?.let { url ->
        if (url.contains("?")) "$url&format=png" else "$url?format=png"
    }