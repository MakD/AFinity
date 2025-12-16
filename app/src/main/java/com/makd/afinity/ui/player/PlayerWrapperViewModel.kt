package com.makd.afinity.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlayerWrapperViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val apiClient: ApiClient
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

                val userId = try {
                    var user = databaseRepository.getCurrentUser()

                    if (user == null) {
                        val allUsers = databaseRepository.getAllUsers()
                        user = allUsers.firstOrNull()
                        if (user != null) {
                            Timber.d("PlayerWrapperViewModel: No current user, using first available user: ${user.id}")
                        }
                    } else {
                        Timber.d("PlayerWrapperViewModel: Current user from database: ${user.id}")
                    }

                    var userIdResult: UUID? = null
                    if (user == null) {
                        val userIdString = preferencesRepository.getCurrentUserId()
                        if (userIdString != null) {
                            Timber.d("PlayerWrapperViewModel: Using userId from preferences: $userIdString")
                            try {
                                userIdResult = UUID.fromString(userIdString)
                            } catch (e: Exception) {
                                Timber.w("PlayerWrapperViewModel: Invalid UUID in preferences: $userIdString")
                            }
                        } else {
                            Timber.w("PlayerWrapperViewModel: No userId in preferences either")
                        }
                    } else {
                        userIdResult = user.id
                    }

                    // TODO: The real fix is to save userId to Preferences during login For now, use API client's userId
                    if (userIdResult == null) {
                        try {
                            val currentUser = apiClient.userApi.getCurrentUser().content
                            if (currentUser != null) {
                                userIdResult = currentUser.id
                                Timber.d("PlayerWrapperViewModel: Using userId from API client: $userIdResult")

                                preferencesRepository.setCurrentUserId(userIdResult.toString())
                                Timber.d("PlayerWrapperViewModel: Saved userId to preferences for future offline use")
                            }
                        } catch (e: Exception) {
                            Timber.w("PlayerWrapperViewModel: Could not get userId from API: ${e.message}")
                        }
                    }

                    userIdResult
                } catch (e: Exception) {
                    Timber.w(e, "PlayerWrapperViewModel: Failed to get userId")
                    null
                }

                if (userId != null) {
                    Timber.d("PlayerWrapperViewModel: Trying to load from database with userId: $userId")
                    try {
                        loadedItem = databaseRepository.getMovie(itemId, userId)
                        Timber.d("PlayerWrapperViewModel: Movie query result: ${if (loadedItem != null) "found" else "not found"}")

                        if (loadedItem == null) {
                            loadedItem = databaseRepository.getEpisode(itemId, userId)
                            Timber.d("PlayerWrapperViewModel: Episode query result: ${if (loadedItem != null) "found" else "not found"}")
                        }

                        if (loadedItem != null) {
                            Timber.d("PlayerWrapperViewModel: Loaded item from database: ${loadedItem.name}")
                        } else {
                            Timber.w("PlayerWrapperViewModel: Item not found in database")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "PlayerWrapperViewModel: Could not load from database")
                    }
                } else {
                    Timber.w("PlayerWrapperViewModel: No userId available, skipping database lookup")
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
                        Timber.w(e, "PlayerWrapperViewModel: Could not load from API (likely offline)")
                    }
                }

                if (loadedItem == null) {
                    Timber.e("PlayerWrapperViewModel: Failed to load item from both database and API")
                }

                _item.value = loadedItem
                Timber.d("PlayerWrapperViewModel: Set item value to: ${if (loadedItem != null) loadedItem.name else "null"}")
            } catch (e: Exception) {
                Timber.e(e, "PlayerWrapperViewModel: Failed to load item: $itemId")
                _item.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}