package com.makd.afinity.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resume

suspend fun OkHttpClient.pingUrl(url: String): Boolean =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(Request.Builder().url(url).get().build())

        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        Timber.d("Ping failed for $url: ${e.message}")
                        continuation.resume(false)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { if (continuation.isActive) continuation.resume(it.isSuccessful) }
                }
            }
        )
    }
