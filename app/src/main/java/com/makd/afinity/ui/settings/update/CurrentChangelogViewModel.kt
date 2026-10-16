package com.makd.afinity.ui.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.updater.GitHubApiService
import com.makd.afinity.data.updater.models.GitHubRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CurrentChangelogState {
    data object Loading : CurrentChangelogState

    data class Loaded(val release: GitHubRelease) : CurrentChangelogState

    data object NotPublished : CurrentChangelogState

    data object Failed : CurrentChangelogState
}

@HiltViewModel
class CurrentChangelogViewModel
@Inject
constructor(private val gitHubApiService: GitHubApiService) : ViewModel() {

    val tagName: String = "v" + AppConstants.VERSION_NAME.removePrefix("v")

    private val _state = MutableStateFlow<CurrentChangelogState>(CurrentChangelogState.Loading)
    val state: StateFlow<CurrentChangelogState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        if (_state.value is CurrentChangelogState.Loaded || loadJob?.isActive == true) return
        _state.value = CurrentChangelogState.Loading
        loadJob = viewModelScope.launch {
            _state.value =
                gitHubApiService
                    .getReleaseByTag(tagName)
                    .fold(
                        onSuccess = { release ->
                            if (release != null) CurrentChangelogState.Loaded(release)
                            else CurrentChangelogState.NotPublished
                        },
                        onFailure = { CurrentChangelogState.Failed },
                    )
        }
    }
}
