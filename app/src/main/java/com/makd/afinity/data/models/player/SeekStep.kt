package com.makd.afinity.data.models.player

import androidx.annotation.DrawableRes
import com.makd.afinity.R

enum class SeekStep(
    val seconds: Int,
    @param:DrawableRes val backIcon: Int,
    @param:DrawableRes val forwardIcon: Int,
) {
    FIVE(5, R.drawable.ic_rewind_backward_5, R.drawable.ic_rewind_forward_5),
    TEN(10, R.drawable.ic_rewind_backward_10, R.drawable.ic_rewind_forward_10),
    FIFTEEN(15, R.drawable.ic_rewind_backward_15, R.drawable.ic_rewind_forward_15),
    THIRTY(30, R.drawable.ic_rewind_backward_30, R.drawable.ic_rewind_forward_30);

    val millis: Long
        get() = seconds * 1000L

    companion object {
        val defaultBack = TEN
        val defaultForward = THIRTY

        fun fromSeconds(seconds: Int?, default: SeekStep): SeekStep =
            entries.find { it.seconds == seconds } ?: default
    }
}
