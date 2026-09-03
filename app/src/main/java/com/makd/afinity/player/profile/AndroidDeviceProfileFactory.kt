package com.makd.afinity.player.profile

import com.makd.afinity.data.models.player.VideoQuality
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceProfileFactory @Inject constructor() {

    private val capabilities by lazy { DeviceCodecs.detect() }

    fun createExoPlayerProfile(
        quality: VideoQuality,
        maxAudioChannels: Int,
        allowHdrPassthrough: Boolean,
        allowTranscoding: Boolean,
    ): DeviceProfile {
        val directPlayProfiles = mutableListOf<DirectPlayProfile>()
        val codecProfiles = mutableListOf<CodecProfile>()

        DeviceCodecs.CONTAINERS.forEachIndexed { index, container ->
            val videoCodecs = capabilities.videoCodecsFor(index)
            val audioCodecs = capabilities.audioCodecsFor(index)

            if (videoCodecs.isNotEmpty()) {
                directPlayProfiles.add(
                    DirectPlayProfile(
                        type = DlnaProfileType.VIDEO,
                        container = container,
                        videoCodec = videoCodecs.joinToString(","),
                        audioCodec = audioCodecs.joinToString(","),
                    )
                )
            }
            if (audioCodecs.isNotEmpty()) {
                directPlayProfiles.add(
                    DirectPlayProfile(
                        type = DlnaProfileType.AUDIO,
                        container = container,
                        audioCodec = audioCodecs.joinToString(","),
                    )
                )
            }
        }

        capabilities.videoCodecs.forEach { codec ->
            val profiles = capabilities.videoProfiles[codec].orEmpty()
            val maxLevel = capabilities.videoLevels[codec]
            val conditions = mutableListOf<ProfileCondition>()

            if (profiles.isNotEmpty()) {
                conditions.add(
                    ProfileCondition(
                        condition = ProfileConditionType.EQUALS_ANY,
                        property = ProfileConditionValue.VIDEO_PROFILE,
                        value = profiles.joinToString("|"),
                        isRequired = false,
                    )
                )
            }
            if (maxLevel != null) {
                conditions.add(
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.VIDEO_LEVEL,
                        value = maxLevel.toString(),
                        isRequired = false,
                    )
                )
            }
            if (conditions.isEmpty()) return@forEach

            codecProfiles.add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = codec,
                    applyConditions = emptyList(),
                    conditions = conditions,
                )
            )
        }

        globalVideoConditions(quality, allowHdrPassthrough)?.let(codecProfiles::add)

        return DeviceProfile(
            name = PROFILE_NAME,
            maxStreamingBitrate = streamingBitrate(quality),
            maxStaticBitrate = MAX_STATIC_BITRATE,
            musicStreamingTranscodingBitrate = MAX_MUSIC_TRANSCODING_BITRATE,
            directPlayProfiles = directPlayProfiles,
            transcodingProfiles =
                if (allowTranscoding) transcodingProfiles(maxAudioChannels) else emptyList(),
            containerProfiles = emptyList(),
            codecProfiles = codecProfiles,
            subtitleProfiles = EXO_SUBTITLE_PROFILES,
        )
    }

    fun createMpvProfile(
        quality: VideoQuality,
        maxAudioChannels: Int,
        allowHdrPassthrough: Boolean,
        allowTranscoding: Boolean,
    ): DeviceProfile {
        val codecProfiles = listOfNotNull(globalVideoConditions(quality, allowHdrPassthrough))

        return DeviceProfile(
            name = MPV_PROFILE_NAME,
            maxStreamingBitrate = streamingBitrate(quality),
            maxStaticBitrate = MAX_STATIC_BITRATE,
            musicStreamingTranscodingBitrate = MAX_MUSIC_TRANSCODING_BITRATE,
            directPlayProfiles =
                listOf(
                    DirectPlayProfile(type = DlnaProfileType.VIDEO, container = ""),
                    DirectPlayProfile(type = DlnaProfileType.AUDIO, container = ""),
                ),
            transcodingProfiles =
                if (allowTranscoding) transcodingProfiles(maxAudioChannels) else emptyList(),
            containerProfiles = emptyList(),
            codecProfiles = codecProfiles,
            subtitleProfiles = MPV_SUBTITLE_PROFILES,
        )
    }

    private fun globalVideoConditions(
        quality: VideoQuality,
        allowHdrPassthrough: Boolean,
    ): CodecProfile? {
        val conditions = mutableListOf<ProfileCondition>()

        quality.maxWidth?.let { width ->
            conditions.add(
                ProfileCondition(
                    condition = ProfileConditionType.LESS_THAN_EQUAL,
                    property = ProfileConditionValue.WIDTH,
                    value = width.toString(),
                    isRequired = true,
                )
            )
        }

        if (!allowHdrPassthrough) {
            conditions.add(
                ProfileCondition(
                    condition = ProfileConditionType.EQUALS_ANY,
                    property = ProfileConditionValue.VIDEO_RANGE_TYPE,
                    value = "SDR",
                    isRequired = true,
                )
            )
        }

        if (conditions.isEmpty()) return null

        return CodecProfile(
            type = CodecType.VIDEO,
            applyConditions = emptyList(),
            conditions = conditions,
        )
    }

    private fun streamingBitrate(quality: VideoQuality): Int =
        if (quality.maxBitrate > 0) quality.maxBitrate else MAX_STREAMING_BITRATE

    private fun transcodingAudioCodecs(): String {
        val supported = TS_AUDIO_CODECS.filter { it in capabilities.audioCodecs }
        return (if ("aac" in supported) supported else supported + "aac").joinToString(",")
    }

    private fun transcodingProfiles(maxAudioChannels: Int): List<TranscodingProfile> =
        listOf(
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "ts",
                videoCodec = "h264",
                audioCodec = transcodingAudioCodecs(),
                protocol = MediaStreamProtocol.HLS,
                context = EncodingContext.STREAMING,
                maxAudioChannels = maxAudioChannels.toString(),
                minSegments = 1,
                breakOnNonKeyFrames = false,
                conditions = emptyList(),
            ),
            TranscodingProfile(
                type = DlnaProfileType.AUDIO,
                container = "mp3",
                videoCodec = "",
                audioCodec = "mp3",
                protocol = MediaStreamProtocol.HTTP,
                context = EncodingContext.STREAMING,
                conditions = emptyList(),
            ),
        )

    private companion object {
        const val PROFILE_NAME = "AFinity Android"
        const val MPV_PROFILE_NAME = "AFinity Android (MPV)"

        const val MAX_STREAMING_BITRATE = 120_000_000
        const val MAX_STATIC_BITRATE = 100_000_000
        const val MAX_MUSIC_TRANSCODING_BITRATE = 384_000

        val TS_AUDIO_CODECS =
            listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd")

        val EXO_SUBTITLE_PROFILES =
            listOf("subrip", "srt", "ttml", "pgssub", "dvbsub").map {
                SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED)
            } +
                listOf("srt", "subrip", "ttml", "vtt", "webvtt", "ass", "ssa").map {
                    SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL)
                }

        val MPV_SUBTITLE_PROFILES =
            listOf("subrip", "srt", "ttml", "ass", "ssa", "pgssub", "dvbsub").map {
                SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED)
            } +
                listOf("srt", "subrip", "ttml", "vtt", "webvtt", "ass", "ssa", "sub", "idx").map {
                    SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL)
                }
    }
}
