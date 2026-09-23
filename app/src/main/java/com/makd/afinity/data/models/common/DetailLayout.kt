package com.makd.afinity.data.models.common

enum class DetailLayout(val value: String) {
    CLASSIC("classic"),
    MODERN("modern");

    companion object {
        fun fromValue(value: String): DetailLayout {
            return entries.find { it.value == value } ?: CLASSIC
        }
    }
}
