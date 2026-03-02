package com.makd.afinity.player.audiobookshelf

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfSkipSilenceManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs =
        context.getSharedPreferences("audiobookshelf_skip_silence", Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit { putBoolean("enabled", enabled) }
    }
}