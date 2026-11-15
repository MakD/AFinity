package com.makd.afinity.data.books

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import timber.log.Timber

enum class BookContainer {
    PDF,
    ARCHIVE,
    UNKNOWN,
}

object BookFileSniffer {
    fun detect(file: File): BookContainer {
        val header = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(header) }
        if (read <= 0) return BookContainer.UNKNOWN
        return when {
            header.startsWith("%PDF") -> BookContainer.PDF
            header.startsWith("PK") -> BookContainer.ARCHIVE
            header.startsWith("Rar!") -> BookContainer.ARCHIVE
            header.startsWith(SEVEN_ZIP) -> BookContainer.ARCHIVE
            read >= 262 && String(header, 257, 5, Charsets.US_ASCII) == "ustar" ->
                BookContainer.ARCHIVE
            else -> BookContainer.UNKNOWN
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean =
        startsWith(prefix.toByteArray(Charsets.US_ASCII))

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val HEADER_BYTES = 512
    private val SEVEN_ZIP = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
}

interface BookPageSource : Closeable {
    val pageCount: Int

    suspend fun page(index: Int): File

    suspend fun thumbnail(index: Int): File = page(index)
}

class ComicPageSource private constructor(private val pages: List<File>) : BookPageSource {
    override val pageCount: Int
        get() = pages.size

    override suspend fun page(index: Int): File = pages[index]

    override fun close() = Unit

    companion object {
        private const val PAGES_DIR = "pages"
        private const val MANIFEST = "pages.list"
        private const val BLOCK_SIZE = 64L * 1024L
        private val IMAGE_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif")

        fun cached(bookDir: File): ComicPageSource? {
            val manifest = File(bookDir, MANIFEST)
            if (!manifest.exists()) return null
            val pagesDir = File(bookDir, PAGES_DIR)
            val pages = manifest.readLines().filter { it.isNotBlank() }.map { File(pagesDir, it) }
            if (pages.isEmpty() || pages.any { !it.exists() }) return null
            return ComicPageSource(pages)
        }

        suspend fun extract(
            bookDir: File,
            source: File,
            deleteSource: Boolean = true,
        ): ComicPageSource =
            withContext(Dispatchers.IO) {
                val pagesDir = File(bookDir, PAGES_DIR)
                pagesDir.deleteRecursively()
                pagesDir.mkdirs()
                val extracted = mutableListOf<Pair<String, String>>()
                val archive =
                    try {
                        Archive.readNew()
                    } catch (e: ArchiveException) {
                        throw BookOpenException(BookOpenError.CORRUPT, e)
                    }
                try {
                    Archive.readSupportFilterAll(archive)
                    Archive.readSupportFormatAll(archive)
                    Archive.readOpenFileName(
                        archive,
                        source.absolutePath.toByteArray(Charsets.UTF_8),
                        BLOCK_SIZE,
                    )
                    var entry = Archive.readNextHeader(archive)
                    while (entry != 0L) {
                        ensureActive()
                        val name = ArchiveEntry.pathnameUtf8(entry).orEmpty()
                        val isFile = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFREG
                        val extension = name.substringAfterLast('.', "").lowercase()
                        if (
                            isFile &&
                                extension in IMAGE_EXTENSIONS &&
                                !isHiddenPath(name) &&
                                !ArchiveEntry.isEncrypted(entry)
                        ) {
                            val fileName = "%05d.%s".format(extracted.size, extension)
                            val out = File(pagesDir, fileName)
                            ParcelFileDescriptor.open(
                                    out,
                                    ParcelFileDescriptor.MODE_WRITE_ONLY or
                                        ParcelFileDescriptor.MODE_CREATE or
                                        ParcelFileDescriptor.MODE_TRUNCATE,
                                )
                                .use { pfd -> Archive.readDataIntoFd(archive, pfd.fd) }
                            if (out.length() > 0) {
                                extracted += name to fileName
                            } else {
                                out.delete()
                            }
                        } else {
                            Archive.readDataSkip(archive)
                        }
                        entry = Archive.readNextHeader(archive)
                    }
                } catch (e: ArchiveException) {
                    Timber.w(e, "Comic archive read failed: ${source.name}")
                    throw BookOpenException(BookOpenError.CORRUPT, e)
                } finally {
                    runCatching { Archive.readClose(archive) }
                    runCatching { Archive.readFree(archive) }
                }
                if (extracted.isEmpty()) throw BookOpenException(BookOpenError.NO_PAGES)
                val ordered = extracted.sortedWith { a, b ->
                    NaturalOrder.compare(a.first, b.first)
                }
                File(bookDir, MANIFEST).writeText(ordered.joinToString("\n") { it.second })
                if (deleteSource) source.delete()
                ComicPageSource(ordered.map { File(pagesDir, it.second) })
            }

        private fun isHiddenPath(path: String): Boolean =
            path.split('/', '\\').any { segment ->
                segment.startsWith(".") || segment.equals("__MACOSX", ignoreCase = true)
            }
    }
}

class PdfPageSource
private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cacheDir: File,
    private val targetWidth: Int,
) : BookPageSource {
    private val lock = Mutex()

    override val pageCount: Int = renderer.pageCount

    override suspend fun page(index: Int): File = render(index, targetWidth)

    override suspend fun thumbnail(index: Int): File = render(index, THUMBNAIL_WIDTH)

    suspend fun pageText(index: Int): String = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                page.textContents.joinToString(" ") { it.text.orEmpty() }
            }
        }
    }

    suspend fun hasText(): Boolean {
        for (index in 0 until minOf(pageCount, TEXT_PROBE_PAGES)) {
            if (pageText(index).isNotBlank()) return true
        }
        return false
    }

    private suspend fun render(index: Int, width: Int): File = lock.withLock {
        withContext(Dispatchers.IO) {
            val out = File(cacheDir, "%05d_%d.png".format(index, width))
            if (out.exists() && out.length() > 0) return@withContext out
            renderer.openPage(index).use { page ->
                val height =
                    (width.toFloat() * page.height / page.width.coerceAtLeast(1))
                        .toInt()
                        .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(out).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            out
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val RENDER_DIR = "pdf"
        private const val THUMBNAIL_WIDTH = 240
        private const val TEXT_PROBE_PAGES = 5

        fun open(bookDir: File, source: File, targetWidth: Int): PdfPageSource {
            val descriptor =
                try {
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw BookOpenException(BookOpenError.CORRUPT, e)
                }
            val renderer =
                try {
                    PdfRenderer(descriptor)
                } catch (e: SecurityException) {
                    descriptor.close()
                    throw BookOpenException(BookOpenError.PASSWORD_PROTECTED, e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    descriptor.close()
                    throw BookOpenException(BookOpenError.CORRUPT, e)
                }
            if (renderer.pageCount == 0) {
                renderer.close()
                descriptor.close()
                throw BookOpenException(BookOpenError.NO_PAGES)
            }
            val cacheDir = File(bookDir, RENDER_DIR).apply { mkdirs() }
            return PdfPageSource(descriptor, renderer, cacheDir, targetWidth)
        }
    }
}

object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
