package com.makd.afinity.player.profile

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import org.jellyfin.sdk.model.api.VideoRangeType

object DeviceCodecs {

    private val codecList by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    private val PCM_CODECS =
        listOf(
            "pcm_s8",
            "pcm_s16be",
            "pcm_s16le",
            "pcm_s24le",
            "pcm_s32le",
            "pcm_f32le",
            "pcm_alaw",
            "pcm_mulaw",
        )

    private val PROFILE_ORDER =
        mapOf(
            "h264" to
                listOf(
                    "high",
                    "constrained high",
                    "main",
                    "baseline",
                    "constrained baseline",
                    "extended",
                    "high 10",
                    "high 422",
                    "high 444",
                ),
            "hevc" to
                listOf("Main", "Main 10", "Main 10 HDR 10", "Main 10 HDR 10 Plus", "Main Still"),
            "vp9" to listOf("Profile 0", "Profile 1", "Profile 2", "Profile 3"),
        )

    private val FORCED_AUDIO_CODECS =
        PCM_CODECS + listOf("alac", "aac", "ac3", "eac3", "dts", "mlp", "truehd")

    val CONTAINERS =
        listOf("mp4", "fmp4", "webm", "mkv", "mp3", "ogg", "wav", "mpegts", "flv", "aac", "flac", "3gp")

    private val CONTAINER_VIDEO_CODECS =
        listOf(
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            listOf("vp8", "vp9", "av1"),
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9"),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf("mpeg1video", "mpeg2video", "mpeg4", "h264", "hevc"),
            listOf("mpeg4", "h264"),
            emptyList(),
            emptyList(),
            listOf("h263", "mpeg4", "h264", "hevc"),
        )

    private val CONTAINER_AUDIO_CODECS =
        listOf(
            listOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"),
            listOf("mp3", "aac", "ac3", "eac3"),
            listOf("vorbis", "opus"),
            PCM_CODECS +
                listOf(
                    "mp1",
                    "mp2",
                    "mp3",
                    "aac",
                    "vorbis",
                    "opus",
                    "flac",
                    "alac",
                    "ac3",
                    "eac3",
                    "dts",
                    "mlp",
                    "truehd",
                ),
            listOf("mp3"),
            listOf("vorbis", "opus", "flac"),
            PCM_CODECS,
            PCM_CODECS + listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd"),
            listOf("mp3", "aac"),
            listOf("aac"),
            listOf("flac"),
            listOf("3gpp", "aac", "flac"),
        )

    data class Capabilities(
        val videoCodecs: Set<String>,
        val audioCodecs: Set<String>,
        val videoProfiles: Map<String, Set<String>>,
        val videoLevels: Map<String, Int>,
        val videoRanges: Set<VideoRangeType>,
    ) {
        fun videoCodecsFor(containerIndex: Int): List<String> =
            CONTAINER_VIDEO_CODECS[containerIndex].filter { it in videoCodecs }

        fun audioCodecsFor(containerIndex: Int): List<String> =
            CONTAINER_AUDIO_CODECS[containerIndex].filter { it in audioCodecs }
    }

    fun detect(): Capabilities {
        val video = mutableSetOf<String>()
        val audio = mutableSetOf<String>()
        val profiles = mutableMapOf<String, MutableSet<String>>()
        val levels = mutableMapOf<String, Int>()
        val dolbyVisionProfiles = mutableSetOf<Int>()

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            for (mimeType in info.supportedTypes) {
                if (mimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) {
                    video.add("hevc")
                    val capabilities =
                        runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull() ?: continue
                    capabilities.profileLevels.forEach { dolbyVisionProfiles.add(it.profile) }
                    continue
                }
                val videoCodec = videoCodecFor(mimeType)
                if (videoCodec != null) {
                    video.add(videoCodec)
                    val capabilities =
                        runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull() ?: continue
                    val bucket = profiles.getOrPut(videoCodec) { mutableSetOf() }
                    for (profileLevel in capabilities.profileLevels) {
                        videoProfileName(videoCodec, profileLevel.profile)?.let(bucket::add)
                        videoLevelValue(videoCodec, profileLevel.level)?.let { value ->
                            val existing = levels[videoCodec]
                            if (existing == null || value > existing) levels[videoCodec] = value
                        }
                    }
                    continue
                }
                audioCodecFor(mimeType)?.let(audio::add)
            }
        }

        audio.addAll(FORCED_AUDIO_CODECS)

