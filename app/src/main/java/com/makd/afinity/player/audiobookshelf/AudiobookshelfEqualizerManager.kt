package com.makd.afinity.player.audiobookshelf

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class EqualizerPreset(val displayName: String, val gains: List<Int>) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0)),
    VOICE_BOOST("Voice Boost", listOf(2, 4, 5, 3, 1)),
    BASS_BOOST("Bass Boost", listOf(5, 3, 0, -1, -2)),
    TREBLE_BOOST("Treble Boost", listOf(-2, -1, 0, 3, 5)),
    PODCAST("Podcast", listOf(3, 5, 4, 2, 0)),
    AUDIOBOOK("Audiobook", listOf(1, 3, 5, 4, 2)),
    REDUCE_NOISE("Reduce Noise", listOf(-3, -1, 0, -1, -3)),
    LOUDNESS("Loudness", listOf(4, 2, 0, 2, 4)),
    CUSTOM("Custom", listOf(0, 0, 0, 0, 0)),
}

val EQ_BAND_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
const val EQ_MIN_DB = -15
const val EQ_MAX_DB = 15

data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandGains: List<Int> = List(5) { 0 },
    val isAvailable: Boolean = false,
    val volumeBoostDb: Int = 0,
)

@Singleton
class AudiobookshelfEqualizerManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs =
        context.getSharedPreferences("audiobookshelf_equalizer", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadPersistedState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private fun loadPersistedState(): EqualizerState {
        val isEnabled = prefs.getBoolean("is_enabled", false)
        val presetName = prefs.getString("preset", EqualizerPreset.FLAT.name)
        val preset =
            try {
                EqualizerPreset.valueOf(presetName ?: EqualizerPreset.FLAT.name)
            } catch (e: IllegalArgumentException) {
                EqualizerPreset.FLAT
            }
        val bandGains = (0 until 5).map { i -> prefs.getInt("band_$i", 0) }
        val volumeBoostDb = prefs.getInt("volume_boost_db", 0)
        return EqualizerState(
            isEnabled = isEnabled,
            currentPreset = preset,
            bandGains = bandGains,
            isAvailable = false,
            volumeBoostDb = volumeBoostDb,
        )
    }

    fun attachToSession(audioSessionId: Int) {
        releaseEqualizer()
        val currentState = _state.value
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = currentState.isEnabled
            currentState.bandGains.forEachIndexed { index, db ->
                eq.setBandLevel(index.toShort(), (db * 100).toShort())
            }
            equalizer = eq
            Timber.d("Equalizer attached to audio session $audioSessionId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach equalizer to session $audioSessionId")
        }
        try {
            val enhancer = LoudnessEnhancer(audioSessionId)
            val boostDb = currentState.volumeBoostDb
            if (boostDb > 0) {
                enhancer.setTargetGain(boostDb * 100)
                enhancer.enabled = true
            } else {
                enhancer.enabled = false
            }
            loudnessEnhancer = enhancer
            Timber.d("LoudnessEnhancer attached to audio session $audioSessionId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach loudness enhancer to session $audioSessionId")
        }
        _state.value = currentState.copy(isAvailable = true)
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Timber.e(e, "Failed to set equalizer enabled state")
        }
        _state.value = _state.value.copy(isEnabled = enabled)
        prefs.edit { putBoolean("is_enabled", enabled) }
    }

    fun applyPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        val gains = preset.gains
        try {
            gains.forEachIndexed { index, db ->
                equalizer?.setBandLevel(index.toShort(), (db * 100).toShort())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply preset $preset")
        }
        _state.value = _state.value.copy(currentPreset = preset, bandGains = gains)
        persistPresetAndGains(preset, gains)
    }

    fun setBandGain(index: Int, gainDb: Int) {
        val clamped = gainDb.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
        try {
            equalizer?.setBandLevel(index.toShort(), (clamped * 100).toShort())
        } catch (e: Exception) {
            Timber.e(e, "Failed to set band $index gain to $clamped dB")
        }
        val newGains = _state.value.bandGains.toMutableList().also { it[index] = clamped }
        _state.value =
            _state.value.copy(currentPreset = EqualizerPreset.CUSTOM, bandGains = newGains)
        prefs.edit {
            putString("preset", EqualizerPreset.CUSTOM.name)
                .putInt("band_$index", clamped)
        }
    }

    fun setVolumeBoost(db: Int) {
        val clamped = db.coerceIn(0, 10)
        try {
            if (clamped == 0) {
                loudnessEnhancer?.enabled = false
            } else {
                loudnessEnhancer?.setTargetGain(clamped * 100)
                loudnessEnhancer?.enabled = true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set volume boost to $clamped dB")
        }
        _state.value = _state.value.copy(volumeBoostDb = clamped)
        prefs.edit { putInt("volume_boost_db", clamped) }
    }

    fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing equalizer")
        }
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing loudness enhancer")
        }
        equalizer = null
        loudnessEnhancer = null
        _state.value = _state.value.copy(isAvailable = false)
    }

    private fun persistPresetAndGains(preset: EqualizerPreset, gains: List<Int>) {
        prefs.edit().apply {
            putString("preset", preset.name)
            gains.forEachIndexed { index, db -> putInt("band_$index", db) }
            apply()
        }
    }
}