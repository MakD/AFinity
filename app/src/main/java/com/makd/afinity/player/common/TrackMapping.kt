package com.makd.afinity.player.common

import com.makd.afinity.data.models.media.AfinityMediaStream

object TrackMapping {

    const val EXTERNAL_SUBTITLE_ID_BASE = 128

    fun embeddedOrdinal(streams: List<AfinityMediaStream>, streamIndex: Int): Int? {
        val stream = streams.firstOrNull { it.index == streamIndex } ?: return null
        if (stream.isExternal) return null
        return streams
            .filter { !it.isExternal }
            .indexOfFirst { it.index == streamIndex }
            .takeIf { it >= 0 }
    }

    fun streamIndexAtEmbeddedOrdinal(
        streams: List<AfinityMediaStream>,
        embeddedOrdinal: Int,
    ): Int? = streams.filter { !it.isExternal }.getOrNull(embeddedOrdinal)?.index

    fun audioSortKey(formatId: String?): String? =
        formatId?.toIntOrNull()?.let { "%05d".format(it) } ?: formatId

    fun sideLoadedId(streamIndex: Int): String =
        (EXTERNAL_SUBTITLE_ID_BASE + streamIndex).toString()

    fun streamIndexFromSideLoadedId(formatId: String?): Int? =
        formatId
            ?.toIntOrNull()
            ?.takeIf { it >= EXTERNAL_SUBTITLE_ID_BASE }
            ?.minus(EXTERNAL_SUBTITLE_ID_BASE)

    fun streamIndexFromExternalUri(uri: String?, sideLoadedUris: Map<Int, String>): Int? {
        if (uri == null) return null
        return sideLoadedUris.entries.firstOrNull { it.value == uri }?.key
    }
}