        return Capabilities(
            videoCodecs = video,
            audioCodecs = audio,
            videoProfiles = profiles.mapValues { orderedProfiles(it.key, it.value) },
            videoLevels = levels,
            videoRanges = videoRanges(dolbyVisionProfiles),
        )
    }

    private fun orderedProfiles(codec: String, names: Set<String>): Set<String> {
        val order = PROFILE_ORDER[codec] ?: return names
        return names
            .sortedBy { name -> order.indexOf(name).takeIf { it >= 0 } ?: order.size }
            .toSet()
    }

    private fun videoRanges(dolbyVisionProfiles: Set<Int>): Set<VideoRangeType> {
        val ranges =
            mutableSetOf(
                VideoRangeType.SDR,
                VideoRangeType.HDR10,
                VideoRangeType.HDR10_PLUS,
                VideoRangeType.HLG,
                VideoRangeType.DOVI_WITH_SDR,
                VideoRangeType.DOVI_WITH_HDR10,
                VideoRangeType.DOVI_WITH_HDR10_PLUS,
                VideoRangeType.DOVI_WITH_HLG,
            )
        if (dolbyVisionProfiles.isNotEmpty()) {
            ranges.add(VideoRangeType.DOVI_WITH_EL)
            ranges.add(VideoRangeType.DOVI_WITH_ELHDR10_PLUS)
        }
        if (CodecProfileLevel.DolbyVisionProfileDvheStn in dolbyVisionProfiles) {
            ranges.add(VideoRangeType.DOVI)
        }
        return ranges
    }

    private fun videoLevelValue(codec: String, level: Int): Int? =
        when (codec) {
            "h264" -> avcLevelValue(level)
            "hevc" -> hevcLevelValue(level)
            else -> null
        }

    private fun avcLevelValue(level: Int): Int? =
        when (level) {
            CodecProfileLevel.AVCLevel1 -> 10
            CodecProfileLevel.AVCLevel11 -> 11
            CodecProfileLevel.AVCLevel12 -> 12
            CodecProfileLevel.AVCLevel13 -> 13
            CodecProfileLevel.AVCLevel2 -> 20
            CodecProfileLevel.AVCLevel21 -> 21
            CodecProfileLevel.AVCLevel22 -> 22
            CodecProfileLevel.AVCLevel3 -> 30
            CodecProfileLevel.AVCLevel31 -> 31
            CodecProfileLevel.AVCLevel32 -> 32
            CodecProfileLevel.AVCLevel4 -> 40
            CodecProfileLevel.AVCLevel41 -> 41
            CodecProfileLevel.AVCLevel42 -> 42
            CodecProfileLevel.AVCLevel5 -> 50
            CodecProfileLevel.AVCLevel51 -> 51
            CodecProfileLevel.AVCLevel52 -> 52
            CodecProfileLevel.AVCLevel6 -> 60
            CodecProfileLevel.AVCLevel61 -> 61
            CodecProfileLevel.AVCLevel62 -> 62
            else -> null
        }

    private fun hevcLevelValue(level: Int): Int? =
        when (level) {
            CodecProfileLevel.HEVCMainTierLevel1,
            CodecProfileLevel.HEVCHighTierLevel1 -> 30
            CodecProfileLevel.HEVCMainTierLevel2,
            CodecProfileLevel.HEVCHighTierLevel2 -> 60
            CodecProfileLevel.HEVCMainTierLevel21,
            CodecProfileLevel.HEVCHighTierLevel21 -> 63
            CodecProfileLevel.HEVCMainTierLevel3,
            CodecProfileLevel.HEVCHighTierLevel3 -> 90
            CodecProfileLevel.HEVCMainTierLevel31,
            CodecProfileLevel.HEVCHighTierLevel31 -> 93
            CodecProfileLevel.HEVCMainTierLevel4,
            CodecProfileLevel.HEVCHighTierLevel4 -> 120
            CodecProfileLevel.HEVCMainTierLevel41,
            CodecProfileLevel.HEVCHighTierLevel41 -> 123
            CodecProfileLevel.HEVCMainTierLevel5,
            CodecProfileLevel.HEVCHighTierLevel5 -> 150
            CodecProfileLevel.HEVCMainTierLevel51,
            CodecProfileLevel.HEVCHighTierLevel51 -> 153
            CodecProfileLevel.HEVCMainTierLevel52,
            CodecProfileLevel.HEVCHighTierLevel52 -> 156
            CodecProfileLevel.HEVCMainTierLevel6,
            CodecProfileLevel.HEVCHighTierLevel6 -> 180
            CodecProfileLevel.HEVCMainTierLevel61,
            CodecProfileLevel.HEVCHighTierLevel61 -> 183
            CodecProfileLevel.HEVCMainTierLevel62,
            CodecProfileLevel.HEVCHighTierLevel62 -> 186
            else -> null
        }

    private fun videoCodecFor(mimeType: String): String? =
        when (mimeType) {
            MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
            MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
            MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
            MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
            MediaFormat.MIMETYPE_VIDEO_HEVC -> "hevc"
            MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
            else -> null
        }

    private fun audioCodecFor(mimeType: String): String? =
        when (mimeType) {
            MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
            MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
            MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
            MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
            MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
            MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
            MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
            MediaFormat.MIMETYPE_AUDIO_AMR_WB,
            MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "3gpp"
            else -> null
        }

    private fun videoProfileName(codec: String, profile: Int): String? =
        when (codec) {
            "h264" -> avcProfileName(profile)
            "hevc" -> hevcProfileName(profile)
            "vp8" -> if (profile == CodecProfileLevel.VP8ProfileMain) "main" else null
            "vp9" -> vp9ProfileName(profile)
            else -> null
        }

    private fun avcProfileName(profile: Int): String? =
        when (profile) {
            CodecProfileLevel.AVCProfileBaseline -> "baseline"
            CodecProfileLevel.AVCProfileMain -> "main"
            CodecProfileLevel.AVCProfileExtended -> "extended"
            CodecProfileLevel.AVCProfileHigh -> "high"
            CodecProfileLevel.AVCProfileHigh10 -> "high 10"
            CodecProfileLevel.AVCProfileHigh422 -> "high 422"
            CodecProfileLevel.AVCProfileHigh444 -> "high 444"
            CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
            CodecProfileLevel.AVCProfileConstrainedHigh -> "constrained high"
            else -> null
        }

    private fun hevcProfileName(profile: Int): String? =
        when (profile) {
            CodecProfileLevel.HEVCProfileMain -> "Main"
            CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
            CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
            CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
            CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
            else -> null
        }

    private fun vp9ProfileName(profile: Int): String? =
        when (profile) {
            CodecProfileLevel.VP9Profile0 -> "Profile 0"
            CodecProfileLevel.VP9Profile1 -> "Profile 1"
            CodecProfileLevel.VP9Profile2,
            CodecProfileLevel.VP9Profile2HDR -> "Profile 2"
            CodecProfileLevel.VP9Profile3,
            CodecProfileLevel.VP9Profile3HDR -> "Profile 3"
            else -> null
        }
}
