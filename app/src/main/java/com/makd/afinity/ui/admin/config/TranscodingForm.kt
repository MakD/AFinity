package com.makd.afinity.ui.admin.config

import com.makd.afinity.R

private const val HARDWARE_KEY = "HardwareAccelerationType"
private const val MIN_SEGMENT_SECONDS = 10L
private const val MAX_SEGMENT_SECONDS = 3_600L
private const val MAX_CRF = 51L
private const val MAX_THREAD_CHOICE = 16

private fun hardwareIs(vararg types: String) = FieldCondition(HARDWARE_KEY, types.toSet())

private val ANY_HARDWARE =
    hardwareIs("amf", "nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox", "v4l2m2m")
private val TONE_MAPPING_HARDWARE =
    hardwareIs("amf", "nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox")
private val TONE_MAPPING_OR_SOFTWARE =
    hardwareIs("none", "amf", "nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox")
private val DEEP_COLOR_HARDWARE = hardwareIs("amf", "nvenc", "qsv", "vaapi", "rkmpp")
private val RANGE_EXTENSION_HARDWARE = hardwareIs("nvenc", "qsv", "vaapi")
private val INTEL_HARDWARE = hardwareIs("qsv", "vaapi")
private val VP8_HARDWARE = hardwareIs("nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox")

private val THREAD_OPTIONS =
    listOf(ChoiceOption("-1", R.string.config_auto)) +
        (1..MAX_THREAD_CHOICE).map { ChoiceOption(it.toString()) } +
        ChoiceOption("0", R.string.config_max)

