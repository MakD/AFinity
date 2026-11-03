package com.makd.afinity.data.repository.admin

import com.makd.afinity.data.models.admin.CollectionChoice
import com.makd.afinity.data.models.admin.DeleteSummary
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.data.models.admin.ItemImage
import com.makd.afinity.data.models.admin.LyricsTarget
import com.makd.afinity.data.models.admin.RemoteLyrics

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
        albumArtist: String? = null,
    ): Result<List<IdentifyResult>>

    suspend fun applyIdentifyResult(
        itemId: String,
        result: IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit>

    suspend fun getLyricsTarget(trackId: String): Result<LyricsTarget>

    suspend fun searchRemoteLyrics(trackId: String): Result<List<RemoteLyrics>>

    suspend fun replaceWithRemoteLyrics(trackId: String, lyricId: String): Result<Unit>

    suspend fun replaceWithUploadedLyrics(
        trackId: String,
        fileName: String,
        text: String,
    ): Result<Unit>

    suspend fun deleteLyrics(trackId: String): Result<Unit>

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

    suspend fun getDeleteSummary(itemId: String): DeleteSummary?

    suspend fun deleteItem(
        itemId: String,
        tmdbId: Int? = null,
        isMovie: Boolean? = null,
    ): Result<Unit>

    suspend fun getCollections(): Result<List<CollectionChoice>>

    suspend fun createCollection(
        name: String,
        itemIds: List<String>,
        searchMetadata: Boolean,
    ): Result<String>

    suspend fun addToCollection(collectionId: String, itemIds: List<String>): Result<Unit>

    suspend fun removeFromCollection(collectionId: String, itemIds: List<String>): Result<Unit>
}
