package com.makd.afinity.data.models.player

enum class SkipMode {
    BUTTON,
    AUTO_SKIP,
    DISABLED;

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.name == value } ?: BUTTON
    }

    fun getDisplayName(): String = when (this) {
        BUTTON -> "Show button"
        AUTO_SKIP -> "Auto-skip"
        DISABLED -> "Disabled"
    }
}