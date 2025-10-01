package com.makd.afinity.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainNavigationViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository,
    private val authRepository: AuthRepository
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
}

data class AppLoadingState(
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingPhase: String = "",
    val error: String? = null
)