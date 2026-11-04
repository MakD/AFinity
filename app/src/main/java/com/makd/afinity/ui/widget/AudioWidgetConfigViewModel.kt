package com.makd.afinity.ui.widget

import android.appwidget.AppWidgetManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.widget.AudioWidgetSettings
import com.makd.afinity.widget.AudioWidgetSettingsStore
import com.makd.afinity.widget.AudioWidgetState
import com.makd.afinity.widget.AudioWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AudioWidgetConfigViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsStore: AudioWidgetSettingsStore,
    private val updater: AudioWidgetUpdater,
) : ViewModel() {

    val widgetId: Int =
        savedStateHandle.get<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

    val previewState: AudioWidgetState? = updater.currentState()

    private val _settings = MutableStateFlow<AudioWidgetSettings?>(null)
    val settings: StateFlow<AudioWidgetSettings?> = _settings.asStateFlow()

    init {
        viewModelScope.launch { _settings.value = settingsStore.get(widgetId) }
    }

    fun update(transform: (AudioWidgetSettings) -> AudioWidgetSettings) {
        _settings.update { it?.let(transform) }
    }

    fun save(onSaved: () -> Unit) {
        val current = _settings.value ?: return
        viewModelScope.launch {
            settingsStore.save(widgetId, current)
            updater.start()
            updater.refreshAsync()
            onSaved()
        }
    }
}
