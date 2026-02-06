package com.makd.afinity.data.models.common

enum class EpisodeLayout(val value: String) {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");

    companion object {
        fun fromValue(value: String): EpisodeLayout {
            return entries.find { it.value == value } ?: HORIZONTAL
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            HORIZONTAL -> "Horizontal Scroll"
            VERTICAL -> "Vertical List"
        }
    }
}
