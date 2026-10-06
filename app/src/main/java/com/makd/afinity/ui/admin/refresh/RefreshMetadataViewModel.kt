package com.makd.afinity.ui.admin.refresh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

private const val MODE_DEFAULT = "Default"
private const val MODE_FULL_REFRESH = "FullRefresh"

enum class RefreshMode {
    Scan,
    Missing,
    ReplaceAll,
}

@HiltViewModel
class RefreshMetadataViewModel @Inject constructor(private val adminRepository: AdminRepository) :
    ViewModel() {

    fun refresh(
        itemId: String,
        mode: RefreshMode,
        replaceImages: Boolean,
        regenerateTrickplay: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        val fullRefresh = mode != RefreshMode.Scan
        val refreshMode = if (fullRefresh) MODE_FULL_REFRESH else MODE_DEFAULT
        viewModelScope.launch {
            val result =
                adminRepository.refreshItem(
                    itemId = itemId,
                    metadataRefreshMode = refreshMode,
                    imageRefreshMode = refreshMode,
                    replaceAllMetadata = mode == RefreshMode.ReplaceAll,
                    replaceAllImages = fullRefresh && replaceImages,
                    regenerateTrickplay = fullRefresh && regenerateTrickplay,
                )
            onResult(result.isSuccess)
        }
    }
}
