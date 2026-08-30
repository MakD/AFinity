package com.makd.afinity.util.logging

import android.util.Log

data class LogEntry(
    val sequence: Long,
    val timeMillis: Long,
    val priority: Int,
    val tag: String,
    val message: String,
    val stackTrace: String?,
) {
    val level: LogLevel
        get() = LogLevel.fromPriority(priority)
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    companion object {
        fun fromPriority(priority: Int): LogLevel =
            when {
                priority >= Log.ERROR -> ERROR
                priority == Log.WARN -> WARN
                priority == Log.INFO -> INFO
                else -> DEBUG
            }
    }
}
