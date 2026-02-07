package com.makd.afinity.ui.player

import android.os.Build
import androidx.media3.common.Tracks
import java.util.Locale

@androidx.media3.common.util.UnstableApi
fun List<Tracks.Group>.getTrackNames(): Array<String> {
    return this.map { group ->
            val nameParts: MutableList<String?> = mutableListOf()
            val format = group.mediaTrackGroup.getFormat(0)
            nameParts.run {
                add(format.label)
                add(
                    format.language?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Locale(it.split("-").last()).displayLanguage
                        } else {
                            @Suppress("DEPRECATION") Locale(it.split("-").last()).displayLanguage
                        }
                    }
                )
                add(format.codecs)
                filterNotNull().joinToString(separator = " - ")
            }
        }
        .toTypedArray()
}
