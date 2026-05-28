package com.makd.afinity.util

import android.media.MediaCodecList
import android.media.MediaFormat

object MediaCapabilities {
    private val codecList by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    fun getSupportedVideoCodecs(): String {
        val supported = mutableListOf("h264", "mpeg4", "mpeg2video")

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            if (!info.isHardwareAccelerated) {
                continue
            }

            val types = info.supportedTypes

            if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }) {
                if (!supported.contains("hevc")) supported.add("hevc")
            }
            if (types.any { it.equals("video/x-vnd.on2.vp9", ignoreCase = true) }) {
                if (!supported.contains("vp9")) supported.add("vp9")
            }
            if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) }) {
                if (!supported.contains("av1")) supported.add("av1")
            }
        }
        return supported.joinToString(",")
    }

    fun getSupportedAudioCodecs(): String {
        val supported = mutableListOf("aac", "mp3", "flac", "vorbis", "opus")

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue

            val types = info.supportedTypes
            if (types.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AC3, ignoreCase = true) }) {
                if (!supported.contains("ac3")) supported.add("ac3")
            }
            if (types.any { it.equals(MediaFormat.MIMETYPE_AUDIO_EAC3, ignoreCase = true) }) {
                if (!supported.contains("eac3")) supported.add("eac3")
            }
        }
        return supported.joinToString(",")
    }
}
