package com.makd.afinity.data.books

import android.content.Context
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.di.DownloadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.extensions.libraryApi
import timber.log.Timber

enum class BookOpenError {
    NOT_SIGNED_IN,
    NETWORK,
    UNSUPPORTED_FORMAT,
    CORRUPT,
    NO_PAGES,
    PASSWORD_PROTECTED,
}

class BookOpenException(val error: BookOpenError, cause: Throwable? = null) :
    Exception(error.name, cause)

@Singleton
class BookFileCache
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    @param:DownloadClient private val okHttpClient: OkHttpClient,
) {
    private val evictionLock = Mutex()

    private val root: File
        get() = File(context.cacheDir, ROOT_DIR)

    fun bookDir(itemId: UUID): File = File(root, itemId.toString()).apply { mkdirs() }

    fun sourceFile(itemId: UUID): File = File(bookDir(itemId), SOURCE_FILE)

    fun touch(itemId: UUID) {
        bookDir(itemId).setLastModified(System.currentTimeMillis())
    }

    suspend fun download(itemId: UUID, onProgress: (Long, Long?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = sourceFile(itemId)
            if (target.exists() && target.length() > 0) return@withContext target
            val partial = File(bookDir(itemId), "$SOURCE_FILE.part")
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: throw BookOpenException(BookOpenError.NOT_SIGNED_IN)
            val url = apiClient.libraryApi.getDownloadUrl(itemId = itemId)
            val existing = if (partial.exists()) partial.length() else 0L
            val request =
                Request.Builder()
                    .url(url)
                    .header(
                        "Authorization",
                        "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                    )
                    .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                    .build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 416 && existing > 0) {
                        partial.renameTo(target)
                        return@use
                    }
                    if (!response.isSuccessful) {
                        throw BookOpenException(BookOpenError.NETWORK)
                    }
                    val append = response.code == 206 && existing > 0
                    val body = response.body
                    val total =
                        body
                            .contentLength()
                            .takeIf { it > 0 }
                            ?.let { if (append) it + existing else it }
                    var downloaded = if (append) existing else 0L
                    onProgress(downloaded, total)
                    FileOutputStream(partial, append).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    if (!partial.renameTo(target)) {
                        throw BookOpenException(BookOpenError.NETWORK)
                    }
                }
            } catch (e: BookOpenException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Book download failed for $itemId")
                throw BookOpenException(BookOpenError.NETWORK, e)
            }
            target
        }

    suspend fun evict(keep: UUID) =
        withContext(Dispatchers.IO) {
            evictionLock.withLock {
                val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
                val sizes = dirs.associateWith { dir -> dir.walkBottomUp().sumOf { it.length() } }
                var total = sizes.values.sum()
                if (total <= MAX_CACHE_BYTES) return@withLock
                dirs
                    .filter { it.name != keep.toString() }
                    .sortedBy { it.lastModified() }
                    .forEach { dir ->
                        if (total <= MAX_CACHE_BYTES) return@withLock
                        total -= sizes[dir] ?: 0L
                        dir.deleteRecursively()
                    }
            }
        }

    companion object {
        private const val ROOT_DIR = "books"
        private const val SOURCE_FILE = "source"
        private const val BUFFER_SIZE = 64 * 1024
        const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L
    }
}
