package com.makd.afinity.ui.admin.images

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ItemImage
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val PRIMARY = "Primary"
private val IMAGE_TYPE_ORDER = listOf(PRIMARY, "Backdrop", "Logo", "Thumb", "Banner", "Art", "Disc")
private val MULTI_IMAGE_TYPES = setOf("Backdrop", "Screenshot")

enum class ImageSort {
    RECOMMENDED,
    LARGEST,
}

enum class EditImagesMessage(@param:StringRes val textRes: Int, val isSuccess: Boolean) {
    IMAGE_UPDATED(R.string.admin_image_updated, true),
    IMAGE_REMOVED(R.string.admin_image_removed, true),
    ACTION_FAILED(R.string.admin_image_action_failed, false),
    FILE_UNREADABLE(R.string.admin_image_file_unreadable, false),
}

data class EditImagesUiState(
    val types: List<String> = listOf(PRIMARY),
    val selectedType: String = PRIMARY,
    val allowsMultiple: Boolean = false,
    val canReorder: Boolean = false,
    val typeCounts: Map<String, Int> = emptyMap(),
    val currentImages: List<ItemImage> = emptyList(),
    val serverLoading: Boolean = true,
    val serverFailed: Boolean = false,
    val candidates: List<ItemImage> = emptyList(),
    val providers: List<String> = emptyList(),
    val remoteLoading: Boolean = true,
    val remoteFailed: Boolean = false,
    val includeAllLanguages: Boolean = false,
    val provider: String? = null,
    val sort: ImageSort = ImageSort.RECOMMENDED,
    val busy: Boolean = false,
    val message: EditImagesMessage? = null,
)

