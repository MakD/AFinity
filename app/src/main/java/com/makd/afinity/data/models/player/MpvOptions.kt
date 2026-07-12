package com.makd.afinity.data.models.player

enum class MpvHwDec(val value: String) {
    NO("no"),
    MEDIACODEC("mediacodec"),
    MEDIACODEC_COPY("mediacodec-copy");

    companion object {
        val default = MEDIACODEC_COPY

        fun fromValue(value: String): MpvHwDec {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            NO -> "Disabled"
            MEDIACODEC -> "mediacodec"
            MEDIACODEC_COPY -> "mediacodec-copy"
        }
    }
}

enum class MpvVideoOutput(val value: String) {
    GPU("gpu"),
    GPU_NEXT("gpu-next");

    companion object {
        val default = GPU_NEXT

        fun fromValue(value: String): MpvVideoOutput {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            GPU -> "gpu"
            GPU_NEXT -> "gpu-next"
        }
    }
}

enum class MpvGpuApi(val value: String) {
    OPENGL("opengl"),
    VULKAN("vulkan");

    companion object {
        val default = OPENGL

        fun fromValue(value: String): MpvGpuApi {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            OPENGL -> "OpenGL (default)"
            VULKAN -> "Vulkan (experimental)"
        }
    }
}

enum class MpvHdrOutput(val value: String) {
    AUTO("auto"),
    TONE_MAP("tone-map");

    companion object {
        val default = AUTO

        fun fromValue(value: String): MpvHdrOutput {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            AUTO -> "Auto (HDR passthrough)"
            TONE_MAP -> "Always tone-map to SDR"
        }
    }
}

enum class MpvToneMapping(val value: String) {
    AUTO("auto"),
    BT2446A("bt.2446a"),
    SPLINE("spline"),
    HABLE("hable"),
    MOBIUS("mobius"),
    CLIP("clip");

    companion object {
        val default = AUTO

        fun fromValue(value: String): MpvToneMapping {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            AUTO -> "Auto"
            BT2446A -> "BT.2446a"
            SPLINE -> "Spline"
            HABLE -> "Hable"
            MOBIUS -> "Mobius"
            CLIP -> "Clip"
        }
    }
}

enum class MpvAudioOutput(val value: String) {
    AUDIOTRACK("audiotrack"),
    OPENSLES("opensles"),
    AAUDIO("aaudio");

    companion object {
        val default = AUDIOTRACK

        fun fromValue(value: String): MpvAudioOutput {
            return entries.find { it.value == value } ?: default
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            AUDIOTRACK -> "AudioTrack"
            OPENSLES -> "OpenSL ES"
            AAUDIO -> "AAudio"
        }
    }
}
