package com.makd.afinity.data.models.common

enum class HeroStyle(val value: String) {
    FULL_BLEED("full_bleed"),
    PEEK("peek"),
    INSET_CARD("inset_card");

    companion object {
        fun fromValue(value: String): HeroStyle {
            return entries.find { it.value == value } ?: FULL_BLEED
        }
    }
}
