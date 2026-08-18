package com.makd.afinity.data.models.player

import androidx.annotation.StringRes
import com.makd.afinity.R

enum class MpvHwDec(val value: String, @param:StringRes val labelRes: Int) {
    NO("no", R.string.pref_mpv_hwdec_disabled),
    MEDIACODEC("mediacodec", R.string.pref_mpv_hwdec_mediacodec),
    MEDIACODEC_COPY("mediacodec-copy", R.string.pref_mpv_hwdec_mediacodec_copy);

    companion object {
        val default = MEDIACODEC_COPY

        fun fromValue(value: String): MpvHwDec {
            return entries.find { it.value == value } ?: default
        }
    }
}

enum class MpvVideoOutput(val value: String, @param:StringRes val labelRes: Int) {
    GPU("gpu", R.string.pref_mpv_vo_gpu),
    GPU_NEXT("gpu-next", R.string.pref_mpv_vo_gpu_next);

    companion object {
        val default = GPU_NEXT

        fun fromValue(value: String): MpvVideoOutput {
            return entries.find { it.value == value } ?: default
        }
    }
}

enum class MpvGpuApi(val value: String, @param:StringRes val labelRes: Int) {
    OPENGL("opengl", R.string.pref_mpv_gpu_api_opengl),
    VULKAN("vulkan", R.string.pref_mpv_gpu_api_vulkan);

    companion object {
        val default = OPENGL

        fun fromValue(value: String): MpvGpuApi {
            return entries.find { it.value == value } ?: default
        }
    }
}

enum class MpvHdrOutput(val value: String, @param:StringRes val labelRes: Int) {
    AUTO("auto", R.string.pref_mpv_hdr_auto),
    TONE_MAP("tone-map", R.string.pref_mpv_hdr_tone_map);

    companion object {
        val default = AUTO

        fun fromValue(value: String): MpvHdrOutput {
            return entries.find { it.value == value } ?: default
        }
    }
}

enum class MpvToneMapping(val value: String, @param:StringRes val labelRes: Int) {
    AUTO("auto", R.string.pref_mpv_tone_auto),
    BT2446A("bt.2446a", R.string.pref_mpv_tone_bt2446a),
    SPLINE("spline", R.string.pref_mpv_tone_spline),
    HABLE("hable", R.string.pref_mpv_tone_hable),
    MOBIUS("mobius", R.string.pref_mpv_tone_mobius),
    CLIP("clip", R.string.pref_mpv_tone_clip);

    companion object {
        val default = AUTO

        fun fromValue(value: String): MpvToneMapping {
            return entries.find { it.value == value } ?: default
        }
    }
}

enum class MpvAudioOutput(val value: String, @param:StringRes val labelRes: Int) {
    AUDIOTRACK("audiotrack", R.string.pref_mpv_ao_audiotrack),
    OPENSLES("opensles", R.string.pref_mpv_ao_opensles),
    AAUDIO("aaudio", R.string.pref_mpv_ao_aaudio);

    companion object {
        val default = AUDIOTRACK

        fun fromValue(value: String): MpvAudioOutput {
            return entries.find { it.value == value } ?: default
        }
    }
}