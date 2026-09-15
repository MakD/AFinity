package com.makd.afinity.data.models.player

data class MusicQuality(val maxBitrate: Int) {

    val isOriginal: Boolean
        get() = maxBitrate == ORIGINAL_BITRATE

    val streamingBitrate: Int
        get() =
            if (isOriginal) maxBitrate
            else (maxBitrate * (1f + CONTAINER_OVERHEAD_HEADROOM)).toInt()

    companion object {
        const val ORIGINAL_BITRATE = -1

        const val CONTAINER_OVERHEAD_HEADROOM = 0.05f

        val ORIGINAL = MusicQuality(ORIGINAL_BITRATE)

        const val AUDIO_CODECS = "opus,aac,mp3"
        const val CONTAINERS = "opus,ogg,aac,m4a,m4b,mp4,mp3,flac,alac,wav"
        const val TRANSCODING_CONTAINER = "ogg"
        const val TRANSCODING_PROTOCOL = "http"

        private val LADDER =
            listOf(
                MusicQuality(320_000),
                MusicQuality(256_000),
                MusicQuality(192_000),
                MusicQuality(128_000),
                MusicQuality(96_000),
                MusicQuality(64_000),
            )

        val CELLULAR_DEFAULT_BITRATE = 128_000

        fun options(): List<MusicQuality> = listOf(ORIGINAL) + LADDER

        fun fromBitrate(bitrate: Int?): MusicQuality =
            when (bitrate) {
                null,
                ORIGINAL_BITRATE -> ORIGINAL
                else -> LADDER.firstOrNull { it.maxBitrate == bitrate } ?: MusicQuality(bitrate)
            }
    }
}
