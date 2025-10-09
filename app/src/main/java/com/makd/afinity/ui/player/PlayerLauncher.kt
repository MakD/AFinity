package com.makd.afinity.ui.player

import android.content.Context
import android.content.Intent
import timber.log.Timber
import java.util.UUID

object PlayerLauncher {

    fun launch(
        context: Context,
        itemId: UUID,
        mediaSourceId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = 0L
    ) {
        Timber.d("PlayerLauncher: Launching player for item $itemId")

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("itemId", itemId.toString())
            putExtra("mediaSourceId", mediaSourceId)
            audioStreamIndex?.let { putExtra("audioStreamIndex", it) }
            subtitleStreamIndex?.let { putExtra("subtitleStreamIndex", it) }
            putExtra("startPositionMs", startPositionMs)

            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }
}