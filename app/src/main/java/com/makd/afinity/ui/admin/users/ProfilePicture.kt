package com.makd.afinity.ui.admin.users

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val MAX_PICTURE_EDGE = 1024
private const val JPEG_QUALITY = 90
private const val PNG_QUALITY = 100

internal class ProfilePicture(val bytes: ByteArray, val mimeType: String)

internal suspend fun readProfilePicture(context: Context, uri: Uri): ProfilePicture? =
    withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > MAX_PICTURE_EDGE) {
                        val scale = MAX_PICTURE_EDGE.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                }
            val keepsTransparency = bitmap.hasAlpha()
            val output = ByteArrayOutputStream()
            if (keepsTransparency) {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            bitmap.recycle()
            ProfilePicture(
                bytes = output.toByteArray(),
                mimeType = if (keepsTransparency) "image/png" else "image/jpeg",
            )
        } catch (e: IOException) {
            Timber.w(e, "Failed to read the picked picture")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No access to the picked picture")
            null
        }
    }
