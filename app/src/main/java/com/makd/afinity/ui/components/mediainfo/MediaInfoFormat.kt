package com.makd.afinity.ui.components.mediainfo

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.util.formatFileSize
import java.util.Locale

internal class MediaInfoFormat(private val context: Context) {

    private val locale: Locale = context.resources.configuration.locales[0]

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun bitrate(bitsPerSecond: Long): String =
        if (bitsPerSecond < 1_000_000L) {
            context.getString(R.string.player_quality_kbps_fmt, (bitsPerSecond / 1000L).toInt())
        } else {
            context.getString(
                R.string.player_quality_mbps_fmt,
                String.format(locale, "%.1f", bitsPerSecond / 1_000_000.0),
            )
        }

    fun size(bytes: Long): String = formatFileSize(context, bytes)

    fun runtime(ticks: Long): String {
        val totalMinutes = (ticks + 599_999_999L) / 600_000_000L
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        return if (hours > 0) {
            context.getString(R.string.meta_runtime_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.meta_runtime_minutes, minutes)
        }
    }

    fun clock(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(locale, "%d:%02d", minutes, secs)
        }
    }

    fun decimal(value: Double, maxFractionDigits: Int): String {
        val text = String.format(Locale.ROOT, "%.${maxFractionDigits}f", value)
        val trimmed = if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
        return trimmed.replace('.', decimalSeparator())
    }

    private fun decimalSeparator(): Char =
        java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator

    fun kiloHertz(sampleRate: Int): String = decimal(sampleRate / 1000.0, 1)

    fun yesNo(value: Boolean): String =
        context.getString(if (value) R.string.media_info_yes else R.string.media_info_no)

    fun presence(flag: Int?): String? = flag?.let {
        context.getString(if (it == 1) R.string.media_info_present else R.string.media_info_absent)
    }

    fun files(count: Int): String =
        context.resources.getQuantityString(R.plurals.media_info_files, count, count)
}

internal fun prettyContainer(container: String?): String? =
    container?.takeIf { it.isNotBlank() }?.split(',')?.joinToString(", ") { it.trim().uppercase() }

internal fun prettyColor(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when (raw.lowercase()) {
        "smpte2084" -> "PQ (SMPTE 2084)"
        "arib-std-b67" -> "HLG (ARIB STD-B67)"
        "bt2020nc" -> "BT.2020 NC"
        "bt2020c" -> "BT.2020 C"
        "bt2020",
        "bt2020-10",
        "bt2020-12" -> "BT.2020"
        "bt709" -> "BT.709"
        "bt470bg" -> "BT.470 BG"
        "smpte170m" -> "SMPTE 170M"
        else -> raw
    }
}

internal fun shortTransfer(value: String?): String? =
    when (value?.lowercase()) {
        "smpte2084" -> "PQ"
        "arib-std-b67" -> "HLG"
        else -> prettyColor(value)
    }

internal fun prettyAudioCodec(codec: String, profile: String?): String {
    val lowerProfile = profile?.lowercase().orEmpty()
    val base =
        when (codec.lowercase()) {
            "eac3" -> "Dolby Digital+"
            "ac3" -> "Dolby Digital"
            "truehd" -> "Dolby TrueHD"
            "dts" ->
                when {
                    lowerProfile.contains("dts:x") || lowerProfile.contains("dts-x") -> "DTS:X"
                    lowerProfile == "ma" || lowerProfile.contains("dts-hd ma") -> "DTS-HD MA"
                    lowerProfile == "hra" || lowerProfile.contains("dts-hd hra") -> "DTS-HD HR"
                    else -> "DTS"
                }
            "opus" -> "Opus"
            "vorbis" -> "Vorbis"
            else -> codec.uppercase()
        }
    return if (lowerProfile.contains("atmos")) "$base Atmos" else base
}

internal fun prettySubtitleCodec(codec: String): String =
    when (codec.lowercase()) {
        "subrip",
        "srt" -> "SRT"
        "ass",
        "ssa" -> "ASS"
        "webvtt",
        "vtt" -> "VTT"
        "pgssub",
        "pgs",
        "hdmv_pgs_subtitle" -> "PGS"
        "dvdsub",
        "vobsub",
        "dvd_subtitle" -> "VobSub"
        "dvbsub",
        "dvb_subtitle" -> "DVB"
        "mov_text" -> "TX3G"
        else -> codec.uppercase()
    }

internal fun channelLabel(layout: String?, channels: Int?): String? {
    val lower = layout?.lowercase()
    return when {
        lower == null -> channels?.let { if (it == 2) "2.0" else if (it == 1) "1.0" else null }
        lower.contains("7.1") -> "7.1"
        lower.contains("5.1") -> "5.1"
        lower.contains("2.1") -> "2.1"
        lower.contains("stereo") || lower.contains("2.0") -> "2.0"
        lower.contains("mono") -> "1.0"
        else -> layout
    }
}
