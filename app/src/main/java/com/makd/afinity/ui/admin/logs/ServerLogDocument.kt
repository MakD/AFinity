package com.makd.afinity.ui.admin.logs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.makd.afinity.data.models.admin.ServerLogTail
import com.makd.afinity.ui.settings.logs.LogTagCount
import com.makd.afinity.util.logging.LabelPart
import com.makd.afinity.util.logging.LogLevel
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.BitSet

private const val VARIABLE_MARK = '\u0001'
private const val PREFIX_TOKENS = 3
private const val MERGE_MIN_SHAPES = 5
private const val CHECK_INTERVAL = 1_000
private const val ENTRY_CACHE_SIZE = 256
private const val READ_BUFFER_BYTES = 64 * 1024
private const val MAX_ENTRY_BYTES = 256 * 1024
private const val MAX_LISTED_TIMES = 3
private const val INITIAL_CAPACITY = 4_096
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val NEWLINE: Byte = 10

private val headerPattern =
    Regex(
        "^\\[\\d{4}-\\d{2}-\\d{2} (\\d{2}):(\\d{2}):(\\d{2})\\.\\d{3} [+-]\\d{2}:\\d{2}\\] " +
            "\\[(\\w{3})\\] \\[\\d+\\] ([^:]+): (.*)$"
    )

private val variablePattern =
    Regex(
        "https?://\\S+" +
            "|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" +
            "|\\b[0-9a-fA-F]{8,}\\b" +
            "|\"[^\"]*\"" +
            "|\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b" +
            "|\\b\\d+(?:\\.\\d+)?(?:ms|s|kb|mb|gb|%)?\\b"
    )

private val whitespacePattern = Regex("\\s+")

data class ServerLogEntry(val message: String, val trace: String?)

@Immutable
data class ServerLogRow(
    val key: String,
    val groupId: Int,
    val entryIndex: Int,
    val tag: String,
    val level: LogLevel,
    val time: String,
    val count: Int,
    val label: List<LabelPart>,
    val truncated: Boolean,
    val times: String,
)

@Immutable
class ServerLogView
internal constructor(
    val document: ServerLogDocument,
    val matchCount: Int,
    val groupCount: Int,
    private val groupRows: List<ServerLogRow>?,
    private val entryIndices: IntArray,
) {
    val size: Int
        get() = groupRows?.size ?: entryIndices.size

    val grouped: Boolean
        get() = groupRows != null

    fun keyAt(index: Int): String = groupRows?.get(index)?.key ?: entryKey(entryIndices[index])

    fun rowAt(index: Int): ServerLogRow =
        groupRows?.get(index) ?: document.entryRow(entryIndices[index])
}

private class ServerLogGroup(
    val tagId: Int,
    val level: LogLevel,
    val label: List<LabelPart>,
    val truncated: Boolean,
)

