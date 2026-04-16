package com.makd.afinity.util.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashFileExporter(
    private val context: Context,
    private val ringBufferTree: RingBufferTree?,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashDir = File(context.cacheDir, "crashes")
            crashDir.mkdirs()

            crashDir
                .listFiles()
                ?.sortedBy { it.lastModified() }
                ?.dropLast(4)
                ?.forEach { it.delete() }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")

            crashFile.writeText(
                buildString {
                    appendLine("FATAL CRASH at $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine("--- Stack Trace ---")
                    appendLine(throwable.stackTraceToString())
                    appendLine()
                    appendLine("--- Last App Logs Before Crash ---")
                    appendLine(ringBufferTree?.dump() ?: "(no ring buffer available)")
                }
            )
        } catch (_: Exception) {} finally {
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }
}
