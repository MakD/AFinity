package com.makd.afinity.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlayerWrapperViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _item = MutableStateFlow<AfinityItem?>(null)
    val item: StateFlow<AfinityItem?> = _item.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadItem(itemId: UUID) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val loadedItem = jellyfinRepository.getItemById(itemId)
                _item.value = loadedItem
                Timber.d("Loaded item: ${loadedItem?.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load item: $itemId")
                _item.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}