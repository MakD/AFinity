package com.makd.afinity.data.models.common

enum class CardSize(val value: String, val scale: Float, val gridColumnDelta: Int) {
    EXTRA_SMALL("extra_small", 0.7f, 2),
    SMALL("small", 0.8f, 1),
    DEFAULT("default", 1f, 0),
    LARGE("large", 1.15f, -1);

    companion object {
        fun fromValue(value: String): CardSize {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}
