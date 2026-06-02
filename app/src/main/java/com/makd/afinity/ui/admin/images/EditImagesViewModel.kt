package com.makd.afinity.ui.admin.images

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.ItemImage
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditImagesUiState(
    val loading: Boolean = false,
    val loadingRemote: Boolean = false,
    val applying: Boolean = false,
    val serverImages: List<ItemImage> = emptyList(),
    val remoteImages: List<ItemImage> = emptyList(),
    val selectedType: String = "Primary",
    val includeAllLanguages: Boolean = false,
    val error: String? = null,
    val actionSuccess: Boolean = false,
)

@HiltViewModel
class EditImagesViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EditImagesUiState())
    val uiState: StateFlow<EditImagesUiState> = _uiState.asStateFlow()

    init {
        loadServerImages()
        loadRemoteImages()
    }

    private fun loadServerImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val images = adminRepository.getItemImages(itemId)
            _uiState.update { it.copy(loading = false, serverImages = images) }
        }
    }

    fun selectType(imageType: String) {
        _uiState.update { it.copy(selectedType = imageType, remoteImages = emptyList()) }
        loadRemoteImages(imageType)
    }

    fun loadRemoteImages(imageType: String = _uiState.value.selectedType) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingRemote = true) }
            val remote = adminRepository.getRemoteImages(
                itemId = itemId,
                imageType = imageType,
                includeAllLanguages = _uiState.value.includeAllLanguages,
            )
            _uiState.update { it.copy(loadingRemote = false, remoteImages = remote) }
        }
    }

    fun toggleIncludeAllLanguages() {
        _uiState.update { it.copy(includeAllLanguages = !it.includeAllLanguages) }
        loadRemoteImages()
    }

    fun applyRemoteImage(image: ItemImage) {
        val url = image.remoteUrl ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, error = null) }
            val result = adminRepository.downloadRemoteImage(
                itemId = itemId,
                imageType = image.imageType,
                imageUrl = url,
            )
            _uiState.update {
                it.copy(
                    applying = false,
                    actionSuccess = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
            }
            if (result.isSuccess) loadServerImages()
        }
    }

    fun uploadImage(imageType: String, data: ByteArray, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, error = null) }
            val result = adminRepository.uploadImage(itemId, imageType, data, mimeType)
            _uiState.update {
                it.copy(
                    applying = false,
                    actionSuccess = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
            }
            if (result.isSuccess) loadServerImages()
        }
    }

    fun deleteImage(image: ItemImage) {
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, error = null) }
            val result = adminRepository.deleteImage(itemId, image.imageType, image.imageIndex)
            _uiState.update {
                it.copy(
                    applying = false,
                    actionSuccess = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
            }
            if (result.isSuccess) loadServerImages()
        }
    }

    fun clearActionSuccess() = _uiState.update { it.copy(actionSuccess = false) }
}