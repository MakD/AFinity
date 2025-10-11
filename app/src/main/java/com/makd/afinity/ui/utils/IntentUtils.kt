package com.makd.afinity.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import timber.log.Timber

object IntentUtils {

    fun openYouTubeUrl(context: Context, youtubeUrl: String?) {
        if (youtubeUrl.isNullOrBlank()) {
            Toast.makeText(context, "No trailer available", Toast.LENGTH_SHORT).show()
            Timber.w("Attempted to open null or blank YouTube URL")
            return
        }

        try {
            val videoId = extractYouTubeVideoId(youtubeUrl)

            if (videoId != null) {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                appIntent.setPackage("com.google.android.youtube")

                try {
                    context.startActivity(appIntent)
                    Timber.d("Opened YouTube video in app: $videoId")
                } catch (e: ActivityNotFoundException) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                    context.startActivity(webIntent)
                    Timber.d("Opened YouTube video in browser: $youtubeUrl")
                }
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                context.startActivity(webIntent)
                Timber.d("Opened trailer URL in browser: $youtubeUrl")
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open trailer", Toast.LENGTH_SHORT).show()
            Timber.e(e, "Failed to open YouTube URL: $youtubeUrl")
        }
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})".toRegex(),
            "v=([a-zA-Z0-9_-]{11})".toRegex()
        )

        for (pattern in patterns) {
            val matchResult = pattern.find(url)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }

        return null
    }
}