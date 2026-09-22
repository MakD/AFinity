package com.makd.afinity.util.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

class RingBufferTree(private val maxLines: Int = 2000) : Timber.Tree() {

    private val buffer = ArrayDeque<LogEntry>(INITIAL_CAPACITY)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var nextSequence = 0L

    val capacity: Int
        get() = maxLines

    val launchTimeMillis: Long = System.currentTimeMillis()

    private val _updates =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val updates: SharedFlow<Unit> = _updates

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) return

        val fullStackTrace = t?.stackTraceToString()
        val baseMessage =
            if (fullStackTrace == null) message
            else message.removeSuffix("\n$fullStackTrace").trimEnd()

        synchronized(buffer) {
            val created =
                LogEntry(
                    sequence = nextSequence++,
                    timeMillis = System.currentTimeMillis(),
                    priority = priority,
                    tag = tag ?: "App",
                    message = LogRedactor.redact(baseMessage),
                    stackTrace =
                        fullStackTrace?.let {
                            LogRedactor.redact(
                                it.lines().take(STACK_TRACE_LINES).joinToString("\n")
                            )
                        },
                )
            if (buffer.size >= maxLines) buffer.removeFirst()
            buffer.addLast(created)
        }

        _updates.tryEmit(Unit)
    }

    fun snapshot(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        _updates.tryEmit(Unit)
    }

    fun dump(): String = format(snapshot())

    fun format(entries: List<LogEntry>): String = buildString {
        entries.forEach { entry ->
            append(dateFormat.format(Date(entry.timeMillis)))
            append(' ')
            append(priorityChar(entry.priority))
            append('/')
            append(entry.tag)
            append(": ")
            append(entry.message)
            append('\n')
            entry.stackTrace?.let {
                append(it)
                append('\n')
            }
        }
    }

    private fun priorityChar(priority: Int) =
        when (priority) {
            Log.VERBOSE -> 'V'
            Log.DEBUG -> 'D'
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR -> 'E'
            Log.ASSERT -> 'A'
            else -> '?'
        }

    private companion object {
        const val INITIAL_CAPACITY = 256
        const val STACK_TRACE_LINES = 20
    }
}
