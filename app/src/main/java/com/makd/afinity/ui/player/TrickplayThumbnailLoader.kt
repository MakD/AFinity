package com.makd.afinity.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.SystemClock
import android.util.LruCache
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.repository.media.MediaRepository
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class TrickplayThumbnailLoader(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val onThumbnail: (Bitmap) -> Unit,
) {
    private class Session(
        val itemId: UUID,
        val mediaSourceId: String?,
        val info: AfinityTrickplayInfo,
        val localDir: File?,
    ) {
        val thumbnailsPerTile = info.tileWidth * info.tileHeight
        val tileCount = (info.thumbnailCount + thumbnailsPerTile - 1) / thumbnailsPerTile

        fun thumbnailIndex(positionMs: Long): Int =
            (positionMs.coerceAtLeast(0L) / info.interval)
                .coerceAtMost((info.thumbnailCount - 1).toLong())
                .toInt()

        fun tileOf(thumbnailIndex: Int): Int = thumbnailIndex / thumbnailsPerTile
    }

    private val tiles =
        object : LruCache<Int, ByteArray>(MAX_TILE_BYTES) {
            override fun sizeOf(key: Int, value: ByteArray): Int = value.size
        }

    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val decoders =
        object : LinkedHashMap<ByteArray, BitmapRegionDecoder>(4, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<ByteArray, BitmapRegionDecoder>?
            ): Boolean {
                if (size <= MAX_DECODERS) return false
                eldest?.value?.recycle()
                return true
            }
        }

    private var session: Session? = null
    private val inFlight = HashMap<Int, Job>()
    private val failedAt = HashMap<Int, Long>()
    private val urgent = ArrayDeque<Int>()
    private var fillOrder: List<Int> = emptyList()
    private var fillCursor = 0
    private var fillJob: Job? = null
    private var decodeJob: Job? = null
    private var pendingPosition: Long? = null
    private var shownIndex = -1
    private var lastPlayheadTile = -1
    private var fetchedBytes = 0L
    private var fetchedCount = 0

    fun start(
        itemId: UUID,
        mediaSourceId: String?,
        info: AfinityTrickplayInfo,
        localDir: File?,
        playheadMs: () -> Long,
    ) {
        reset()
        if (
            info.thumbnailCount <= 0 ||
                info.tileWidth <= 0 ||
                info.tileHeight <= 0 ||
                info.interval <= 0 ||
                info.width <= 0 ||
                info.height <= 0
        ) {
            return
        }
        val s = Session(itemId, mediaSourceId, info, localDir)
        session = s
        onPlayhead(playheadMs())
        fillJob = scope.launch {
            delay(FILL_DELAY)
            if (session !== s) return@launch
            val playheadTile = s.tileOf(s.thumbnailIndex(playheadMs()))
            fillOrder = (0 until s.tileCount).sortedBy { abs(it - playheadTile) }
            fillCursor = 0
            drain()
        }
    }

    fun reset() {
        session = null
        fillJob?.cancel()
        fillJob = null
        decodeJob?.cancel()
        decodeJob = null
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        failedAt.clear()
        urgent.clear()
        fillOrder = emptyList()
        fillCursor = 0
        tiles.evictAll()
        pendingPosition = null
        shownIndex = -1
        lastPlayheadTile = -1
        fetchedBytes = 0L
        fetchedCount = 0
        scope.launch(decodeDispatcher) {
            decoders.values.forEach { it.recycle() }
            decoders.clear()
        }
    }

    fun onPlayhead(positionMs: Long) {
        val s = session ?: return
        val tile = s.tileOf(s.thumbnailIndex(positionMs))
        if (tile == lastPlayheadTile) return
        lastPlayheadTile = tile
        request(tile, front = false)
    }

    fun onDragStart(positionMs: Long) {
        val s = session ?: return
        val tile = s.tileOf(s.thumbnailIndex(positionMs))
        request(tile + 1, front = true)
        request(tile - 1, front = true)
        request(tile, front = true)
        show(positionMs)
    }

    fun show(positionMs: Long) {
        val s = session ?: return
        pendingPosition = positionMs
        val tile = s.tileOf(s.thumbnailIndex(positionMs))
        if (tiles.get(tile) == null) request(tile, front = true)
        pump()
    }

    fun hide() {
        pendingPosition = null
        shownIndex = -1
    }

    private fun pump() {
        val s = session ?: return
        val position = pendingPosition ?: return
        if (decodeJob != null) return
        val index = s.thumbnailIndex(position)
        if (index == shownIndex) return
        val bytes = tiles.get(s.tileOf(index)) ?: return
        decodeJob =
            scope.launch(start = CoroutineStart.LAZY) {
                val bitmap = withContext(decodeDispatcher) { decode(bytes, index, s) }
                if (session !== s) return@launch
                decodeJob = null
                if (pendingPosition != null) {
                    shownIndex = index
                    if (bitmap != null) onThumbnail(bitmap)
                }
                pump()
            }
        decodeJob?.start()
    }

    private fun decode(bytes: ByteArray, index: Int, s: Session): Bitmap? =
        try {
            val decoder =
                decoders[bytes]?.takeUnless { it.isRecycled }
                    ?: BitmapRegionDecoder.newInstance(bytes, 0, bytes.size).also {
                        decoders[bytes] = it
                    }
            val info = s.info
            val inTile = index % s.thumbnailsPerTile
            val left = (inTile % info.tileWidth) * info.width
            val top = (inTile / info.tileWidth) * info.height
            val rect =
                Rect(
                    left,
                    top,
                    minOf(left + info.width, decoder.width),
                    minOf(top + info.height, decoder.height),
                )
            if (rect.isEmpty) null else decoder.decodeRegion(rect, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode trickplay thumbnail $index")
            null
        }

    private fun request(tile: Int, front: Boolean) {
        val s = session ?: return
        if (tile !in 0 until s.tileCount) return
        if (inFlight.containsKey(tile) || tiles.get(tile) != null) return
        val failed = failedAt[tile]
        if (failed != null && SystemClock.elapsedRealtime() - failed < FAILURE_COOLDOWN_MS) return
        urgent.remove(tile)
        if (front) urgent.addFirst(tile) else urgent.addLast(tile)
        drain()
    }

    private fun drain() {
        val s = session ?: return
        while (inFlight.size < MAX_PARALLEL_FETCHES) {
            val tile = urgent.removeFirstOrNull() ?: nextFillTile() ?: return
            if (inFlight.containsKey(tile) || tiles.get(tile) != null) continue
            val job = scope.launch(start = CoroutineStart.LAZY) { fetch(s, tile) }
            inFlight[tile] = job
            job.start()
        }
    }

    private fun nextFillTile(): Int? {
        while (fillCursor < fillOrder.size) {
            if (fetchedCount > 0) {
                val averageTile = fetchedBytes / fetchedCount
                val committed = tiles.size() + averageTile * (inFlight.size + 1)
                if (committed > MAX_TILE_BYTES) {
                    fillCursor = fillOrder.size
                    return null
                }
            }
            val tile = fillOrder[fillCursor++]
            if (!inFlight.containsKey(tile) && !failedAt.containsKey(tile)) return tile
        }
        return null
    }

    private suspend fun fetch(s: Session, tile: Int) {
        val bytes =
            try {
                mediaRepository.getTrickplayTile(
                    itemId = s.itemId,
                    mediaSourceId = s.mediaSourceId,
                    width = s.info.width,
                    index = tile,
                    localDir = s.localDir,
                )
            } finally {
                if (session === s) inFlight.remove(tile)
            }
        if (session !== s) return
        if (bytes != null) {
            failedAt.remove(tile)
            tiles.put(tile, bytes)
            fetchedBytes += bytes.size
            fetchedCount++
            pump()
        } else {
            failedAt[tile] = SystemClock.elapsedRealtime()
        }
        drain()
    }

    private companion object {
        const val MAX_TILE_BYTES = 24 * 1024 * 1024
        const val MAX_PARALLEL_FETCHES = 2
        const val MAX_DECODERS = 2
        const val FAILURE_COOLDOWN_MS = 5_000L
        val FILL_DELAY = 10.seconds
    }
}
