package com.makd.afinity.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.DatabaseRepository
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
    private val jellyfinRepository: JellyfinRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _item = MutableStateFlow<AfinityItem?>(null)
    val item: StateFlow<AfinityItem?> = _item.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadItem(itemId: UUID) {
        viewModelScope.launch {
            _isLoading.value = true
            Timber.d("PlayerWrapperViewModel: Loading item $itemId")
            try {
                var loadedItem: AfinityItem? = null

                val userId = sessionManager.currentSession.value?.userId ?: run {
                    Timber.w("No active session found in PlayerWrapperViewModel, trying DB fallback")
                    databaseRepository.getAllUsers().firstOrNull()?.id
                }

                if (userId != null) {
                    Timber.d("PlayerWrapperViewModel: Using userId: $userId")

                    try {
                        loadedItem = databaseRepository.getMovie(itemId, userId)
                            ?: databaseRepository.getEpisode(itemId, userId)

                        if (loadedItem != null) {
                            Timber.d("PlayerWrapperViewModel: Loaded item from database: ${loadedItem.name}")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "PlayerWrapperViewModel: Database lookup failed")
                    }
                } else {
                    Timber.e("PlayerWrapperViewModel: Could not determine userId")
                }

                if (loadedItem == null) {
                    Timber.d("PlayerWrapperViewModel: Trying to load from API")
                    try {
                        loadedItem = jellyfinRepository.getItemById(itemId)
                        if (loadedItem != null) {
                            Timber.d("PlayerWrapperViewModel: Loaded item from API: ${loadedItem.name}")
                        } else {
                            Timber.w("PlayerWrapperViewModel: Item not found via API")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "PlayerWrapperViewModel: API load failed (likely offline)")
                    }
                }

                if (loadedItem == null) {
                    Timber.e("PlayerWrapperViewModel: Failed to load item from both database and API")
                }

                _item.value = loadedItem
            } catch (e: Exception) {
                Timber.e(e, "PlayerWrapperViewModel: Critical failure loading item $itemId")
                _item.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}