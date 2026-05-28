package com.makd.afinity.data.models.livetv

data class LiveTvPlaybackInfo(
    val streamUrl: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val liveStreamId: String?,
    val playMethod: String,
    val container: String?,
) {
    val isHls: Boolean
        get() =
            streamUrl.contains(".m3u8", ignoreCase = true) ||
                container.equals("hls", ignoreCase = true)
}
