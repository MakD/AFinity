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
