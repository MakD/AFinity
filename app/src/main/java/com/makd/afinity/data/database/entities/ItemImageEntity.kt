package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.extensions.logoImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityImages
import java.util.UUID
import androidx.core.net.toUri

@Entity(tableName = "item_images")
data class ItemImageEntity(
    @PrimaryKey
    val itemId: UUID,
    val primaryImageUrl: String?,
    val backdropImageUrl: String?,
    val logoImageUrl: String?,
    val thumbImageUrl: String?,
    val primaryBlurHash: String?,
    val backdropBlurHash: String?,
    val logoBlurHash: String?,
    val thumbBlurHash: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

fun AfinityImages.toItemImageEntity(itemId: UUID): ItemImageEntity {
    return ItemImageEntity(
        itemId = itemId,
        primaryImageUrl = this.primaryImageUrl,
        backdropImageUrl = this.backdropImageUrl,
        logoImageUrl = this.logoImageUrl,
        thumbImageUrl = this.thumbImageUrl,
        primaryBlurHash = this.primaryBlurHash,
        backdropBlurHash = this.backdropBlurHash,
        logoBlurHash = this.logoBlurHash,
        thumbBlurHash = this.thumbBlurHash
    )
}

fun ItemImageEntity.toAfinityImages(): AfinityImages {
    return AfinityImages(
        primary = this.primaryImageUrl?.toUri(),
        backdrop = this.backdropImageUrl?.toUri(),
        logo = this.logoImageUrl?.toUri(),
        thumb = this.thumbImageUrl?.toUri(),
        primaryImageBlurHash = this.primaryBlurHash,
        backdropImageBlurHash = this.backdropBlurHash,
        logoImageBlurHash = this.logoBlurHash,
        thumbImageBlurHash = this.thumbBlurHash,
        showPrimary = null,
        showBackdrop = null,
        showLogo = null,
        showPrimaryImageBlurHash = null,
        showBackdropImageBlurHash = null,
        showLogoImageBlurHash = null
    )
}