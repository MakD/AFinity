package com.makd.afinity.ui.player.utils

import android.content.Context
import android.media.AudioManager
import timber.log.Timber

class VolumeManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getCurrentVolume(): Int {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return ((currentVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()
    }

    fun setVolume(volumePercent: Int) {
        try {
            val clampedVolume = volumePercent.coerceIn(0, 100)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = ((clampedVolume.toFloat() / 100f) * maxVolume).toInt()

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                0
            )

            Timber.d("System volume set to $clampedVolume% (raw: $targetVolume/$maxVolume)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set system volume: $volumePercent")
        }
    }

    fun adjustVolume(delta: Int) {
        val currentVolume = getCurrentVolume()
        val newVolume = (currentVolume + delta).coerceIn(0, 100)
        setVolume(newVolume)
    }

    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
}