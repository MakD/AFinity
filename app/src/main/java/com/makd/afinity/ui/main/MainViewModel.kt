package com.makd.afinity.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.AppDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(private val appDataRepository: AppDataRepository) :
    ViewModel() {

    val uiState: StateFlow<MainUiState> =
        appDataRepository.userProfileImageUrl
            .map { MainUiState(userProfileImageUrl = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MainUiState(),
            )
}
