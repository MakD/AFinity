package com.makd.afinity.util.logging

import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import timber.log.Timber

object SdkLogBridge {

    fun install(minimumLevel: Level = Level.DEBUG) {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = minimumLevel
        KotlinLoggingConfiguration.direct.appender = TimberAppender
    }

    private object TimberAppender : Appender {
        override fun log(loggingEvent: KLoggingEvent) {
            val tree = Timber.tag(shortTag(loggingEvent.loggerName))
            val message = loggingEvent.message.orEmpty()
            val cause = loggingEvent.cause

            when (loggingEvent.level) {
                Level.ERROR -> tree.e(cause, message)
                Level.WARN -> tree.w(cause, message)
                Level.INFO -> tree.i(cause, message)
                else -> tree.d(cause, message)
            }
        }
    }

    private fun shortTag(loggerName: String): String =
        loggerName.substringAfterLast('.').removeSuffix("Kt").ifEmpty { "JellyfinSdk" }
}
