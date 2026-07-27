package com.makd.afinity.data.repository.admin

import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.ItemImage

interface AdminRepository {

    suspend fun getEditableItem(itemId: String): EditableItem?

    suspend fun updateItemMetadata(itemId: String, item: EditableItem): Result<Unit>

    suspend fun getExternalIdProviders(itemId: String): List<ExternalIdProvider>

    suspend fun searchMovie(
        itemId: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
    ): List<IdentifyResult>

    suspend fun searchSeries(
        itemId: String,
        name: String,
        year: Int?,
        providerIds: Map<String, String>,
    ): List<IdentifyResult>

    suspend fun applyIdentifyResult(
        itemId: String,
        result: IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit>

    suspend fun getItemImages(itemId: String): List<ItemImage>

    suspend fun getRemoteImages(
        itemId: String,
        imageType: String,
        includeAllLanguages: Boolean,
    ): List<ItemImage>

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

    suspend fun refreshItem(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
    ): Result<Unit>

    suspend fun deleteItem(itemId: String): Result<Unit>
}

