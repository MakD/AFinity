package com.makd.afinity.ui.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPhoto
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.di.DownloadClient
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.extensions.libraryApi
import timber.log.Timber

data class PhotoViewerUiState(
    val items: List<AfinityItem> = emptyList(),
    val startIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

sealed interface PhotoViewerEvent {
    data class Share(val uri: Uri, val mimeType: String) : PhotoViewerEvent

    data object SaveStarted : PhotoViewerEvent

    data object Saved : PhotoViewerEvent

    data object SaveFailed : PhotoViewerEvent

    data object ActionFailed : PhotoViewerEvent

    data class PlayVideo(val itemId: UUID, val mediaSourceId: String, val startPositionMs: Long) :
        PhotoViewerEvent
}

@HiltViewModel
class PhotoViewerViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val itemStore: ItemStore,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val sessionManager: SessionManager,
    downloadPermissions: DownloadPermissions,
    @param:DownloadClient private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val parentId: UUID? =
        savedStateHandle.get<String>("parentId")?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
    private val startId: UUID? =
        savedStateHandle.get<String>("startId")?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
    private val sortBy: SortBy =
        savedStateHandle.get<String>("sortBy")?.let { name ->
            SortBy.entries.firstOrNull { it.name == name }
        } ?: SortBy.NAME
    private val descending: Boolean = savedStateHandle.get<Boolean>("desc") ?: false

    val canDownload: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    private val _uiState = MutableStateFlow(PhotoViewerUiState())
    val uiState: StateFlow<PhotoViewerUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val items = live(state.items)
            if (items === state.items) state else state.copy(items = items)
        }

    private val _events = MutableSharedFlow<PhotoViewerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PhotoViewerEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = false) }
            val parent = parentId
            val result =
                if (parent == null) {
                    Result.success(listOfNotNull(startId?.let { mediaRepository.getItemById(it) }))
                } else {
                    mediaRepository
                        .getItemsResult(
                            parentId = parent,
                            sortBy = sortBy,
                            sortDescending = descending,
                            includeItemTypes = listOf("PHOTO", "VIDEO"),
                            recursive = false,
                            fields = FieldSets.PHOTO_DETAIL,
                        )
                        .map { response ->
                            val baseUrl = mediaRepository.getBaseUrl()
                            response.items.mapNotNull { it.toAfinityItem(baseUrl) }
                        }
                }
            result
                .onSuccess { loaded ->
                    val items = loaded.filter { it is AfinityPhoto || it is AfinityVideo }
                    val start = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
                    _uiState.update {
                        it.copy(items = items, startIndex = start, isLoading = false, error = false)
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to load photos for $parent")
                    _uiState.update { it.copy(isLoading = false, error = true) }
                }
        }
    }

    fun toggleFavorite(item: AfinityItem) {
        itemUserDataDelegate.toggleFavorite(
            scope = viewModelScope,
            item = item,
            updateOptimisticUI = {
                itemStore.applyPatch(item.id, UserDataPatch(favorite = !item.favorite))
            },
            revertUI = { itemStore.applyPatch(item.id, UserDataPatch(favorite = item.favorite)) },
        )
    }

    fun playVideo(video: AfinityItem) {
        viewModelScope.launch {
            val detailed =
                try {
                    mediaRepository.getItemById(video.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load video ${video.id}")
                    null
                } ?: video
            val mediaSourceId = detailed.sources.firstOrNull()?.id
            if (mediaSourceId == null) {
                _events.emit(PhotoViewerEvent.ActionFailed)
                return@launch
            }
            _events.emit(
                PhotoViewerEvent.PlayVideo(
                    itemId = video.id,
                    mediaSourceId = mediaSourceId,
                    startPositionMs = detailed.playbackPositionTicks / 10000,
                )
            )
        }
    }

    fun savePhoto(photo: AfinityPhoto) {
        _events.tryEmit(PhotoViewerEvent.SaveStarted)
        viewModelScope.launch {
            val saved =
                try {
                    withContext(Dispatchers.IO) { saveToGallery(photo) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save photo ${photo.id}")
                    false
                }
            _events.emit(if (saved) PhotoViewerEvent.Saved else PhotoViewerEvent.SaveFailed)
        }
    }

    private fun saveToGallery(photo: AfinityPhoto): Boolean {
        val apiClient = sessionManager.getCurrentApiClient() ?: return false
        val request =
            Request.Builder()
                .url(apiClient.libraryApi.getDownloadUrl(photo.id))
                .header("Authorization", "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"")
                .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Photo ${photo.id} download returned ${response.code}")
                return@use false
            }
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, photo.safeFileName())
                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        response.body.contentType()?.toString() ?: "image/jpeg",
                    )
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$SAVE_FOLDER",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val target =
                resolver.insert(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values,
                ) ?: return@use false
            var published = false
            try {
                val written =
                    resolver.openOutputStream(target)?.use { out ->
                        response.body.byteStream().copyTo(out)
                        true
                    } ?: false
                if (written) {
                    resolver.update(
                        target,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                    published = true
                }
                published
            } finally {
                if (!published) resolver.delete(target, null, null)
            }
        }
    }

    fun sharePhoto(photo: AfinityPhoto) {
        viewModelScope.launch {
            val shared =
                try {
                    withContext(Dispatchers.IO) { downloadForShare(photo) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to prepare photo ${photo.id} for sharing")
                    null
                }
            _events.emit(shared ?: PhotoViewerEvent.ActionFailed)
        }
    }

    private fun downloadForShare(photo: AfinityPhoto): PhotoViewerEvent.Share? {
        val apiClient = sessionManager.getCurrentApiClient() ?: return null
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, photo.safeFileName())
        val request =
            Request.Builder()
                .url(apiClient.libraryApi.getDownloadUrl(photo.id))
                .header("Authorization", "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"")
                .build()
        val mimeType =
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                target.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                response.body.contentType()?.toString() ?: "image/*"
            }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return PhotoViewerEvent.Share(uri, mimeType)
    }

    private fun AfinityPhoto.safeFileName(): String {
        val raw = fileName ?: "$name.jpg"
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private companion object {
        const val SAVE_FOLDER = "AFinity"
        const val SHARE_DIR = "shared_photos"
    }
}
