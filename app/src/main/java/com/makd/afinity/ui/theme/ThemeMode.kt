package com.makd.afinity.ui.theme

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED");

    companion object {
        fun fromString(value: String): ThemeMode {
            return entries.find { it.name == value } ?: SYSTEM
        }
    }
}