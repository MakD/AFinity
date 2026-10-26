package com.makd.afinity.data.models.common

enum class CardSize(val value: String, val scale: Float) {
    EXTRA_SMALL("extra_small", 0.76f),
    SMALL("small", 0.87f),
    DEFAULT("default", 1f),
    LARGE("large", 1.15f);

    companion object {
        fun fromValue(value: String): CardSize {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}
