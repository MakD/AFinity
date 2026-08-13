package com.makd.afinity.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrCookieJar @Inject constructor() : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Volatile private var sessionKey: String? = null

    fun setSession(sessionKey: String?) {
        this.sessionKey = sessionKey
        store.clear()
    }

    fun clearAll() {
        store.clear()
    }

    private fun bucketKey(host: String): String = "${host}_${sessionKey ?: "none"}"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = bucketKey(url.host)
        val bucket = store.getOrPut(key) { mutableListOf() }
        synchronized(bucket) {
            for (c in cookies) {
                bucket.removeIf { it.name == c.name }
                if (c.value.isNotEmpty()) bucket.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val key = bucketKey(url.host)
        val bucket = store[key] ?: return emptyList()
        synchronized(bucket) {
            return bucket.filter { !it.secure || url.scheme == "https" }
        }
    }

    fun preloadSessionCookie(url: HttpUrl, rawSetCookie: String?) {
        if (rawSetCookie.isNullOrBlank()) return
        Cookie.parse(url, rawSetCookie)?.let { saveFromResponse(url, listOf(it)) }
    }

    fun sessionCookieHeader(host: String): String? {
        val bucket = store[bucketKey(host)] ?: return null
        synchronized(bucket) {
            return bucket.find { it.name == SESSION_COOKIE_NAME }?.toString()
        }
    }

    fun hasXsrfToken(host: String): Boolean {
        val bucket = store[bucketKey(host)] ?: return false
        synchronized(bucket) {
            return bucket.any { it.name == "XSRF-TOKEN" }
        }
    }

    fun getXsrfToken(host: String): String? {
        val bucket = store[bucketKey(host)] ?: return null
        synchronized(bucket) {
            return bucket.find { it.name == "XSRF-TOKEN" }?.value
        }
    }

    fun clear(host: String? = null) {
        if (host != null) {
            store.remove(bucketKey(host))
        } else {
            store.clear()
        }
    }

    private companion object {
        const val SESSION_COOKIE_NAME = "connect.sid"
    }
}
