package com.makd.afinity.data.network

import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

internal fun normalizeDynamicBaseUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) {
        return null
    }

    var base = raw.trim()

    if (!base.startsWith("http://") && !base.startsWith("https://")) {
        base = "http://$base"
    }

    if (!base.endsWith("/")) {
        base += "/"
    }

    return base
}

internal fun resolveDynamicBaseUrl(rawUrl: String?, notConfiguredMessage: String): String {
    return normalizeDynamicBaseUrl(rawUrl) ?: throw IOException(notConfiguredMessage)
}

internal fun HttpUrl.rewriteWithRequestPathAndQuery(originalRequest: Request): HttpUrl {
    return newBuilder()
        .addPathSegments(originalRequest.url.encodedPath.removePrefix("/"))
        .apply {
            for (i in 0 until originalRequest.url.querySize) {
                addQueryParameter(
                    originalRequest.url.queryParameterName(i),
                    originalRequest.url.queryParameterValue(i),
                )
            }
        }
        .build()
}