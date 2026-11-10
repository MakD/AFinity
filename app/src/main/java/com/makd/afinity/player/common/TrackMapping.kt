package com.makd.afinity.player.common

import com.makd.afinity.data.models.media.AfinityMediaStream

object TrackMapping {

    const val EXTERNAL_SUBTITLE_ID_BASE = 128

    const val DEVICE_SUBTITLE_INDEX_BASE = 4096

    fun deviceSubtitleIndex(ordinal: Int): Int = DEVICE_SUBTITLE_INDEX_BASE + ordinal

    fun isDeviceSubtitleIndex(index: Int?): Boolean =
        index != null && index >= DEVICE_SUBTITLE_INDEX_BASE

    fun serverSubtitleIndex(index: Int?): Int? = index?.takeUnless { isDeviceSubtitleIndex(it) }

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

    fun audioSortKey(formatId: String?): String? {
        val id = formatId?.let { withoutSourcePrefix(it) } ?: return null
        return id.toIntOrNull()?.let { "%05d".format(it) } ?: id
    }

    fun sideLoadedId(streamIndex: Int): String =
        (EXTERNAL_SUBTITLE_ID_BASE + streamIndex).toString()

    fun streamIndexFromSideLoadedId(formatId: String?): Int? {
        if (formatId == null) return null
        val separator = formatId.indexOf(':')
        val id =
            if (separator < 0) {
                formatId
            } else {
                val source = formatId.substring(0, separator).toIntOrNull() ?: return null
                if (source < 1) return null
                formatId.substring(separator + 1)
            }
        return id.toIntOrNull()
            ?.takeIf { it >= EXTERNAL_SUBTITLE_ID_BASE }
            ?.minus(EXTERNAL_SUBTITLE_ID_BASE)
    }

    private fun withoutSourcePrefix(formatId: String): String {
        val separator = formatId.indexOf(':')
        if (separator < 0) return formatId
        val source = formatId.substring(0, separator).toIntOrNull() ?: return formatId
        return if (source >= 0) formatId.substring(separator + 1) else formatId
    }

    fun streamIndexFromExternalUri(uri: String?, sideLoadedUris: Map<Int, String>): Int? {
        if (uri == null) return null
        return sideLoadedUris.entries.firstOrNull { it.value == uri }?.key
    }
}