internal val TRANSCODING_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.TRANSCODING,
        title = R.string.config_transcoding_title,
        note = R.string.config_transcoding_note,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_hw_acceleration,
                    listOf(
                        ChoiceField(
                            HARDWARE_KEY,
                            R.string.config_hw_acceleration,
                            listOf(
                                ChoiceOption("none", R.string.config_none),
                                ChoiceOption("amf", text = "AMD AMF"),
                                ChoiceOption("nvenc", text = "Nvidia NVENC"),
                                ChoiceOption("qsv", text = "Intel Quick Sync (QSV)"),
                                ChoiceOption("vaapi", text = "Video Acceleration API (VA-API)"),
                                ChoiceOption("rkmpp", text = "Rockchip MPP (RKMPP)"),
                                ChoiceOption("videotoolbox", text = "Apple VideoToolbox"),
                                ChoiceOption("v4l2m2m", text = "Video4Linux2 (V4L2)"),
                            ),
                            R.string.config_hw_acceleration_summary,
                        ),
                        TextInputField(
                            "VaapiDevice",
                            R.string.config_vaapi_device,
                            R.string.config_vaapi_device_summary,
                            visibleWhen = hardwareIs("vaapi"),
                        ),
                        TextInputField(
                            "QsvDevice",
                            R.string.config_qsv_device,
                            R.string.config_qsv_device_summary,
                            visibleWhen = hardwareIs("qsv"),
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_hw_decoding,
                    listOf(
                        MultiChoiceField(
                            "HardwareDecodingCodecs",
                            R.string.config_hw_decoding_codecs,
                            listOf(
                                ChoiceOption("h264", text = "H.264", visibleWhen = ANY_HARDWARE),
                                ChoiceOption(
                                    "hevc",
                                    text = "HEVC",
                                    visibleWhen = TONE_MAPPING_HARDWARE,
                                ),
                                ChoiceOption(
                                    "mpeg1video",
                                    text = "MPEG-1",
                                    visibleWhen = hardwareIs("rkmpp"),
                                ),
                                ChoiceOption(
                                    "mpeg2video",
                                    text = "MPEG-2",
                                    visibleWhen = DEEP_COLOR_HARDWARE,
                                ),
                                ChoiceOption(
                                    "mpeg4",
                                    text = "MPEG-4",
                                    visibleWhen = hardwareIs("nvenc", "rkmpp"),
                                ),
                                ChoiceOption(
                                    "vc1",
                                    text = "VC-1",
                                    visibleWhen = hardwareIs("amf", "nvenc", "qsv", "vaapi"),
                                ),
                                ChoiceOption("vp8", text = "VP8", visibleWhen = VP8_HARDWARE),
                                ChoiceOption(
                                    "vp9",
                                    text = "VP9",
                                    visibleWhen = TONE_MAPPING_HARDWARE,
                                ),
                                ChoiceOption(
                                    "av1",
                                    text = "AV1",
                                    visibleWhen = TONE_MAPPING_HARDWARE,
                                ),
                            ),
                            visibleWhen = ANY_HARDWARE,
                            flags =
                                listOf(
                                    FlagChoice(
                                        "EnableDecodingColorDepth10Hevc",
                                        R.string.config_hevc_10bit,
                                        DEEP_COLOR_HARDWARE,
                                    ),
                                    FlagChoice(
                                        "EnableDecodingColorDepth10Vp9",
                                        R.string.config_vp9_10bit,
                                        DEEP_COLOR_HARDWARE,
                                    ),
                                    FlagChoice(
                                        "EnableDecodingColorDepth10HevcRext",
                                        R.string.config_hevc_rext_10bit,
                                        RANGE_EXTENSION_HARDWARE,
                                    ),
                                    FlagChoice(
                                        "EnableDecodingColorDepth12HevcRext",
                                        R.string.config_hevc_rext_12bit,
                                        RANGE_EXTENSION_HARDWARE,
                                    ),
                                ),
                        ),
                        SwitchField(
                            "EnableEnhancedNvdecDecoder",
                            R.string.config_enhanced_nvdec,
                            R.string.config_enhanced_nvdec_summary,
                            visibleWhen = hardwareIs("nvenc"),
                        ),
                        SwitchField(
                            "PreferSystemNativeHwDecoder",
                            R.string.config_prefer_native_decoder,
                            visibleWhen = hardwareIs("qsv"),
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_hw_encoding,
                    listOf(
                        SwitchField(
                            "EnableHardwareEncoding",
                            R.string.config_hw_encoding,
                            visibleWhen = ANY_HARDWARE,
                        ),
                        SwitchField(
                            "EnableIntelLowPowerH264HwEncoder",
                            R.string.config_low_power_h264,
                            visibleWhen = INTEL_HARDWARE,
                        ),
                        SwitchField(
                            "EnableIntelLowPowerHevcHwEncoder",
                            R.string.config_low_power_hevc,
                            R.string.config_low_power_summary,
                            visibleWhen = INTEL_HARDWARE,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_encoding_formats,
                    listOf(
                        SwitchField("AllowHevcEncoding", R.string.config_allow_hevc_encoding),
                        SwitchField("AllowAv1Encoding", R.string.config_allow_av1_encoding),
                    ),
                    note = R.string.config_encoding_formats_summary,
                ),
                ConfigSection(
                    R.string.config_section_tone_mapping,
                    listOf(
                        SwitchField(
                            "EnableVppTonemapping",
                            R.string.config_vpp_tonemapping,
                            R.string.config_vpp_tonemapping_summary,
                            visibleWhen = INTEL_HARDWARE,
                        ),
                        DecimalField(
                            "VppTonemappingBrightness",
                            R.string.config_vpp_brightness,
                            R.string.config_vpp_brightness_summary,
                            max = 100.0,
                            visibleWhen = INTEL_HARDWARE,
                        ),
                        DecimalField(
                            "VppTonemappingContrast",
                            R.string.config_vpp_contrast,
                            R.string.config_vpp_contrast_summary,
                            min = 1.0,
                            max = 2.0,
                            visibleWhen = INTEL_HARDWARE,
                        ),
                        SwitchField(
                            "EnableVideoToolboxTonemapping",
                            R.string.config_videotoolbox_tonemapping,
                            R.string.config_videotoolbox_tonemapping_summary,
                            visibleWhen = hardwareIs("videotoolbox"),
                        ),
                        SwitchField(
                            "EnableTonemapping",
                            R.string.config_tonemapping,
                            R.string.config_tonemapping_summary,
                            visibleWhen = TONE_MAPPING_HARDWARE,
                        ),
                        ChoiceField(
                            "TonemappingAlgorithm",
                            R.string.config_tonemapping_algorithm,
                            listOf(
                                ChoiceOption("none", R.string.config_none),
                                ChoiceOption("clip", text = "Clip"),
                                ChoiceOption("linear", text = "Linear"),
                                ChoiceOption("gamma", text = "Gamma"),
                                ChoiceOption("reinhard", text = "Reinhard"),
                                ChoiceOption("hable", text = "Hable"),
                                ChoiceOption("mobius", text = "Mobius"),
                                ChoiceOption("bt2390", text = "BT.2390"),
                            ),
                            R.string.config_tonemapping_algorithm_summary,
                            visibleWhen = TONE_MAPPING_OR_SOFTWARE,
                        ),
                        ChoiceField(
                            "TonemappingMode",
                            R.string.config_tonemapping_mode,
                            listOf(
                                ChoiceOption("auto", R.string.config_auto),
                                ChoiceOption("max", text = "MAX"),
                                ChoiceOption("rgb", text = "RGB"),
                                ChoiceOption("lum", text = "LUM"),
                                ChoiceOption("itp", text = "ITP"),
                            ),
                            R.string.config_tonemapping_mode_summary,
                            visibleWhen = TONE_MAPPING_HARDWARE,
                        ),
                        ChoiceField(
                            "TonemappingRange",
                            R.string.config_tonemapping_range,
                            listOf(
                                ChoiceOption("auto", R.string.config_auto),
                                ChoiceOption("tv", text = "TV"),
                                ChoiceOption("pc", text = "PC"),
                            ),
                            R.string.config_tonemapping_range_summary,
                            visibleWhen = TONE_MAPPING_OR_SOFTWARE,
                        ),
                        DecimalField(
                            "TonemappingDesat",
                            R.string.config_tonemapping_desat,
                            R.string.config_tonemapping_desat_summary,
                            visibleWhen = TONE_MAPPING_OR_SOFTWARE,
                        ),
                        DecimalField(
                            "TonemappingPeak",
                            R.string.config_tonemapping_peak,
                            R.string.config_tonemapping_peak_summary,
                            visibleWhen = TONE_MAPPING_OR_SOFTWARE,
                        ),
                        DecimalField(
                            "TonemappingParam",
                            R.string.config_tonemapping_param,
                            R.string.config_tonemapping_param_summary,
                            visibleWhen = TONE_MAPPING_OR_SOFTWARE,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_quality,
                    listOf(
                        ChoiceField(
                            "EncodingThreadCount",
                            R.string.config_thread_count,
                            THREAD_OPTIONS,
                            R.string.config_thread_count_summary,
                            numeric = true,
                        ),
                        ChoiceField(
                            "EncoderPreset",
                            R.string.config_encoder_preset,
                            listOf(ChoiceOption("auto", R.string.config_auto)) +
                                listOf(
                                        "veryslow",
                                        "slower",
                                        "slow",
                                        "medium",
                                        "fast",
                                        "faster",
                                        "veryfast",
                                        "superfast",
                                        "ultrafast",
                                    )
                                    .map { ChoiceOption(it) },
                            R.string.config_encoder_preset_summary,
                        ),
                        NumberField("H265Crf", R.string.config_h265_crf, max = MAX_CRF),
                        NumberField(
                            "H264Crf",
                            R.string.config_h264_crf,
                            R.string.config_crf_summary,
                            max = MAX_CRF,
                        ),
                        ChoiceField(
                            "DeinterlaceMethod",
                            R.string.config_deinterlace_method,
                            listOf(
                                ChoiceOption("yadif", R.string.config_deinterlace_yadif),
                                ChoiceOption("bwdif", R.string.config_deinterlace_bwdif),
                            ),
                            R.string.config_deinterlace_method_summary,
                        ),
                        SwitchField(
                            "DeinterlaceDoubleRate",
                            R.string.config_deinterlace_double_rate,
                            R.string.config_deinterlace_double_rate_summary,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_throttling,
                    listOf(
                        SwitchField(
                            "EnableThrottling",
                            R.string.config_throttling,
                            R.string.config_throttling_summary,
                        ),
                        NumberField(
                            "ThrottleDelaySeconds",
                            R.string.config_throttle_delay,
                            R.string.config_throttle_delay_summary,
                            min = MIN_SEGMENT_SECONDS,
                            max = MAX_SEGMENT_SECONDS,
                        ),
                        SwitchField(
                            "EnableSegmentDeletion",
                            R.string.config_segment_deletion,
                            R.string.config_segment_deletion_summary,
                        ),
                        NumberField(
                            "SegmentKeepSeconds",
                            R.string.config_segment_keep,
                            R.string.config_segment_keep_summary,
                            min = MIN_SEGMENT_SECONDS,
                            max = MAX_SEGMENT_SECONDS,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_audio,
                    listOf(
                        SwitchField(
                            "EnableAudioVbr",
                            R.string.config_audio_vbr,
                            R.string.config_audio_vbr_summary,
                        ),
                        DecimalField(
                            "DownMixAudioBoost",
                            R.string.config_downmix_boost,
                            R.string.config_downmix_boost_summary,
                            min = 0.5,
                            max = 3.0,
                        ),
                        ChoiceField(
                            "DownMixStereoAlgorithm",
                            R.string.config_downmix_algorithm,
                            listOf(
                                ChoiceOption("None", R.string.config_none),
                                ChoiceOption("Dave750"),
                                ChoiceOption("NightmodeDialogue"),
                                ChoiceOption("Rfc7845", text = "RFC 7845"),
                                ChoiceOption("Ac4", text = "AC-4"),
                            ),
                            R.string.config_downmix_algorithm_summary,
                        ),
                        ChoiceField(
                            "HlsAudioSeekStrategy",
                            R.string.config_hls_audio_seek,
                            listOf(
                                ChoiceOption("TrimCopiedAudio"),
                                ChoiceOption("TranscodeAudio"),
                            ),
                            R.string.config_hls_audio_seek_summary,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_subtitles,
                    listOf(
                        SwitchField(
                            "EnableSubtitleExtraction",
                            R.string.config_subtitle_extraction,
                            R.string.config_subtitle_extraction_summary,
                        ),
                        SwitchField(
                            "EnableFallbackFont",
                            R.string.config_fallback_font,
                            R.string.config_fallback_font_summary,
                        ),
                        TextInputField(
                            "FallbackFontPath",
                            R.string.config_fallback_font_path,
                            R.string.config_fallback_font_path_summary,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_ffmpeg,
                    listOf(
                        ReadOnlyField(
                            "EncoderAppPathDisplay",
                            R.string.config_ffmpeg_path,
                            R.string.config_ffmpeg_path_summary,
                        ),
                        TextInputField(
                            "TranscodingTempPath",
                            R.string.config_transcode_path,
                            R.string.config_transcode_path_summary,
                        ),
                        NumberField(
                            "MaxMuxingQueueSize",
                            R.string.config_max_muxing_queue,
                            R.string.config_max_muxing_queue_summary,
                        ),
                    ),
                ),
            ),
    )