@HiltViewModel
class EditImagesViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EditImagesUiState())
    val uiState: StateFlow<EditImagesUiState> = _uiState.asStateFlow()

    private var serverImages: List<ItemImage> = emptyList()
    private var remoteImages: List<ItemImage> = emptyList()
    private var supportedTypes: List<String> = emptyList()
    private var serverJob: Job? = null
    private var remoteJob: Job? = null

    init {
        loadSupportedTypes()
        loadServerImages()
        loadRemoteImages()
    }

    private fun loadSupportedTypes() {
        viewModelScope.launch {
            supportedTypes = adminRepository.getSupportedImageTypes(itemId)
            updateState { it }
        }
    }

    fun loadServerImages() {
        serverJob?.cancel()
        serverJob = viewModelScope.launch {
            updateState { it.copy(serverLoading = serverImages.isEmpty(), serverFailed = false) }
            adminRepository
                .getItemImagesResult(itemId)
                .onSuccess { images ->
                    serverImages = images
                    updateState { it.copy(serverLoading = false) }
                }
                .onFailure { updateState { it.copy(serverLoading = false, serverFailed = true) } }
        }
    }

    fun loadRemoteImages() {
        remoteJob?.cancel()
        remoteJob = viewModelScope.launch {
            val includeAllLanguages = _uiState.value.includeAllLanguages
            remoteImages = emptyList()
            updateState { it.copy(remoteLoading = true, remoteFailed = false) }
            adminRepository
                .getRemoteImagesResult(itemId, includeAllLanguages)
                .onSuccess { images ->
                    remoteImages = images
                    updateState { it.copy(remoteLoading = false) }
                }
                .onFailure { updateState { it.copy(remoteLoading = false, remoteFailed = true) } }
        }
    }

    fun selectType(imageType: String) {
        updateState { it.copy(selectedType = imageType, provider = null) }
    }

    fun selectProvider(provider: String?) {
        updateState { it.copy(provider = provider) }
    }

    fun selectSort(sort: ImageSort) {
        updateState { it.copy(sort = sort) }
    }

    fun setIncludeAllLanguages(include: Boolean) {
        if (_uiState.value.includeAllLanguages == include) return
        updateState { it.copy(includeAllLanguages = include) }
        loadRemoteImages()
    }

    fun applyRemoteImage(image: ItemImage) {
        val url = image.remoteUrl ?: return
        runAction(EditImagesMessage.IMAGE_UPDATED) {
            adminRepository.downloadRemoteImage(
                itemId = itemId,
                imageType = image.imageType,
                imageUrl = url,
            )
        }
    }

    fun uploadImage(uri: Uri) {
        val imageType = _uiState.value.selectedType
        runAction(EditImagesMessage.IMAGE_UPDATED) {
            val picked = readPickedImage(uri)
            if (picked == null) {
                Result.failure(UnreadableFileException())
            } else {
                adminRepository.uploadImage(itemId, imageType, picked.first, picked.second)
            }
        }
    }

    fun deleteImage(image: ItemImage) {
        runAction(EditImagesMessage.IMAGE_REMOVED) {
            adminRepository.deleteImage(itemId, image.imageType, image.imageIndex)
        }
    }

    fun moveImage(fromPosition: Int, toPosition: Int) {
        val state = _uiState.value
        val current = state.currentImages
        if (state.busy || !state.canReorder || fromPosition == toPosition) return
        if (fromPosition !in current.indices || toPosition !in current.indices) return
        val imageType = state.selectedType
        val fromIndex = current[fromPosition].imageIndex ?: fromPosition
        val toIndex = current[toPosition].imageIndex ?: toPosition
        val reordered =
            current
                .toMutableList()
                .apply { add(toPosition, removeAt(fromPosition)) }
                .mapIndexed { index, image -> image.copy(imageIndex = index) }
        serverImages = serverImages.filterNot { it.imageType == imageType } + reordered
        runAction(successMessage = null) {
            adminRepository.moveImage(itemId, imageType, fromIndex, toIndex)
        }
    }

    fun consumeMessage() {
        updateState { it.copy(message = null) }
    }

    private fun runAction(successMessage: EditImagesMessage?, action: suspend () -> Result<Unit>) {
        if (_uiState.value.busy) return
        updateState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = action()
            val message =
                when {
                    result.isSuccess -> successMessage
                    result.exceptionOrNull() is UnreadableFileException ->
                        EditImagesMessage.FILE_UNREADABLE

                    else -> EditImagesMessage.ACTION_FAILED
                }
            updateState { it.copy(busy = false, message = message ?: it.message) }
            loadServerImages()
        }
    }

    private suspend fun readPickedImage(uri: Uri): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bytes =
                    resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                bytes to (resolver.getType(uri) ?: "image/jpeg")
            } catch (e: IOException) {
                Timber.e(e, "Failed to read picked image")
                null
            } catch (e: SecurityException) {
                Timber.e(e, "No access to picked image")
                null
            }
        }

    private fun updateState(transform: (EditImagesUiState) -> EditImagesUiState) {
        _uiState.update { derive(transform(it)) }
    }

    private fun derive(state: EditImagesUiState): EditImagesUiState {
        val selectedType = state.selectedType
        val knownTypes = buildSet {
            add(selectedType)
            addAll(supportedTypes)
            serverImages.mapTo(this) { it.imageType }
            remoteImages.mapTo(this) { it.imageType }
        }
        val types =
            IMAGE_TYPE_ORDER.filter { it in knownTypes } +
                knownTypes.filterNot { it in IMAGE_TYPE_ORDER }.sorted()
        val currentImages =
            serverImages.filter { it.imageType == selectedType }.sortedBy { it.imageIndex ?: 0 }
        val allowsMultiple = selectedType in MULTI_IMAGE_TYPES
        val ofType = remoteImages.filter { it.imageType == selectedType }
        val providers = ofType.mapNotNull { it.providerName }.distinct()
        val provider = state.provider?.takeIf { it in providers }
        val filtered =
            if (provider == null) ofType else ofType.filter { it.providerName == provider }
        val candidates =
            when (state.sort) {
                ImageSort.RECOMMENDED -> filtered
                ImageSort.LARGEST ->
                    filtered.sortedByDescending { it.width.toLong() * it.height.toLong() }
            }
        return state.copy(
            types = types,
            allowsMultiple = allowsMultiple,
            canReorder =
                allowsMultiple && currentImages.size > 1 && currentImages.all { it.isLocalFile },
            typeCounts = serverImages.groupingBy { it.imageType }.eachCount(),
            currentImages = currentImages,
            candidates = candidates,
            providers = providers,
            provider = provider,
        )
    }

    private class UnreadableFileException : Exception()
}
