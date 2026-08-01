package com.makd.afinity.ui.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.SettingsSection
import com.makd.afinity.data.repository.settings.SettingsImportFailure
import com.makd.afinity.data.repository.settings.SettingsImportPreview
import com.makd.afinity.data.repository.settings.SettingsImportResult
import com.makd.afinity.data.repository.settings.SettingsTransfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BackupUiState(
    val pendingExport: String? = null,
    val preview: SettingsImportPreview? = null,
    val selected: Set<SettingsSection> = emptySet(),
    val failure: SettingsImportFailure? = null,
    val imported: Boolean = false,
    val isBusy: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(private val settingsTransfer: SettingsTransfer) :
    ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun prepareExport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val payload =
                try {
                    settingsTransfer.export()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to build settings backup")
                    null
                }
            _uiState.update { it.copy(pendingExport = payload, isBusy = false) }
        }
    }

    fun onExportDelivered() {
        _uiState.update { it.copy(pendingExport = null) }
    }

    fun previewImport(raw: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val result = settingsTransfer.parse(raw)) {
                is SettingsImportResult.Ready ->
                    _uiState.update {
                        it.copy(
                            preview = result.preview,
                            selected = result.preview.sections.toSet(),
                            failure = null,
                            isBusy = false,
                        )
                    }
                is SettingsImportResult.Failed ->
                    _uiState.update {
                        it.copy(preview = null, failure = result.reason, isBusy = false)
                    }
            }
        }
    }

    fun toggleSection(section: SettingsSection) {
        _uiState.update { state ->
            val next =
                if (section in state.selected) state.selected - section
                else state.selected + section
            state.copy(selected = next)
        }
    }

    fun applyImport() {
        val state = _uiState.value
        val preview = state.preview ?: return
        if (state.selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            settingsTransfer.apply(preview, state.selected)
            _uiState.update { BackupUiState(imported = true) }
        }
    }

    fun dismiss() {
        _uiState.update { BackupUiState() }
    }
}