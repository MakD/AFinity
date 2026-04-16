package com.makd.afinity.util.logging

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingBufferTree(private val maxLines: Int = 500) : Timber.Tree() {

    private val buffer = ArrayDeque<String>(maxLines)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) return

        val line = buildString {
            append(dateFormat.format(Date()))
            append(' ')
            append(priorityChar(priority))
            append('/')
            append(tag ?: "App")
            append(": ")
            append(message)
        }

        synchronized(buffer) {
            if (buffer.size >= maxLines) buffer.removeFirst()
            buffer.addLast(line)
            t?.let {
                val trace = it.stackTraceToString().lines().take(20).joinToString("\n")
                if (buffer.size >= maxLines) buffer.removeFirst()
                buffer.addLast(trace)
            }
        }
    }

    fun dump(): String = synchronized(buffer) { buffer.joinToString("\n") }

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
}
