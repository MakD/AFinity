package com.makd.afinity.data.manager

import android.content.Context
import com.makd.afinity.di.ImageClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserImageStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:ImageClient private val okHttpClient: OkHttpClient,
) {
    private val dir: File by lazy { File(context.filesDir, "user_avatars").apply { mkdirs() } }

    fun localAvatar(userId: UUID, tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val file = File(dir, "${userId}_$tag.jpg")
        return if (file.exists() && file.length() > 0) "file://${file.absolutePath}" else null
    }

    suspend fun cacheAvatar(userId: UUID, baseUrl: String, tag: String?) =
        withContext(Dispatchers.IO) {
            if (tag.isNullOrBlank() || baseUrl.isBlank()) return@withContext
            val target = File(dir, "${userId}_$tag.jpg")
            if (target.exists() && target.length() > 0) return@withContext
            try {
                val url = "${baseUrl.removeSuffix("/")}/Users/$userId/Images/Primary?tag=$tag"
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val tmp = File(dir, "${userId}_$tag.jpg.tmp")
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() > 0 && tmp.renameTo(target)) {
                        dir.listFiles { f ->
                                f.name.startsWith("${userId}_") && f.name != target.name
                            }
                            ?.forEach { it.delete() }
                    } else {
                        tmp.delete()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to cache avatar for $userId")
            }
        }
}