@Stable
class ServerLogDocument
private constructor(
    private val file: File,
    private val starts: LongArray,
    private val seconds: IntArray,
    private val levels: ByteArray,
    private val tagIds: IntArray,
    private val groupIds: IntArray,
    private val tags: List<String>,
    private val groups: List<ServerLogGroup>,
) : Closeable {

    private val reader = RandomAccessFile(file, "r")

    private val cache =
        object : LinkedHashMap<Int, ServerLogEntry>(ENTRY_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, ServerLogEntry>?
            ): Boolean = size > ENTRY_CACHE_SIZE
        }

    val entryCount: Int
        get() = seconds.size

    val warningCount: Int = levels.count { it.toInt() == LogLevel.WARN.ordinal }

    val errorCount: Int = levels.count { it.toInt() == LogLevel.ERROR.ordinal }

    val tagCounts: List<LogTagCount> = countTags()

    fun entry(index: Int): ServerLogEntry =
        synchronized(reader) {
            cache[index]
                ?: parseEntry(readText(index, index, MAX_ENTRY_BYTES)).also { cache[index] = it }
        }

    fun rawText(fromIndex: Int, toIndex: Int): String =
        synchronized(reader) { readText(fromIndex.coerceAtLeast(0), toIndex, Int.MAX_VALUE) }
            .trimEnd()

    fun search(query: String, checkActive: () -> Unit): BitSet {
        val matches = BitSet(entryCount)
        if (entryCount == 0) return matches
        file.inputStream().buffered(READ_BUFFER_BYTES).use { input ->
            skipFully(input, starts[0])
            var buffer = ByteArray(INITIAL_CAPACITY)
            for (index in 0 until entryCount) {
                if (index % CHECK_INTERVAL == 0) checkActive()
                val length = (starts[index + 1] - starts[index]).toInt()
                if (buffer.size < length) buffer = ByteArray(length)
                val read = readFully(input, buffer, length)
                val text = String(buffer, 0, read, Charsets.UTF_8)
                if (text.contains(query, ignoreCase = true)) matches.set(index)
                if (read < length) break
            }
        }
        return matches
    }

    fun view(
        level: LogLevel?,
        selectedTags: Set<String>,
        matches: BitSet?,
        grouped: Boolean,
        groupId: Int? = null,
    ): ServerLogView {
        val levelOrdinal = level?.ordinal ?: -1
        val allowedTags =
            if (selectedTags.isEmpty()) null
            else BooleanArray(tags.size) { tags[it] in selectedTags }
        val accepted = IntList()
        for (index in 0 until entryCount) {
            if (levelOrdinal >= 0 && levels[index].toInt() != levelOrdinal) continue
            if (allowedTags != null && !allowedTags[tagIds[index]]) continue
            if (matches != null && !matches.get(index)) continue
            if (groupId != null && groupIds[index] != groupId) continue
            accepted.add(index)
        }
        val indices = accepted.toArray()
        if (!grouped) {
            return ServerLogView(
                document = this,
                matchCount = indices.size,
                groupCount = 0,
                groupRows = null,
                entryIndices = indices,
            )
        }
        val rows = groupRows(indices)
        return ServerLogView(
            document = this,
            matchCount = indices.size,
            groupCount = rows.size,
            groupRows = rows,
            entryIndices = indices,
        )
    }

    internal fun entryRow(index: Int): ServerLogRow =
        ServerLogRow(
            key = entryKey(index),
            groupId = groupIds[index],
            entryIndex = index,
            tag = tags[tagIds[index]],
            level = LogLevel.entries[levels[index].toInt()],
            time = clock(seconds[index]),
            count = 1,
            label = emptyList(),
            truncated = false,
            times = "",
        )

    override fun close() {
        synchronized(reader) {
            cache.clear()
            try {
                reader.close()
            } catch (_: IOException) {}
        }
    }

    private fun groupRows(indices: IntArray): List<ServerLogRow> {
        val counts = IntArray(groups.size)
        val occurrences = Array(MAX_LISTED_TIMES) { IntArray(groups.size) }
        val last = IntArray(groups.size)
        for (index in indices) {
            val group = groupIds[index]
            val seen = counts[group]
            if (seen < MAX_LISTED_TIMES) occurrences[seen][group] = index
            last[group] = index
            counts[group] = seen + 1
        }
        return groups.indices
            .filter { counts[it] > 0 }
            .sortedBy { occurrences[0][it] }
            .map { id ->
                val group = groups[id]
                val count = counts[id]
                val first = occurrences[0][id]
                val repeated = count > 1
                ServerLogRow(
                    key = "g$id",
                    groupId = id,
                    entryIndex = first,
                    tag = tags[group.tagId],
                    level = group.level,
                    time = clock(seconds[first]),
                    count = count,
                    label = if (repeated) group.label else emptyList(),
                    truncated = repeated && group.truncated,
                    times =
                        when {
                            !repeated -> ""
                            count <= MAX_LISTED_TIMES ->
                                (0 until count).joinToString(" · ") {
                                    clock(seconds[occurrences[it][id]])
                                }
                            else -> "${clock(seconds[first])} – ${clock(seconds[last[id]])}"
                        },
                )
            }
    }

    private fun countTags(): List<LogTagCount> {
        val totals = IntArray(tags.size)
        val warnings = IntArray(tags.size)
        val errors = IntArray(tags.size)
        for (index in 0 until entryCount) {
            val tag = tagIds[index]
            totals[tag]++
            when (levels[index].toInt()) {
                LogLevel.WARN.ordinal -> warnings[tag]++
                LogLevel.ERROR.ordinal -> errors[tag]++
            }
        }
        return tags.indices
            .map { LogTagCount(tags[it], totals[it], warnings[it], errors[it]) }
            .sortedWith(compareByDescending<LogTagCount> { it.total }.thenBy { it.tag })
    }

    private fun readText(fromIndex: Int, toIndex: Int, maxBytes: Int): String {
        val start = starts[fromIndex]
        val length = (starts[toIndex + 1] - start).coerceAtMost(maxBytes.toLong()).toInt()
        return try {
            val bytes = ByteArray(length)
            reader.seek(start)
            reader.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        } catch (_: IOException) {
            ""
        }
    }

    companion object {

        fun open(file: File, checkActive: () -> Unit): ServerLogDocument? {
            val starts = LongList()
            val seconds = IntList()
            val levels = IntList()
            val tagIds = IntList()
            val shapeIds = IntList()
            val tags = ArrayList<String>()
            val tagIndex = HashMap<String, Int>()
            val shapes = ArrayList<Shape>()
            val shapeIndex = HashMap<String, Int>()
            var end = 0L

            file.inputStream().use { input ->
                val lines = LineReader(input)
                var lineNumber = 0
                while (true) {
                    val lineStart = lines.offset
                    val line = lines.next() ?: break
                    if (lineNumber++ % CHECK_INTERVAL == 0) checkActive()
                    if (!line.startsWith('[')) continue
                    val match = headerPattern.matchEntire(line) ?: continue
                    val (hours, minutes, secs, levelCode, source, message) = match.destructured
                    val level = levelOf(levelCode)
                    val tag = source.trim().substringAfterLast('.')
                    val tagId = tagIndex.getOrPut(tag) { tags.add(tag).let { tags.lastIndex } }
                    val index = seconds.size

                    val values = ArrayList<String>()
                    val signature = signatureOf(message, values)
                    val shapeKey = "$tagId|${level.ordinal}|$signature"
                    val known = shapeIndex[shapeKey]
                    val shapeId =
                        if (known == null) {
                            shapes.add(Shape(tagId, level, signature, values, index))
                            shapeIndex[shapeKey] = shapes.lastIndex
                            shapes.lastIndex
                        } else {
                            shapes[known].absorb(values)
                            known
                        }

                    starts.add(lineStart)
                    seconds.add(
                        hours.toInt() * SECONDS_PER_HOUR +
                            minutes.toInt() * SECONDS_PER_MINUTE +
                            secs.toInt()
                    )
                    levels.add(level.ordinal)
                    tagIds.add(tagId)
                    shapeIds.add(shapeId)
                }
                end = lines.offset
            }

            if (seconds.size == 0) return null
            starts.add(end)

            val shapeToGroup = IntArray(shapes.size)
            val groups = buildGroups(shapes, shapeToGroup)
            val shapeOfEntry = shapeIds.toArray()

            return ServerLogDocument(
                file = file,
                starts = starts.toArray(),
                seconds = seconds.toArray(),
                levels = levels.toArray().let { all -> ByteArray(all.size) { all[it].toByte() } },
                tagIds = tagIds.toArray(),
                groupIds = IntArray(shapeOfEntry.size) { shapeToGroup[shapeOfEntry[it]] },
                tags = tags,
                groups = groups,
            )
        }

        fun readTail(file: File, maxLines: Int, checkActive: () -> Unit): ServerLogTail {
            val tail = ArrayDeque<String>()
            var total = 0
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (total % CHECK_INTERVAL == 0) checkActive()
                    total++
                    if (tail.size == maxLines) tail.removeFirst()
                    tail.addLast(line)
                }
            }
            return ServerLogTail(lines = tail.toList(), totalLines = total)
        }

        private fun buildGroups(shapes: List<Shape>, shapeToGroup: IntArray): List<ServerLogGroup> {
            val tokens = shapes.map { tokensOf(it.signature) }
            val buckets = LinkedHashMap<String, MutableList<Int>>()
            shapes.forEachIndexed { id, shape ->
                val prefix = tokens[id].take(PREFIX_TOKENS).joinToString(" ")
                buckets
                    .getOrPut("${shape.tagId}|${shape.level.ordinal}|$prefix") { mutableListOf() }
                    .add(id)
            }

            val drafts = ArrayList<Draft>()
            buckets.values.forEach { ids ->
                if (ids.size >= MERGE_MIN_SHAPES) {
                    val members = ids.map { shapes[it] }
                    val first = members.first()
                    drafts.add(
                        Draft(
                            firstEntry = first.firstEntry,
                            shapeIds = ids,
                            group =
                                ServerLogGroup(
                                    tagId = first.tagId,
                                    level = first.level,
                                    label = mergedLabel(members, ids.map { tokens[it] }),
                                    truncated = true,
                                ),
                        )
                    )
                } else {
                    ids.forEach { id ->
                        val shape = shapes[id]
                        drafts.add(
                            Draft(
                                firstEntry = shape.firstEntry,
                                shapeIds = listOf(id),
                                group =
                                    ServerLogGroup(
                                        tagId = shape.tagId,
                                        level = shape.level,
                                        label =
                                            labelOf(shape.signature) { slot ->
                                                shape.values[slot].takeUnless { shape.varies[slot] }
                                            },
                                        truncated = false,
                                    ),
                            )
                        )
                    }
                }
            }

            drafts.sortBy { it.firstEntry }
            drafts.forEachIndexed { groupId, draft ->
                draft.shapeIds.forEach { shapeToGroup[it] = groupId }
            }
            return drafts.map { it.group }
        }

        private fun mergedLabel(members: List<Shape>, tokens: List<List<String>>): List<LabelPart> {
            val first = tokens.first()
            val shortest = tokens.minOf { it.size }
            var common = 0
            while (common < shortest && tokens.all { it[common] == first[common] }) common++
            val lead = members.first()
            return labelOf(first.take(common).joinToString(" ")) { slot ->
                lead.values.getOrNull(slot)?.takeIf { value ->
                    members.all {
                        slot < it.varies.size && !it.varies[slot] && it.values[slot] == value
                    }
                }
            }
        }

        private fun labelOf(template: String, stableValue: (Int) -> String?): List<LabelPart> {
            val parts = ArrayList<LabelPart>()
            val literal = StringBuilder()
            var slot = 0
            for (char in template) {
                if (char != VARIABLE_MARK) {
                    literal.append(char)
                    continue
                }
                val value = stableValue(slot++)
                if (value != null) {
                    literal.append(value)
                } else {
                    if (literal.isNotEmpty()) {
                        parts.add(LabelPart.Literal(literal.toString()))
                        literal.clear()
                    }
                    parts.add(LabelPart.Variable)
                }
            }
            if (literal.isNotEmpty()) parts.add(LabelPart.Literal(literal.toString()))
            return parts
        }

        private fun signatureOf(message: String, values: MutableList<String>): String {
            val signature = StringBuilder(message.length)
            var cursor = 0
            variablePattern.findAll(message).forEach { match ->
                signature.append(message, cursor, match.range.first).append(VARIABLE_MARK)
                values.add(match.value)
                cursor = match.range.last + 1
            }
            signature.append(message, cursor, message.length)
            return signature.toString()
        }

        private fun tokensOf(signature: String): List<String> =
            signature.trim().split(whitespacePattern).filter { it.isNotEmpty() }

        private fun levelOf(code: String): LogLevel =
            when (code) {
                "ERR",
                "FTL" -> LogLevel.ERROR
                "WRN" -> LogLevel.WARN
                "INF" -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }
    }
}

