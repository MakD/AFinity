package com.makd.afinity.data.models.player

enum class AssRenderMode(val value: String) {
    OFF("off"),
    CUES("cues"),
    OVERLAY("overlay");

    companion object {
        val default = CUES

        fun fromValue(value: String): AssRenderMode {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            OFF -> "Off (plain text)"
            CUES -> "Standard"
            OVERLAY -> "Smooth animations (experimental)"
        }
    }
}