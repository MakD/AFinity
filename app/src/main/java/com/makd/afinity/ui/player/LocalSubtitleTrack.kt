package com.makd.afinity.ui.player

import com.makd.afinity.data.repository.subtitles.SubtitleLanguageCodes
import java.util.Locale

enum class LocalSubtitleOrigin {
    DEVICE,
    DEVICE_OUT_OF_SYNC,
    SERVER_ADDED,
}

data class LocalSubtitleTrack(
    val fileName: String,
    val label: String,
    val origin: LocalSubtitleOrigin,
)

fun subtitleLanguageDisplayName(code: String): String {
    val tag = SubtitleLanguageCodes.toLanguageTag(code)
    val name = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())
    return name
        .takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
        ?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } ?: code.uppercase()
}
