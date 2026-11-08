package com.makd.afinity.data.repository.subtitles

import android.content.Context
import com.makd.afinity.data.models.subtitles.DeviceSubtitle
import com.makd.afinity.data.models.subtitles.DeviceSubtitleFolder
import com.makd.afinity.data.models.subtitles.DeviceSubtitleMeta
import com.makd.afinity.data.models.subtitles.DeviceSubtitleStats
import com.makd.afinity.data.models.subtitles.ServerAddedSubtitle
import com.makd.afinity.data.models.subtitles.SubtitleItemIdentity
import com.makd.afinity.util.ItemIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

data class SubtitleScope(val serverId: String, val userId: UUID)

@Singleton
class DeviceSubtitleStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val root: File
        get() = File(context.filesDir, ROOT_DIR)

    suspend fun list(
        scope: SubtitleScope,
        itemId: UUID,
        identity: SubtitleItemIdentity,
    ): List<DeviceSubtitle> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir =
                    itemDir(scope, itemId).takeIf { it.isDirectory }
                        ?: reattachLocked(scope, itemId, identity)
                        ?: return@withLock emptyList()
                val folder = readFolder(dir) ?: return@withLock emptyList()
                folder.subtitles.mapNotNull { meta ->
                    File(dir, meta.fileName).takeIf { it.isFile }?.let { DeviceSubtitle(it, meta) }
                }
            }
        }

    suspend fun save(
        scope: SubtitleScope,
        itemId: UUID,
        identity: SubtitleItemIdentity,
        meta: DeviceSubtitleMeta,
        content: ByteArray,
    ): DeviceSubtitle =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = itemDir(scope, itemId)
                if (!dir.isDirectory && !dir.mkdirs()) {
                    throw IllegalStateException("Could not create the subtitle folder")
                }
                val file = File(dir, meta.fileName)
                val temp = File(dir, "${meta.fileName}.tmp")
                temp.writeBytes(content)
                if (!temp.renameTo(file)) {
                    temp.delete()
                    throw IllegalStateException("Could not save the subtitle")
                }
                val folder = readFolder(dir) ?: DeviceSubtitleFolder(itemId = itemId.toString())
                writeFolder(
                    dir,
                    folder.copy(
                        identity = identity,
                        subtitles =
                            folder.subtitles.filterNot { it.fileName == meta.fileName } + meta,
                    ),
                )
                _changes.value++
                DeviceSubtitle(file, meta)
            }
        }

    suspend fun remove(scope: SubtitleScope, itemId: UUID, fileName: String): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = itemDir(scope, itemId)
                val folder = readFolder(dir) ?: return@withLock
                File(dir, fileName).delete()
                val remaining = folder.subtitles.filterNot { it.fileName == fileName }
                if (remaining.isEmpty() && folder.serverAdded.isEmpty()) {
                    dir.deleteRecursively()
                } else {
                    writeFolder(
                        dir,
                        folder.copy(
                            subtitles = remaining,
                            lastSelectedFileName =
                                folder.lastSelectedFileName.takeIf { it != fileName },
                        ),
                    )
                }
                _changes.value++
            }
        }

    suspend fun removeAll(scope: SubtitleScope): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val profileDir = profileDir(scope)
                profileDir.listFiles()?.forEach { dir ->
                    val folder = readFolder(dir) ?: return@forEach
                    folder.subtitles.forEach { File(dir, it.fileName).delete() }
                    if (folder.serverAdded.isEmpty()) {
                        dir.deleteRecursively()
                    } else {
                        writeFolder(
                            dir,
                            folder.copy(subtitles = emptyList(), lastSelectedFileName = null),
                        )
                    }
                }
                _changes.value++
            }
        }

    suspend fun stats(scope: SubtitleScope): DeviceSubtitleStats =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var count = 0
                var bytes = 0L
                profileDir(scope).listFiles()?.forEach { dir ->
                    readFolder(dir)?.subtitles?.forEach { meta ->
                        val file = File(dir, meta.fileName)
                        if (file.isFile) {
                            count++
                            bytes += file.length()
                        }
                    }
                }
                DeviceSubtitleStats(count, bytes)
            }
        }

    suspend fun setLastSelected(scope: SubtitleScope, itemId: UUID, fileName: String?): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = itemDir(scope, itemId)
                val folder = readFolder(dir) ?: return@withLock
                if (folder.lastSelectedFileName == fileName) return@withLock
                writeFolder(dir, folder.copy(lastSelectedFileName = fileName))
            }
        }

    suspend fun lastSelected(scope: SubtitleScope, itemId: UUID): String? =
        withContext(Dispatchers.IO) {
            mutex.withLock { readFolder(itemDir(scope, itemId))?.lastSelectedFileName }
        }

    suspend fun recordServerAdded(
        scope: SubtitleScope,
        itemId: UUID,
        identity: SubtitleItemIdentity,
        added: ServerAddedSubtitle,
    ): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = itemDir(scope, itemId)
                if (!dir.isDirectory && !dir.mkdirs()) return@withLock
                val folder = readFolder(dir) ?: DeviceSubtitleFolder(itemId = itemId.toString())
                writeFolder(
                    dir,
                    folder.copy(
                        identity = identity,
                        serverAdded =
                            folder.serverAdded.filterNot { it.fileName == added.fileName } + added,
                    ),
                )
                _changes.value++
            }
        }

    suspend fun serverAdded(scope: SubtitleScope, itemId: UUID): List<ServerAddedSubtitle> =
        withContext(Dispatchers.IO) {
            mutex.withLock { readFolder(itemDir(scope, itemId))?.serverAdded.orEmpty() }
        }

    suspend fun prune(scope: SubtitleScope, deletedItemIds: Set<String>): Unit =
        withContext(Dispatchers.IO) {
            if (deletedItemIds.isEmpty()) return@withContext
            mutex.withLock {
                var removed = false
                profileDir(scope).listFiles()?.forEach { dir ->
                    if (normalizeId(dir.name) in deletedItemIds) {
                        dir.deleteRecursively()
                        removed = true
                    }
                }
                if (removed) _changes.value++
            }
        }

    fun mayBeOutOfSync(meta: DeviceSubtitleMeta, currentRuntimeTicks: Long): Boolean {
        if (meta.runtimeTicksAtSave <= 0L || currentRuntimeTicks <= 0L) return false
        val drift = abs(currentRuntimeTicks - meta.runtimeTicksAtSave).toDouble()
        return drift / meta.runtimeTicksAtSave > OUT_OF_SYNC_RATIO
    }

    private fun reattachLocked(
        scope: SubtitleScope,
        itemId: UUID,
        identity: SubtitleItemIdentity,
    ): File? {
        val target = itemDir(scope, itemId)
        val match =
            profileDir(scope).listFiles()?.firstOrNull { dir ->
                dir.isDirectory &&
                    dir.name != target.name &&
                    readFolder(dir)?.identity?.matches(identity) == true
            } ?: return null
        if (!match.renameTo(target)) return null
        readFolder(target)?.let { writeFolder(target, it.copy(itemId = itemId.toString())) }
        Timber.d("Re-attached device subtitles to a new item id")
        _changes.value++
        return target
    }

    private fun readFolder(dir: File): DeviceSubtitleFolder? {
        val metaFile = File(dir, META_FILE)
        if (!metaFile.isFile) return null
        return try {
            json.decodeFromString(DeviceSubtitleFolder.serializer(), metaFile.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unreadable device subtitle metadata")
            null
        }
    }

    private fun writeFolder(dir: File, folder: DeviceSubtitleFolder) {
        val temp = File(dir, "$META_FILE.tmp")
        temp.writeText(json.encodeToString(DeviceSubtitleFolder.serializer(), folder))
        val target = File(dir, META_FILE)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("Could not save subtitle metadata")
            }
        }
    }

    private fun profileDir(scope: SubtitleScope): File =
        File(File(root, safeName(scope.serverId)), normalizeId(scope.userId.toString()))

    private fun itemDir(scope: SubtitleScope, itemId: UUID): File =
        File(profileDir(scope), normalizeId(itemId.toString()))

    private fun normalizeId(raw: String): String = ItemIds.normalize(raw) ?: safeName(raw)

    private fun safeName(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val ROOT_DIR = "device_subtitles"
        private const val META_FILE = "meta.json"
        private const val OUT_OF_SYNC_RATIO = 0.01

        fun fileName(subtitleId: String, language: String, format: String): String {
            val id = subtitleId.replace(Regex("[^A-Za-z0-9]"), "")
            val lang = language.replace(Regex("[^A-Za-z0-9-]"), "").ifBlank { "und" }
            val ext = format.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "srt" }
            return "$id.$lang.$ext"
        }
    }
}
