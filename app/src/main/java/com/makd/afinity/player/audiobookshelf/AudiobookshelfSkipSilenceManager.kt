package com.makd.afinity.player.audiobookshelf

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class AudiobookshelfSkipSilenceManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs =
        context.getSharedPreferences("audiobookshelf_skip_silence", Context.MODE_PRIVATE)

    @OptIn(UnstableApi::class)
    val processor = SilenceSkippingAudioProcessor()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    init {
        applyToProcessor(_isEnabled.value)
    }

    @OptIn(UnstableApi::class)
    fun setEnabled(enabled: Boolean) {
        try {
            applyToProcessor(enabled)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set skip silence enabled=$enabled")
        }
        _isEnabled.value = enabled
        prefs.edit { putBoolean("enabled", enabled) }
    }

    @OptIn(UnstableApi::class)
    private fun applyToProcessor(enabled: Boolean) {
        processor.setEnabled(enabled)
    }
}