package com.makd.afinity.cast

import javax.inject.Inject
import javax.inject.Singleton
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

@Singleton
class CastDeviceProfileFactory @Inject constructor() {

    fun createProfile(enableHevc: Boolean, maxBitrate: Int): DeviceProfile {
        val videoCodecs = buildString {
            append("h264,vp8")
            if (enableHevc) append(",hevc")
        }

        val directPlayProfiles = buildList {
            add(
                DirectPlayProfile(
                    container = "mp4,webm,mkv",
                    videoCodec = videoCodecs,
                    audioCodec = "aac,mp3,opus,flac,vorbis",
                    type = DlnaProfileType.VIDEO,
                )
            )
        }

        val transcodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                videoCodec = "h264",
                audioCodec = "aac",
                type = DlnaProfileType.VIDEO,
                context = EncodingContext.STREAMING,
                protocol = MediaStreamProtocol.HLS,
                maxAudioChannels = "6",
                breakOnNonKeyFrames = false,
                conditions = emptyList(),
            )
        )

        val codecProfiles = buildList {
            add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.VIDEO_LEVEL,
                            value = "51",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "3840",
                            isRequired = false,
                        ),
                    ),
                    applyConditions = emptyList(),
                )
            )
            if (enableHevc) {
                add(
                    CodecProfile(
                        type = CodecType.VIDEO,
                        codec = "hevc",
                        conditions = listOf(
                            ProfileCondition(
                                condition = ProfileConditionType.LESS_THAN_EQUAL,
                                property = ProfileConditionValue.VIDEO_LEVEL,
                                value = "153",
                                isRequired = false,
                            ),
                            ProfileCondition(
                                condition = ProfileConditionType.LESS_THAN_EQUAL,
                                property = ProfileConditionValue.WIDTH,
                                value = "3840",
                                isRequired = false,
                            ),
                        ),
                        applyConditions = emptyList(),
                    )
                )
            }
            add(
                CodecProfile(
                    type = CodecType.VIDEO_AUDIO,
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.AUDIO_CHANNELS,
                            value = "6",
                            isRequired = false,
                        ),
                    ),
                    applyConditions = emptyList(),
                )
            )
        }

        val subtitleProfiles = listOf(
            SubtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile("ass", SubtitleDeliveryMethod.ENCODE),
            SubtitleProfile("ssa", SubtitleDeliveryMethod.ENCODE),
            SubtitleProfile("sub", SubtitleDeliveryMethod.ENCODE),
            SubtitleProfile("pgssub", SubtitleDeliveryMethod.ENCODE),
            SubtitleProfile("dvdsub", SubtitleDeliveryMethod.ENCODE),
        )

        return DeviceProfile(
            name = "Chromecast",
            maxStaticBitrate = maxBitrate,
            maxStreamingBitrate = maxBitrate,
            directPlayProfiles = directPlayProfiles,
            transcodingProfiles = transcodingProfiles,
            codecProfiles = codecProfiles,
            containerProfiles = emptyList(),
            subtitleProfiles = subtitleProfiles,
        )
    }
}