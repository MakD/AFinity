package com.makd.afinity.util

import android.content.Context
import com.makd.afinity.R

private const val BYTES_PER_KB = 1024.0
private const val BYTES_PER_MB = BYTES_PER_KB * 1024
private const val BYTES_PER_GB = BYTES_PER_MB * 1024

fun formatFileSize(context: Context, bytes: Long): String =
    when {
        bytes < BYTES_PER_KB -> context.getString(R.string.file_size_b_fmt, bytes)
        bytes < BYTES_PER_MB -> context.getString(R.string.file_size_kb_fmt, bytes / BYTES_PER_KB)
        bytes < BYTES_PER_GB -> context.getString(R.string.file_size_mb_fmt, bytes / BYTES_PER_MB)
        else -> context.getString(R.string.file_size_gb_fmt, bytes / BYTES_PER_GB)
    }