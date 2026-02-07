package com.makd.afinity.data.models.media

import android.net.Uri
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
    val showLogo: Uri? = null,
    val primaryImageBlurHash: String? = null,
    val backdropImageBlurHash: String? = null,
    val thumbImageBlurHash: String? = null,
    val logoImageBlurHash: String? = null,
    val showPrimaryImageBlurHash: String? = null,
    val showBackdropImageBlurHash: String? = null,
    val showLogoImageBlurHash: String? = null,
)

fun BaseItemDto.toAfinityImages(jellyfinRepository: JellyfinRepository): AfinityImages {
    val baseUrl = Uri.parse(jellyfinRepository.getBaseUrl())
    val primary =
        imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.PRIMARY}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val thumb =
        imageTags?.get(ImageType.THUMB)?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.THUMB}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val backdrop =
        backdropImageTags?.firstOrNull()?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.BACKDROP}/0")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val logo =
        imageTags?.get(ImageType.LOGO)?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.LOGO}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val showPrimary =
        seriesPrimaryImageTag?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$seriesId/Images/${ImageType.PRIMARY}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val showBackdrop =
        seriesPrimaryImageTag?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$seriesId/Images/${ImageType.BACKDROP}/0")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val showLogo =
        seriesPrimaryImageTag?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$seriesId/Images/${ImageType.LOGO}")
                .appendQueryParameter("tag", tag)
                .build()
        }

    return AfinityImages(
        primary = primary,
        thumb = thumb,
        backdrop = backdrop,
        logo = logo,
        showPrimary = showPrimary,
        showBackdrop = showBackdrop,
        showLogo = showLogo,
        primaryImageBlurHash =
            imageBlurHashes?.get(ImageType.PRIMARY)?.get(imageTags?.get(ImageType.PRIMARY)),
        backdropImageBlurHash =
            imageBlurHashes?.get(ImageType.BACKDROP)?.get(backdropImageTags?.firstOrNull()),
        thumbImageBlurHash =
            imageBlurHashes?.get(ImageType.THUMB)?.get(imageTags?.get(ImageType.THUMB)),
        logoImageBlurHash =
            imageBlurHashes?.get(ImageType.LOGO)?.get(imageTags?.get(ImageType.LOGO)),
        showPrimaryImageBlurHash =
            imageBlurHashes?.get(ImageType.PRIMARY)?.get(seriesPrimaryImageTag),
        showBackdropImageBlurHash =
            imageBlurHashes?.get(ImageType.BACKDROP)?.get(seriesPrimaryImageTag),
        showLogoImageBlurHash = imageBlurHashes?.get(ImageType.LOGO)?.get(seriesPrimaryImageTag),
    )
}
