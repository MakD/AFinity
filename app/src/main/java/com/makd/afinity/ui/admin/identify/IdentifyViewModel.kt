package com.makd.afinity.ui.admin.identify

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.AdminRequestStillRunningException
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MOVIE = "Movie"
private const val BOX_SET = "BoxSet"
private val IDENTIFIABLE_TYPES = setOf(MOVIE, "Series", BOX_SET)

enum class IdentifyMessage(@param:StringRes val textRes: Int) {
    APPLY_FAILED(R.string.admin_identify_apply_failed),
    APPLY_STILL_RUNNING(R.string.admin_identify_apply_still_running),
}

data class IdentifyUiState(
    val target: IdentifyTarget? = null,
    val searchName: String = "",
    val year: String = "",
    val showYear: Boolean = true,
    val providers: List<ExternalIdProvider> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val results: List<IdentifyResult> = emptyList(),
    val hasSearched: Boolean = false,
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
    val applying: Boolean = false,
    val applied: Boolean = false,
    val replaceAllImages: Boolean = true,
    val message: IdentifyMessage? = null,
) {
    val canSearch: Boolean
        get() =
            !searching && (searchName.isNotBlank() || providerIds.values.any { it.isNotBlank() })
}

@HiltViewModel
class IdentifyViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private var itemType: String = savedStateHandle["itemType"] ?: MOVIE
    private var nameEdited = false
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(IdentifyUiState(showYear = itemType != BOX_SET))
    val uiState: StateFlow<IdentifyUiState> = _uiState.asStateFlow()

    init {
        loadItem()
    }

    private fun loadItem() {
        viewModelScope.launch {
            val target = adminRepository.getIdentifyTarget(itemId)
            if (target != null) {
                if (target.type in IDENTIFIABLE_TYPES) itemType = target.type
                _uiState.update {
                    it.copy(
                        target = target,
                        searchName = if (nameEdited) it.searchName else target.name,
                        showYear = itemType != BOX_SET,
                    )
                }
            }
            val providers =
                adminRepository.getExternalIdProviders(itemId).filter {
                    it.type == null || it.type == itemType
                }
            _uiState.update { it.copy(providers = providers) }
        }
    }

    fun updateSearchName(name: String) {
        nameEdited = true
        _uiState.update { it.copy(searchName = name) }
    }

    fun updateYear(year: String) = _uiState.update {
        it.copy(year = year.filter(Char::isDigit).take(4))
    }

    fun updateProviderId(key: String, value: String) = _uiState.update {
        it.copy(providerIds = it.providerIds + (key to value))
    }

    fun toggleReplaceImages() = _uiState.update { it.copy(replaceAllImages = !it.replaceAllImages) }

    fun search() {
        val state = _uiState.value
        if (!state.canSearch || state.applying) return
        val name = state.searchName.trim()
        val year = if (state.showYear) state.year.toIntOrNull() else null
        val providerIds =
            state.providerIds.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searching = true,
                    hasSearched = true,
                    searchFailed = false,
                    results = emptyList(),
                )
            }
            adminRepository
                .searchRemoteResult(itemId, itemType, name, year, providerIds)
                .onSuccess { results ->
                    _uiState.update { it.copy(searching = false, results = results) }
                }
                .onFailure { _uiState.update { it.copy(searching = false, searchFailed = true) } }
        }
    }

    fun searchWithoutYear() {
        _uiState.update { it.copy(year = "") }
        search()
    }

    fun applyResult(result: IdentifyResult) {
        if (_uiState.value.applying) return
        _uiState.update { it.copy(applying = true, message = null) }
        viewModelScope.launch {
            val outcome =
                adminRepository.applyIdentifyResult(
                    itemId = itemId,
                    result = result,
                    replaceAllImages = _uiState.value.replaceAllImages,
                )
            _uiState.update {
                it.copy(
                    applying = false,
                    applied = outcome.isSuccess,
                    message =
                        when {
                            outcome.isSuccess -> null
                            outcome.exceptionOrNull() is AdminRequestStillRunningException ->
                                IdentifyMessage.APPLY_STILL_RUNNING

                            else -> IdentifyMessage.APPLY_FAILED
                        },
                )
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
