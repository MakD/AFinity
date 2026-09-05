package com.makd.afinity.util.logging

import android.content.Context
import android.os.Build
import com.makd.afinity.BuildConfig
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
            val crashDir = File(context.filesDir, CrashStore.CRASH_DIR)
            if (!crashDir.exists() && !crashDir.mkdirs()) {
                return
            }
            crashDir.mkdirs()

            crashDir
                .listFiles()
                ?.sortedBy { it.lastModified() }
                ?.dropLast(4)
                ?.forEach { it.delete() }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")

            val buildType = if (BuildConfig.DEBUG) "debug" else "release"

            crashFile.writeText(
                buildString {
                    appendLine("FATAL CRASH at $timestamp")
                    appendLine("${CrashStore.THREAD_KEY} ${thread.name}")
                    appendLine(
                        "${CrashStore.BUILD_KEY} ${BuildConfig.VERSION_NAME} " +
                            "(${BuildConfig.VERSION_CODE}) $buildType"
                    )
                    appendLine(
                        "${CrashStore.DEVICE_KEY} ${Build.MANUFACTURER} ${Build.MODEL} · " +
                            "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
                    )
                    appendLine()
                    appendLine(CrashStore.TRACE_HEADER)
                    appendLine(LogRedactor.redact(throwable.stackTraceToString()))
                    appendLine()
                    appendLine(CrashStore.LOGS_HEADER)
                    appendLine(ringBufferTree?.dump() ?: "(no ring buffer available)")
                }
            )
        } catch (_: Exception) {} finally {
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }
}