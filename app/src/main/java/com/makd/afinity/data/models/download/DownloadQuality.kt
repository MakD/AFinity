package com.makd.afinity.data.models.download

data class DownloadQuality(
    val bitrate: Int,
    val burnSubtitleIndex: Int? = null,
    val audioStreamIndex: Int? = null,
) {

    val isOriginal: Boolean
        get() = bitrate <= 0

    companion object {
        const val ORIGINAL_BITRATE = -1

        val ORIGINAL = DownloadQuality(ORIGINAL_BITRATE)
    }
}
