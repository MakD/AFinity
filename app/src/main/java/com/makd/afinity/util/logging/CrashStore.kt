package com.makd.afinity.util.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CrashReport(
    val id: String,
    val timeMillis: Long,
    val exceptionClass: String,
    val exceptionMessage: String?,
    val topAppFrame: String?,
    val thread: String?,
    val build: String?,
    val device: String?,
    val stackTrace: String,
    val precedingLogs: String,
    val precedingLineCount: Int,
) {
    val simpleExceptionClass: String
        get() = exceptionClass.substringAfterLast('.')
}

@Singleton
class CrashStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun load(): List<CrashReport> =
        withContext(Dispatchers.IO) {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.isDirectory) return@withContext emptyList()
            crashDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .mapNotNull { parse(it) }
                .sortedByDescending { it.timeMillis }
        }

    suspend fun delete(report: CrashReport) {
        withContext(Dispatchers.IO) { File(File(context.filesDir, CRASH_DIR), report.id).delete() }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            File(context.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
        }
    }

    private fun parse(file: File): CrashReport? {
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val header = text.substringBefore(TRACE_HEADER)
        val trace = text.substringAfter(TRACE_HEADER, "").substringBefore(LOGS_HEADER).trim()
        val logs = text.substringAfter(LOGS_HEADER, "").trim()

        val firstLine = trace.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val exceptionClass = firstLine.substringBefore(':').trim().ifBlank { UNKNOWN_EXCEPTION }
        val message = firstLine.substringAfter(':', "").trim().ifBlank { null }

        val topAppFrame =
            trace
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("at $APP_PACKAGE") }
                ?.removePrefix("at ")

        return CrashReport(
            id = file.name,
            timeMillis = timeOf(file),
            exceptionClass = exceptionClass,
            exceptionMessage = message,
            topAppFrame = topAppFrame,
            thread = header.valueFor(THREAD_KEY),
            build = header.valueFor(BUILD_KEY),
            device = header.valueFor(DEVICE_KEY),
            stackTrace = trace,
            precedingLogs = logs,
            precedingLineCount = if (logs.isBlank()) 0 else logs.lineSequence().count(),
        )
    }

    private fun String.valueFor(key: String): String? =
        lineSequence().firstOrNull { it.startsWith(key) }?.removePrefix(key)?.trim()?.ifBlank { null }

    private fun timeOf(file: File): Long {
        val stamp = file.name.removePrefix("crash_").removeSuffix(".txt")
        return runCatching { SimpleDateFormat(FILE_STAMP, Locale.US).parse(stamp)?.time }
            .getOrNull() ?: file.lastModified()
    }

    companion object {
        const val CRASH_DIR = "crashes"
        const val TRACE_HEADER = "--- Stack Trace ---"
        const val LOGS_HEADER = "--- Last App Logs Before Crash ---"
        const val THREAD_KEY = "Thread:"
        const val BUILD_KEY = "Build:"
        const val DEVICE_KEY = "Device:"
        private const val FILE_STAMP = "yyyyMMdd_HHmmss"
        private const val APP_PACKAGE = "com.makd.afinity"
        private const val UNKNOWN_EXCEPTION = "Unknown crash"
    }
}