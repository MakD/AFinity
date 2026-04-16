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

    suspend fun export(
        context: Context,
        ringBufferTree: RingBufferTree?,
        secretsToRedact: List<String> = emptyList(),
    ) =
        withContext(Dispatchers.IO) {
            try {
                var logContent = buildString {
                    val crashDir = File(context.cacheDir, "crashes")
                    val crashFiles = crashDir.listFiles()?.sortedBy { it.lastModified() }
                    if (!crashFiles.isNullOrEmpty()) {
                        appendLine("========== PREVIOUS FATAL CRASHES ==========")
                        crashFiles.forEach { file ->
                            appendLine(file.readText())
                            appendLine("============================================")
                            file.delete()
                        }
                        appendLine()
                    }

                    appendLine(buildHeader(context))
                    appendLine("=".repeat(60))
                    appendLine()
                    appendLine("--- App Logs ---")
                    appendLine(ringBufferTree?.dump() ?: "(no ring buffer available)")
                    appendLine()
                    appendLine("--- System Logcat (this process) ---")
                    appendLine(captureLogcat())
                }
                secretsToRedact
                    .filter { it.isNotBlank() }
                    .sortedByDescending { it.length }
                    .forEach { secret -> logContent = logContent.replace(secret, "[REDACTED]") }

                val logDir = File(context.cacheDir, "logs")
                logDir.mkdirs()
                logDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val logFile = File(logDir, "afinity_logs_$timestamp.txt")
                logFile.writeText(logContent)

                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logFile,
                    )

                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "AFinity logs — $timestamp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                context.startActivity(
                    Intent.createChooser(shareIntent, "Share Logs").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to export logs")
            }
        }

    private fun buildHeader(context: Context): String {
        val packageInfo =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
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

    private fun captureLogcat(): String {
        return try {
            val pid = android.os.Process.myPid()
            val process =
                ProcessBuilder("logcat", "-d", "--pid=$pid", "-v", "time", "*:I")
                    .redirectErrorStream(true)
                    .start()
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Timber.w(e, "Failed to capture logcat")
            "(logcat unavailable: ${e.message})"
        }
    }
}
