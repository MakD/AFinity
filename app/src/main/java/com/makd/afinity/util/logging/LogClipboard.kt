package com.makd.afinity.util.logging

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object LogClipboard {

    suspend fun copy(context: Context, label: String, text: String) {
        withContext(Dispatchers.Main) {
            val manager = context.getSystemService<ClipboardManager>()
            if (manager == null) {
                Timber.w("Clipboard unavailable")
                return@withContext
            }
            val clip = ClipData.newPlainText(label, text)
            clip.description.extras =
                PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            manager.setPrimaryClip(clip)
        }
    }
}