package com.makd.afinity.data.models.player

const val SLEEP_TIMER_EXTEND_MINUTES = 15
const val SLEEP_TIMER_PROMPT_THRESHOLD_MS = 30_000L

sealed class SleepTimerMode {
    data class Duration(val minutes: Int) : SleepTimerMode()

    data object EndOfItem : SleepTimerMode()
}
