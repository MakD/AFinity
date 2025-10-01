package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import timber.log.Timber

object PipHelper {

    private var canEnterPipMode = false

    fun setCanEnterPip(canEnter: Boolean) {
        canEnterPipMode = canEnter
        Timber.d("PiP availability: $canEnter")
    }

    fun canEnterPip(): Boolean = canEnterPipMode

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPipMode(context: Context): Boolean {
        if (!canEnterPipMode) {
            Timber.w("Cannot enter PiP - not available")
            return false
        }

        val activity = context as? Activity ?: return false

        return try {
            val aspectRatio = Rational(16, 9)

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()

            val result = activity.enterPictureInPictureMode(params)
            Timber.d("Entered PiP mode: $result")
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to enter PiP mode")
            false
        }
    }
}