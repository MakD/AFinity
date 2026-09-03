package com.makd.afinity.data.models.player

data class VideoQuality(val maxBitrate: Int, val maxWidth: Int?) {

    val isAuto: Boolean
        get() = maxBitrate == AUTO_BITRATE

    val isOriginal: Boolean
        get() = maxBitrate == ORIGINAL_BITRATE

    val maxHeight: Int?
        get() =
            when (maxWidth) {
                3840 -> 2160
                2560 -> 1440
                1920 -> 1080
                1280 -> 720
                854 -> 480
                640 -> 360
                else -> null
            }

    companion object {
        const val AUTO_BITRATE = 0
        const val ORIGINAL_BITRATE = -1

        val AUTO = VideoQuality(AUTO_BITRATE, null)
        val ORIGINAL = VideoQuality(ORIGINAL_BITRATE, null)

        private val LADDER =
            listOf(
                VideoQuality(120_000_000, 3840),
                VideoQuality(80_000_000, 3840),
                VideoQuality(60_000_000, 3840),
                VideoQuality(40_000_000, 3840),
                VideoQuality(20_000_000, 3840),
                VideoQuality(15_000_000, 2560),
                VideoQuality(10_000_000, 2560),
                VideoQuality(8_000_000, 1920),
                VideoQuality(6_000_000, 1920),
                VideoQuality(4_000_000, 1920),
                VideoQuality(3_000_000, 1920),
                VideoQuality(2_000_000, 1280),
                VideoQuality(1_500_000, 1280),
                VideoQuality(720_000, 854),
                VideoQuality(420_000, 640),
            )

        fun settingsLadder(): List<VideoQuality> = listOf(AUTO, ORIGINAL) + LADDER

        fun fromBitrate(bitrate: Int?): VideoQuality =
            when (bitrate) {
                null, ORIGINAL_BITRATE -> ORIGINAL
                AUTO_BITRATE -> AUTO
                else -> LADDER.firstOrNull { it.maxBitrate == bitrate } ?: VideoQuality(bitrate, null)
            }

        fun optionsFor(sourceBitrate: Int?, sourceWidth: Int?): List<VideoQuality> {
            val knownBitrate = sourceBitrate?.takeIf { it > 0 }
            val rungs =
                LADDER.filter { rung ->
                    val width = rung.maxWidth ?: Int.MAX_VALUE
                    val neverUpscale = sourceWidth == null || width <= sourceWidth
                    val downscales = sourceWidth != null && width < sourceWidth
                    val lowersBitrate = knownBitrate == null || rung.maxBitrate < knownBitrate
                    neverUpscale && (downscales || lowersBitrate)
                }
            return buildList {
                add(AUTO)
                add(ORIGINAL)
                addAll(rungs)
            }
        }
    }
}
