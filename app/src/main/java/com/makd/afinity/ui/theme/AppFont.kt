package com.makd.afinity.ui.theme

enum class AppFont {
    DEFAULT,
    GOOGLE_SANS,
    QUICKSAND,
    IBM_PLEX_SANS,
    IBM_PLEX_SANS_CONDENSED;

    companion object {
        fun fromString(value: String?): AppFont {
            return entries.find { it.name == value } ?: DEFAULT
        }
    }
}
