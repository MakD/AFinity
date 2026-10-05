package com.makd.afinity.util

import java.io.File
import java.io.RandomAccessFile

object Mp4FragmentProbe {

    private const val MOOV = 0x6D6F6F76
    private const val MOOF = 0x6D6F6F66
    private const val TRAK = 0x7472616B
    private const val TKHD = 0x746B6864
    private const val MDIA = 0x6D646961
    private const val MDHD = 0x6D646864
    private const val TRAF = 0x74726166
    private const val TFHD = 0x74666864
    private const val TFDT = 0x74666474

    private const val UINT_MASK = 0xFFFFFFFFL

    fun lastFragmentTimeMs(file: File): Long? =
        try {
            RandomAccessFile(file, "r").use { input -> read(input) }
        } catch (_: Exception) {
            null
        }

    private fun read(input: RandomAccessFile): Long? {
        val timescales = HashMap<Int, Long>()
        var lastMoofStart = -1L
        var lastMoofEnd = -1L

        forEachBox(input, 0L, input.length()) { type, bodyStart, bodyEnd ->
            when (type) {
                MOOV -> readTimescales(input, bodyStart, bodyEnd, timescales)
                MOOF -> {
                    lastMoofStart = bodyStart
                    lastMoofEnd = bodyEnd
                }
            }
        }

        if (lastMoofStart < 0L || timescales.isEmpty()) return null
        return readFragmentTimeMs(input, lastMoofStart, lastMoofEnd, timescales)
    }

    private fun readTimescales(
        input: RandomAccessFile,
        start: Long,
        end: Long,
        timescales: MutableMap<Int, Long>,
    ) {
        forEachBox(input, start, end) { type, trakStart, trakEnd ->
            if (type != TRAK) return@forEachBox
            var trackId = -1
            var timescale = 0L
            forEachBox(input, trakStart, trakEnd) { childType, childStart, childEnd ->
                when (childType) {
                    TKHD -> {
                        input.seek(childStart)
                        val version = input.readUnsignedByte()
                        input.skipBytes(3 + if (version == 1) 16 else 8)
                        trackId = input.readInt()
                    }

                    MDIA ->
                        forEachBox(input, childStart, childEnd) { mediaType, mediaStart, _ ->
                            if (mediaType == MDHD) {
                                input.seek(mediaStart)
                                val version = input.readUnsignedByte()
                                input.skipBytes(3 + if (version == 1) 16 else 8)
                                timescale = input.readInt().toLong() and UINT_MASK
                            }
                        }
                }
            }
            if (trackId >= 0 && timescale > 0L) timescales[trackId] = timescale
        }
    }

    private fun readFragmentTimeMs(
        input: RandomAccessFile,
        start: Long,
        end: Long,
        timescales: Map<Int, Long>,
    ): Long? {
        var latestMs: Long? = null
        forEachBox(input, start, end) { type, trafStart, trafEnd ->
            if (type != TRAF) return@forEachBox
            var trackId = -1
            var decodeTime = -1L
            forEachBox(input, trafStart, trafEnd) { childType, childStart, _ ->
                when (childType) {
                    TFHD -> {
                        input.seek(childStart + 4)
                        trackId = input.readInt()
                    }

                    TFDT -> {
                        input.seek(childStart)
                        val version = input.readUnsignedByte()
                        input.skipBytes(3)
                        decodeTime =
                            if (version == 1) input.readLong()
                            else input.readInt().toLong() and UINT_MASK
                    }
                }
            }
            val timescale = timescales[trackId]
            if (timescale != null && decodeTime >= 0L) {
                val timeMs = decodeTime * 1000L / timescale
                val current = latestMs
                if (current == null || timeMs > current) latestMs = timeMs
            }
        }
        return latestMs
    }

    private inline fun forEachBox(
        input: RandomAccessFile,
        start: Long,
        end: Long,
        block: (type: Int, bodyStart: Long, bodyEnd: Long) -> Unit,
    ) {
        var position = start
        while (position + 8 <= end) {
            input.seek(position)
            var size = input.readInt().toLong() and UINT_MASK
            val type = input.readInt()
            var headerSize = 8L
            if (size == 1L) {
                size = input.readLong()
                headerSize = 16L
            } else if (size == 0L) {
                size = end - position
            }
            if (size < headerSize || position + size > end) return
            block(type, position + headerSize, position + size)
            position += size
        }
    }
}
