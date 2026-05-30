package com.makd.afinity.ui.player

import java.util.Locale

fun String?.toLocalizedLanguageName(): String? {
    if (this.isNullOrBlank() || this.lowercase() == "und") return this

    return try {
        val locale = Locale.forLanguageTag(this)
        val displayLanguage = locale.displayLanguage
        if (displayLanguage.isNotEmpty() && displayLanguage.lowercase() != this.lowercase()) {
            displayLanguage.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else {
            this
        }
    } catch (e: Exception) {
        this
    }
}
