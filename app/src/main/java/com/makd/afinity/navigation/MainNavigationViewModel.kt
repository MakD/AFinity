package com.makd.afinity.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainNavigationViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository,
    private val authRepository: AuthRepository,
    private val jellyfinRepository: JellyfinRepository,
    val watchlistRepository: WatchlistRepository
) : ViewModel() {

    val appLoadingState = combine(
        appDataRepository.isInitialDataLoaded,
        appDataRepository.loadingProgress,
        appDataRepository.loadingPhase
    ) { isLoaded, progress, phase ->
        AppLoadingState(
            isLoading = !isLoaded,
            loadingProgress = progress,
            loadingPhase = phase
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLoadingState(isLoading = true)
    )

    init {
        observeAuthAndLoadData()
        refreshServerInfo()
    }

    private fun observeAuthAndLoadData() {
        var previousAuthState = false

        viewModelScope.launch {
            authRepository.isAuthenticated.collect { isAuthenticated ->
                Timber.d("Auth state changed - isAuthenticated: $isAuthenticated, previous: $previousAuthState")

                if (isAuthenticated && !previousAuthState) {
                    val isDataLoaded = appDataRepository.isInitialDataLoaded.value
                    Timber.d("Fresh login detected - isDataLoaded: $isDataLoaded - triggering data load")
                    loadAppData()
                }

                previousAuthState = isAuthenticated
            }
        }
    }

    private fun refreshServerInfo() {
        viewModelScope.launch {
            try {
                jellyfinRepository.refreshServerInfo()
                Timber.d("Server info refreshed on app start")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh server info on app start")
            }
        }
    }

    private fun loadAppData() {
        viewModelScope.launch {
            try {
                appDataRepository.loadInitialData()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load app data")
            }
        }
    }

    fun retry() {
        loadAppData()
    }

    suspend fun resolvePlayableItem(item: AfinityItem): AfinityItem? {
        return try {
            if (item is AfinityShow) {
                val episode = jellyfinRepository.getEpisodeToPlay(item.id)
                if (episode == null) {
                    Timber.w("No episode found to play for series: ${item.name}")
                }
                episode
            } else {
                item
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve playable item for: ${item.name}")
            null
        }
    }
}

data class AppLoadingState(
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingPhase: String = "",
    val error: String? = null
)