private class Shape(
    val tagId: Int,
    val level: LogLevel,
    val signature: String,
    val values: List<String>,
    val firstEntry: Int,
) {
    val varies = BooleanArray(values.size)

    fun absorb(other: List<String>) {
        for (slot in varies.indices) {
            if (!varies[slot] && other.getOrNull(slot) != values[slot]) varies[slot] = true
        }
    }
}

private class Draft(val firstEntry: Int, val shapeIds: List<Int>, val group: ServerLogGroup)

private class IntList {
    private var data = IntArray(INITIAL_CAPACITY)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}

private class LongList {
    private var data = LongArray(INITIAL_CAPACITY)
    private var size = 0

    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): LongArray = data.copyOf(size)
}

private class LineReader(private val input: InputStream) {
    private val buffer = ByteArray(READ_BUFFER_BYTES)
    private val line = ByteArrayOutputStream(INITIAL_CAPACITY)
    private var position = 0
    private var limit = 0

    var offset = 0L
        private set

    fun next(): String? {
        line.reset()
        var pending = false
        while (true) {
            if (position == limit) {
                limit = input.read(buffer).coerceAtLeast(0)
                position = 0
                if (limit == 0) return if (pending) text() else null
            }
            val start = position
            while (position < limit && buffer[position] != NEWLINE) position++
            if (position > start) {
                line.write(buffer, start, position - start)
                pending = true
            }
            offset += position - start
            if (position < limit) {
                position++
                offset++
                return text()
            }
        }
    }

    private fun text(): String = line.toString(Charsets.UTF_8.name()).removeSuffix("\r")
}

private fun entryKey(index: Int): String = "e$index"

private fun parseEntry(text: String): ServerLogEntry {
    val lineEnd = text.indexOf('\n')
    val head = (if (lineEnd < 0) text else text.substring(0, lineEnd)).removeSuffix("\r")
    val message = headerPattern.matchEntire(head)?.groupValues?.get(6) ?: head
    val trace =
        if (lineEnd < 0) null
        else text.substring(lineEnd + 1).replace("\r\n", "\n").trimEnd().ifEmpty { null }
    return ServerLogEntry(message = message, trace = trace)
}

private fun clock(seconds: Int): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return "${pad(hours)}:${pad(minutes)}:${pad(seconds % SECONDS_PER_MINUTE)}"
}

private fun pad(value: Int): String = value.toString().padStart(2, '0')

private fun skipFully(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped <= 0) {
            if (input.read() < 0) return
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
    var total = 0
    while (total < length) {
        val read = input.read(buffer, total, length - total)
        if (read < 0) break
        total += read
    }
    return total
}
