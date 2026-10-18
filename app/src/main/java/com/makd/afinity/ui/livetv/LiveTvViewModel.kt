package com.makd.afinity.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LiveTvViewModel @Inject constructor(private val liveTvRepository: LiveTvRepository) :
    ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val refresh =
        VisibleRefresh(viewModelScope, REFRESH_INTERVAL) {
            if (_uiState.value.hasLiveTvAccess) loadCategorizedPrograms()
        }

    init {
        checkLiveTvAccess()
    }

    fun setScreenVisible(visible: Boolean) = refresh.setVisible(visible)

    fun retry() {
        if (_uiState.value.accessCheckFailed) {
            _uiState.update { it.copy(isLoading = true, accessCheckFailed = false) }
            checkLiveTvAccess()
        } else {
            _uiState.update { it.copy(isCategoriesLoading = true, programsFailed = false) }
            refresh.invalidate()
        }
    }

    private fun checkLiveTvAccess() {
        viewModelScope.launch {
            liveTvRepository
                .hasLiveTvAccessResult()
                .onSuccess { hasAccess ->
                    _uiState.update { it.copy(hasLiveTvAccess = hasAccess, isLoading = false) }
                    if (hasAccess) refresh.invalidate()
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to check Live TV access")
                    }
                    _uiState.update { it.copy(isLoading = false, accessCheckFailed = true) }
                }
        }
    }

    private suspend fun loadCategorizedPrograms() {
        val results = coroutineScope {
            LiveTvCategory.entries
                .map { category ->
                    async {
                        val programCategory = category.programCategory
                        category to
                            if (programCategory == null) {
                                liveTvRepository.getOnNowResult(ROW_LIMIT)
                            } else {
                                liveTvRepository.getUpcomingResult(programCategory, ROW_LIMIT)
                            }
                    }
                }
                .awaitAll()
        }

        results.forEach { (category, result) ->
            result.exceptionOrNull()?.let { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to load Live TV row $category")
                }
            }
        }

        val loaded = results.mapNotNull { (category, result) ->
            result.getOrNull()?.let { category to it }
        }
        val allFailed = loaded.isEmpty()

        _uiState.update {
            it.copy(
                categorizedPrograms = if (allFailed) it.categorizedPrograms else loaded.toMap(),
                isCategoriesLoading = false,
                programsFailed = allFailed,
            )
        }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
        const val ROW_LIMIT = 12
    }
}
