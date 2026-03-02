package com.makd.afinity.data.models.media

import android.net.Uri
import androidx.core.net.toUri
import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

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

fun BaseItemDto.toAfinityImages(jellyfinRepository: JellyfinRepository): AfinityImages {
    val baseUrl = jellyfinRepository.getBaseUrl().toUri()
    return AfinityImages(
        primary =
            imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$id/Images/Primary")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        backdrop =
            backdropImageTags?.firstOrNull()?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$id/Images/Backdrop/0")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        thumb =
            imageTags?.get(ImageType.THUMB)?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$id/Images/Thumb")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        logo =
            imageTags?.get(ImageType.LOGO)?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$id/Images/Logo")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        showPrimary =
            seriesPrimaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$seriesId/Images/Primary")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        showBackdrop =
            seriesPrimaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$seriesId/Images/Backdrop/0")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        showThumb =
            (seriesThumbImageTag ?: seriesPrimaryImageTag)?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$seriesId/Images/Thumb")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        showLogo =
            seriesPrimaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("Items/$seriesId/Images/Logo")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        primaryImageBlurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.values?.firstOrNull(),
        backdropImageBlurHash = imageBlurHashes?.get(ImageType.BACKDROP)?.values?.firstOrNull(),
        thumbImageBlurHash = imageBlurHashes?.get(ImageType.THUMB)?.values?.firstOrNull(),
        logoImageBlurHash = imageBlurHashes?.get(ImageType.LOGO)?.values?.firstOrNull(),
        showPrimaryImageBlurHash =
            imageBlurHashes?.get(ImageType.PRIMARY)?.get(seriesPrimaryImageTag),
        showBackdropImageBlurHash =
            imageBlurHashes?.get(ImageType.BACKDROP)?.get(parentBackdropImageTags?.firstOrNull()),
        showThumbImageBlurHash = imageBlurHashes?.get(ImageType.THUMB)?.get(seriesThumbImageTag),
        showLogoImageBlurHash = imageBlurHashes?.get(ImageType.LOGO)?.get(seriesPrimaryImageTag),
    )
}
