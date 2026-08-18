package com.makd.afinity.util

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateSkeleton {
    const val MONTH_DAY = "MMMd"
    const val MONTH_DAY_LONG = "MMMMd"
    const val MONTH_DAY_YEAR = "MMMdy"
    const val MONTH_DAY_YEAR_LONG = "MMMMdy"
    const val WEEKDAY_MONTH_DAY = "EEEMMMd"

    fun withTime(skeleton: String, is24Hour: Boolean): String =
        skeleton + if (is24Hour) "Hm" else "hm"
}

fun localizedPattern(locale: Locale, skeleton: String): String =
    DateFormat.getBestDateTimePattern(locale, skeleton)

fun localizedDateFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(localizedPattern(locale, skeleton), locale)

fun localizedDateFormat(locale: Locale, skeleton: String): SimpleDateFormat =
    SimpleDateFormat(localizedPattern(locale, skeleton), locale)