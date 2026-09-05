package com.makd.afinity.util.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.makd.afinity.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    private const val MASK = "[REDACTED]"

    suspend fun export(
        context: Context,
        ringBufferTree: RingBufferTree?,
        secretsToRedact: List<String> = emptyList(),
        entries: List<LogEntry>? = null,
    ) =
        withContext(Dispatchers.IO) {
            try {
                val logContent = buildString {
                    val crashDir = File(context.filesDir, CrashStore.CRASH_DIR)
                    if (crashDir.exists()) {
                        val crashFiles = crashDir.listFiles()?.sortedBy { it.lastModified() }
                        if (!crashFiles.isNullOrEmpty()) {
                            appendLine("========== PREVIOUS FATAL CRASHES ==========")
                            crashFiles.forEach { file ->
                                appendLine(file.readText())
                                appendLine("============================================")
                            }
                            appendLine()
                        }
                    }

                    appendLine(buildHeader(context))
                    appendLine("=".repeat(60))
                    appendLine()
                    if (entries != null) {
                        appendLine("--- App Logs (filtered view, ${entries.size} lines) ---")
                        appendLine(
                            ringBufferTree?.format(entries) ?: "(no ring buffer available)"
                        )
                    } else {
                        appendLine("--- App Logs ---")
                        appendLine(ringBufferTree?.dump() ?: "(no ring buffer available)")
                        appendLine()
                        appendLine("--- System Logcat (this process) ---")
                        appendLine(captureLogcat())
                    }
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                share(
                    context = context,
                    fileName = "afinity_logs_$timestamp.txt",
                    subject = "AFinity logs — $timestamp",
                    content = scrub(logContent, secretsToRedact),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to export logs")
            }
        }

    suspend fun exportCrash(
        context: Context,
        report: CrashReport,
        secretsToRedact: List<String> = emptyList(),
    ) =
        withContext(Dispatchers.IO) {
            try {
                val content = buildString {
                    appendLine(buildHeader(context))
                    appendLine("=".repeat(60))
                    appendLine()
                    appendLine(buildCrashSummary(report))
                    appendLine()
                    appendLine(CrashStore.TRACE_HEADER)
                    appendLine(report.stackTrace)
                    appendLine()
                    appendLine(CrashStore.LOGS_HEADER)
                    appendLine(report.precedingLogs)
                }

                share(
                    context = context,
                    fileName = "afinity_${report.id}",
                    subject = "AFinity crash — ${report.simpleExceptionClass}",
                    content = scrub(content, secretsToRedact),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to export crash report")
            }
        }

    internal fun scrub(content: String, secretsToRedact: List<String>): String {
        var result = content
        secretsToRedact
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .forEach { secret -> result = result.replace(secret, MASK) }
        return result
    }

    internal fun buildCrashSummary(report: CrashReport): String = buildString {
        appendLine("${report.exceptionClass}${report.exceptionMessage?.let { ": $it" }.orEmpty()}")
        report.topAppFrame?.let { appendLine("at $it") }
        report.thread?.let { appendLine("Thread: $it") }
        report.build?.let { appendLine("Build: $it") }
        report.device?.let { appendLine("Device: $it") }
    }
        .trimEnd()

    internal fun buildHeader(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
            .getOrNull()

        val versionName = packageInfo?.versionName ?: BuildConfig.VERSION_NAME
        val versionCode = packageInfo?.longVersionCode ?: 0L
        val buildType = if (BuildConfig.DEBUG) "debug" else "release"

        return buildString {
            appendLine("AFinity $versionName ($versionCode) [$buildType]")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        }
            .trimEnd()
    }

    private fun share(context: Context, fileName: String, subject: String, content: String) {
        val logDir = File(context.cacheDir, "logs")
        if (!logDir.exists() && !logDir.mkdirs()) {
            Timber.e("Failed to create log directory. Device might be full.")
            return
        }
        logDir.listFiles()?.forEach { it.delete() }

        val logFile = File(logDir, fileName)
        logFile.writeText(content)

        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)

        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        context.startActivity(
            Intent.createChooser(shareIntent, "Share Logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun captureLogcat(): String {
        return try {
            val pid = android.os.Process.myPid()
            val process =
                ProcessBuilder(
                        "logcat",
                        "-d",
                        "--pid=$pid",
                        "-v",
                        "time",
                        "View:W",
                        "ViewRootImpl:W",
                        "Choreographer:W",
                        "OpenGLRenderer:W",
                        "HWUI:W",
                        "*:I",
                    )
                    .redirectErrorStream(true)
                    .start()
            LogRedactor.redact(process.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            Timber.w(e, "Failed to capture logcat")
            "(logcat unavailable: ${e.message})"
        }
    }
}