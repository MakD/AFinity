package com.makd.afinity.ui.player

import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
fun List<Tracks.Group>.getTrackNames(): Array<String> {
    return map { group ->
            val format = group.mediaTrackGroup.getFormat(0)

            val languageName =
                format.language
                    ?.takeIf { it.isNotBlank() && it != "und" }
                    ?.let { languageTag ->
                        Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault())
                    }

            listOfNotNull(format.label, languageName, format.codecs).joinToString(" - ")
        }
        .toTypedArray()
}
