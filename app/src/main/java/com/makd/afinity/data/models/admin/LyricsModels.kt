package com.makd.afinity.data.models.admin

data class LyricsLineInfo(val text: String, val startMs: Long?)

data class LyricsContent(val lines: List<LyricsLineInfo>) {
    val synced: Boolean
        get() = lines.any { it.startMs != null }

    val lineCount: Int
        get() = lines.count { it.text.isNotBlank() }
}

data class TrackLyrics(val content: LyricsContent, val sidecarFileName: String?)

data class LyricsTarget(val trackName: String, val artist: String?, val lyrics: TrackLyrics?)

data class RemoteLyrics(
    val id: String,
    val providerName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val lengthMs: Long?,
    val synced: Boolean,
    val content: LyricsContent,
)

class LyricsNotSavedException : Exception("The server did not serve the saved lyrics")
