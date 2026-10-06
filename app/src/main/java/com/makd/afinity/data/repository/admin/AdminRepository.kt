package com.makd.afinity.data.repository.admin

import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.data.models.admin.ItemImage

interface AdminRepository {

    suspend fun getEditableItem(itemId: String): EditableItem?

    suspend fun updateItemMetadata(itemId: String, item: EditableItem): Result<Unit>

    suspend fun getIdentifyTarget(itemId: String): IdentifyTarget?

    suspend fun getExternalIdProviders(itemId: String): List<ExternalIdProvider>

    suspend fun searchRemoteResult(
        itemId: String,
        itemType: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
    ): Result<List<IdentifyResult>>

    suspend fun applyIdentifyResult(
        itemId: String,
        result: IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit>

    suspend fun getItemImagesResult(itemId: String): Result<List<ItemImage>>

    suspend fun getRemoteImagesResult(
        itemId: String,
        includeAllLanguages: Boolean,
    ): Result<List<ItemImage>>

    suspend fun getSupportedImageTypes(itemId: String): List<String>

    suspend fun downloadRemoteImage(
        itemId: String,
        imageType: String,
        imageUrl: String,
    ): Result<Unit>

    suspend fun uploadImage(
        itemId: String,
        imageType: String,
        imageData: ByteArray,
        mimeType: String,
    ): Result<Unit>

    suspend fun deleteImage(
        itemId: String,
        imageType: String,
        imageIndex: Int?,
    ): Result<Unit>

    suspend fun moveImage(
        itemId: String,
        imageType: String,
        fromIndex: Int,
        toIndex: Int,
    ): Result<Unit>

    suspend fun refreshItem(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
        regenerateTrickplay: Boolean,
    ): Result<Unit>

    suspend fun canDeleteItem(itemId: String): Boolean

    suspend fun deleteItem(
        itemId: String,
        tmdbId: Int? = null,
        isMovie: Boolean? = null,
    ): Result<Unit>
}
