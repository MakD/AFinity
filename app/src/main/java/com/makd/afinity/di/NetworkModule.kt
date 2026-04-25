package com.makd.afinity.di

import android.content.Context
import com.makd.afinity.BuildConfig
import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.network.AudiobookshelfApiService
import com.makd.afinity.data.network.JellyseerrApiService
import com.makd.afinity.data.network.MdbListApiService
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DownloadClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ImageClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GitHubClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class JellyseerrClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AudiobookshelfRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TmdbClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MdbListClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private class SeerrCookieJar(private val securePrefs: SecurePreferencesRepository) : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        private fun getSessionKey(host: String): String {
            val activeSession = securePrefs.getCachedJellyseerrCookie() ?: "anonymous"
            return "${host}_${activeSession.hashCode()}"
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = getSessionKey(url.host)
            val bucket = store.getOrPut(key) { mutableListOf() }
            synchronized(bucket) {
                for (c in cookies) {
                    bucket.removeIf { it.name == c.name }
                    if (c.value.isNotEmpty()) bucket.add(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val key = getSessionKey(url.host)
            val bucket = store[key] ?: return emptyList()
            synchronized(bucket) {
                return bucket.filter { !it.secure || url.scheme == "https" }
            }
        }

        fun preloadSessionCookie(url: HttpUrl, rawSetCookie: String?) {
            rawSetCookie ?: return
            Cookie.parse(url, rawSetCookie)?.let { saveFromResponse(url, listOf(it)) }
        }

        fun hasXsrfToken(host: String): Boolean {
            val key = getSessionKey(host)
            val bucket = store[key] ?: return false
            synchronized(bucket) {
                return bucket.any { it.name == "XSRF-TOKEN" }
            }
        }

        fun getXsrfToken(host: String): String? {
            val key = getSessionKey(host)
            val bucket = store[key] ?: return null
            synchronized(bucket) {
                return bucket.find { it.name == "XSRF-TOKEN" }?.value
            }
        }

        fun clear(host: String? = null) {
            if (host != null) {
                store.remove(getSessionKey(host))
            } else {
                store.clear()
            }
        }
    }

    @Provides
    @Singleton
    fun provideClientInfo(): ClientInfo =
        ClientInfo(name = AppConstants.APP_NAME, version = AppConstants.VERSION_NAME)

    @Provides
    @Singleton
    fun provideDeviceInfo(@ApplicationContext context: Context): DeviceInfo = androidDevice(context)

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val dispatcher =
            Dispatcher(
                    Executors.newCachedThreadPool { runnable ->
                        Thread(runnable, "Jellyfin-OkHttp").apply { isDaemon = false }
                    }
                )
                .apply {
                    maxRequests = 45
                    maxRequestsPerHost = 10
                }

        val connectionPool =
            ConnectionPool(
                maxIdleConnections = 10,
                keepAliveDuration = 30,
                timeUnit = TimeUnit.SECONDS,
            )

        val builder =
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .cache(
                    Cache(
                        directory = File(context.cacheDir, "http_cache"),
                        maxSize = 50L * 1024L * 1024L,
                    )
                )
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                        val sanitizedMessage =
                            message.replace(
                                Regex("(?i)(api_key|token|accessToken)=[^&\\s]+"),
                                "$1=[REDACTED]",
                            )
                        if (
                            sanitizedMessage.contains("ERROR") ||
                                sanitizedMessage.contains("FAILED") ||
                                sanitizedMessage.contains("-->") ||
                                sanitizedMessage.contains("<--")
                        ) {
                            Timber.tag("Jellyfin-HTTP").d(sanitizedMessage)
                        }
                    }
                    .apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("X-MediaBrowser-Token")
                        redactHeader("x-refresh-token")
                    }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @ImageClient
    fun provideImageOkHttpClient(
        baseOkHttpClient: OkHttpClient,
        securePreferencesRepository: SecurePreferencesRepository,
    ): OkHttpClient {
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            }
        return baseOkHttpClient
            .newBuilder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                val request = chain.request()
                val token = securePreferencesRepository.getCachedJellyfinToken()
                val serverUrl = securePreferencesRepository.getCachedJellyfinServerUrl()
                if (token != null && serverUrl != null) {
                    val serverHost = serverUrl.toHttpUrlOrNull()?.host
                    if (serverHost != null && request.url.host == serverHost) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .addHeader("Authorization", "MediaBrowser Token=$token")
                                .build()
                        )
                    }
                }
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @GitHubClient
    fun provideGitHubOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val dispatcher =
            Dispatcher(
                    Executors.newCachedThreadPool { runnable ->
                        Thread(runnable, "Download-OkHttp").apply { isDaemon = false }
                    }
                )
                .apply {
                    maxRequests = 5
                    maxRequestsPerHost = 2
                }

        val connectionPool =
            ConnectionPool(
                maxIdleConnections = 5,
                keepAliveDuration = 5,
                timeUnit = TimeUnit.MINUTES,
            )

        val builder =
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .callTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                        val sanitizedMessage =
                            message.replace(
                                Regex("(?i)(api_key|token|accessToken)=[^&\\s]+"),
                                "$1=[REDACTED]",
                            )

                        if (
                            sanitizedMessage.contains("ERROR") ||
                                sanitizedMessage.contains("FAILED") ||
                                sanitizedMessage.contains("-->") ||
                                sanitizedMessage.contains("<--")
                        ) {
                            Timber.tag("Download-HTTP").d(sanitizedMessage)
                        }
                    }
                    .apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("X-MediaBrowser-Token")
                    }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOkHttpFactory(baseOkHttpClient: OkHttpClient): OkHttpFactory {
        return OkHttpFactory(base = baseOkHttpClient)
    }

    @Provides
    @Singleton
    fun provideJellyfin(
        @ApplicationContext context: Context,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
        okHttpFactory: OkHttpFactory,
    ): Jellyfin {
        return createJellyfin {
            this.context = context
            this.clientInfo = clientInfo
            this.deviceInfo = deviceInfo
            this.apiClientFactory = okHttpFactory
            this.socketConnectionFactory = okHttpFactory
        }
    }

    @Provides
    @Singleton
    fun provideApiClient(jellyfin: Jellyfin): ApiClient {
        return jellyfin.createApi()
    }

    private fun normalizeJellyseerrUrl(raw: String?): String {
        if (raw.isNullOrBlank()) {
            throw IOException("Seerr server URL not configured")
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

    @Provides
    @Singleton
    @JellyseerrClient
    fun provideJellyseerrOkHttpClient(
        baseOkHttpClient: OkHttpClient,
        securePreferencesRepository: SecurePreferencesRepository,
    ): OkHttpClient {
        val seerrCookieJar = SeerrCookieJar(securePreferencesRepository)
        val csrfSeedClient =
            baseOkHttpClient
                .newBuilder()
                .cookieJar(seerrCookieJar)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

        return baseOkHttpClient
            .newBuilder()
            .cookieJar(seerrCookieJar)
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                val savedUrl = securePreferencesRepository.getCachedJellyseerrServerUrl()
                val currentBaseUrl =
                    try {
                        if (!savedUrl.isNullOrBlank()) normalizeJellyseerrUrl(savedUrl) else null
                    } catch (e: Exception) {
                        null
                    }

                if (currentBaseUrl == null) {
                    throw IOException(
                        "Jellyseerr server URL not configured. Please configure the server URL first."
                    )
                }

                val baseHttpUrl =
                    currentBaseUrl.toHttpUrlOrNull()
                        ?: throw IOException("Failed to parse Jellyseerr URL")

                seerrCookieJar.preloadSessionCookie(
                    baseHttpUrl,
                    securePreferencesRepository.getCachedJellyseerrCookie(),
                )

                val newUrl =
                    baseHttpUrl
                        .newBuilder()
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

                val isMutating = originalRequest.method in listOf("POST", "PUT", "DELETE", "PATCH")

                if (isMutating && !seerrCookieJar.hasXsrfToken(baseHttpUrl.host)) {
                    val candidates = listOf(currentBaseUrl)
                    for (url in candidates) {
                        try {
                            csrfSeedClient
                                .newCall(Request.Builder().url(url).get().build())
                                .execute()
                                .close()
                            if (seerrCookieJar.hasXsrfToken(baseHttpUrl.host)) {
                                if (url != currentBaseUrl)
                                    securePreferencesRepository.updateCachedJellyseerrServerUrl(
                                        url.trimEnd('/')
                                    )
                                break
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Jellyseerr: CSRF seed failed for $url")
                        }
                    }
                }

                val newRequest =
                    originalRequest
                        .newBuilder()
                        .url(newUrl)
                        .apply {
                            addHeader("Content-Type", "application/json")
                            seerrCookieJar.getXsrfToken(baseHttpUrl.host)?.let {
                                addHeader("XSRF-TOKEN", it)
                            }
                        }
                        .build()

                val response = chain.proceed(newRequest)

                if (response.code == 403) {
                    seerrCookieJar.clear(baseHttpUrl.host)
                }

                response
            }
            .build()
    }

    private val jellyseerrJson =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    @Provides
    @Singleton
    fun provideJellyseerrRetrofit(@JellyseerrClient okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        val baseUrl = "http://placeholder.jellyseerr/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(jellyseerrJson.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyseerrApiService(retrofit: Retrofit): JellyseerrApiService {
        return retrofit.create(JellyseerrApiService::class.java)
    }

    private fun normalizeAudiobookshelfUrl(raw: String?): String {
        if (raw.isNullOrBlank()) {
            throw IOException("Audiobookshelf server URL not configured")
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

    private val absTokenRefreshLock = Any()

    @Provides
    @Singleton
    @AudiobookshelfClient
    fun provideAudiobookshelfOkHttpClient(
        baseOkHttpClient: OkHttpClient,
        securePreferencesRepository: SecurePreferencesRepository,
    ): OkHttpClient {
        return baseOkHttpClient
            .newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                val savedUrl = securePreferencesRepository.getCachedAudiobookshelfServerUrl()
                val currentBaseUrl =
                    try {
                        if (!savedUrl.isNullOrBlank()) {
                            normalizeAudiobookshelfUrl(savedUrl)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }

                if (currentBaseUrl == null) {
                    throw IOException(
                        "Audiobookshelf server URL not configured. Please configure the server URL first."
                    )
                }

                val newUrl =
                    buildAbsUrl(originalRequest, currentBaseUrl)
                        ?: throw IOException("Failed to build Audiobookshelf URL")

                val token = securePreferencesRepository.getCachedAudiobookshelfToken()
                val newRequest =
                    originalRequest
                        .newBuilder()
                        .url(newUrl)
                        .apply {
                            token?.let { addHeader("Authorization", "Bearer $it") }
                            addHeader("Content-Type", "application/json")
                            addHeader("x-return-tokens", "true")
                        }
                        .build()

                val response = chain.proceed(newRequest)

                if (newUrl.encodedPath.contains("/play")) {
                    Timber.d("ABS Play Response [${response.code}]")
                }

                if (response.code == 401 && !newUrl.encodedPath.contains("auth")) {
                    response.close()

                    synchronized(absTokenRefreshLock) {
                        val currentToken =
                            securePreferencesRepository.getCachedAudiobookshelfToken()
                        if (currentToken != null && currentToken != token) {
                            val retryRequest =
                                originalRequest
                                    .newBuilder()
                                    .url(newUrl)
                                    .header("Authorization", "Bearer $currentToken")
                                    .header("Content-Type", "application/json")
                                    .header("x-return-tokens", "true")
                                    .build()
                            return@addInterceptor chain.proceed(retryRequest)
                        }

                        val refreshToken =
                            securePreferencesRepository.getCachedAudiobookshelfRefreshToken()
                        if (refreshToken != null) {
                            val refreshResult =
                                attemptAbsTokenRefresh(
                                    currentBaseUrl,
                                    refreshToken,
                                    baseOkHttpClient,
                                )
                            if (refreshResult != null) {
                                securePreferencesRepository.updateCachedAudiobookshelfTokens(
                                    refreshResult.first,
                                    refreshResult.second,
                                )
                                val retryRequest =
                                    originalRequest
                                        .newBuilder()
                                        .url(newUrl)
                                        .header("Authorization", "Bearer ${refreshResult.first}")
                                        .header("Content-Type", "application/json")
                                        .header("x-return-tokens", "true")
                                        .build()
                                return@addInterceptor chain.proceed(retryRequest)
                            } else {
                                Timber.w("ABS token refresh failed, clearing invalid refresh token")
                                securePreferencesRepository.updateCachedAudiobookshelfTokens(
                                    token ?: "",
                                    null,
                                )
                                securePreferencesRepository.onAbsAuthInvalidated?.invoke()
                            }
                        }
                    }
                    securePreferencesRepository.onAbsAuthInvalidated?.invoke()
                    return@addInterceptor Response.Builder()
                        .request(originalRequest)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized - token refresh failed")
                        .body("".toResponseBody(null))
                        .build()
                }

                response
            }
            .build()
    }

    private fun buildAbsUrl(originalRequest: Request, baseUrl: String): HttpUrl? {
        return baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(originalRequest.url.encodedPath.removePrefix("/"))
            ?.apply {
                for (i in 0 until originalRequest.url.querySize) {
                    addQueryParameter(
                        originalRequest.url.queryParameterName(i),
                        originalRequest.url.queryParameterValue(i),
                    )
                }
            }
            ?.build()
    }

    private fun attemptAbsTokenRefresh(
        baseUrl: String,
        refreshToken: String,
        baseClient: OkHttpClient,
    ): Pair<String, String?>? {
        return try {
            val refreshUrl = "${baseUrl}auth/refresh"
            val refreshClient =
                baseClient
                    .newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

            val request =
                Request.Builder()
                    .url(refreshUrl)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .addHeader("x-refresh-token", refreshToken)
                    .addHeader("Content-Type", "application/json")
                    .build()

            val response = refreshClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val jsonObj = json.parseToJsonElement(body).jsonObject
                val userObj = jsonObj["user"]?.jsonObject
                val newAccessToken =
                    userObj?.get("accessToken")?.jsonPrimitive?.content
                        ?: jsonObj["accessToken"]?.jsonPrimitive?.content
                val newRefreshToken =
                    userObj?.get("refreshToken")?.jsonPrimitive?.content
                        ?: jsonObj["refreshToken"]?.jsonPrimitive?.content
                if (newAccessToken != null) {
                    Timber.d("ABS token refresh successful")
                    Pair(newAccessToken, newRefreshToken)
                } else {
                    Timber.w("ABS token refresh response missing accessToken")
                    null
                }
            } else {
                Timber.w("ABS token refresh failed: ${response.code}")
                response.close()
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "ABS token refresh error")
            null
        }
    }

    private val audiobookshelfJson =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    @Provides
    @Singleton
    @AudiobookshelfRetrofit
    fun provideAudiobookshelfRetrofit(@AudiobookshelfClient okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        val baseUrl = "http://placeholder.audiobookshelf/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(audiobookshelfJson.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAudiobookshelfApiService(
        @AudiobookshelfRetrofit retrofit: Retrofit
    ): AudiobookshelfApiService {
        return retrofit.create(AudiobookshelfApiService::class.java)
    }

    @Provides
    @Singleton
    @TmdbClient
    fun provideTmdbRetrofit(baseOkHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/")
            .client(baseOkHttpClient)
            .addConverterFactory(jellyseerrJson.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(@TmdbClient retrofit: Retrofit): TmdbApiService {
        return retrofit.create(TmdbApiService::class.java)
    }

    @Provides
    @Singleton
    @MdbListClient
    fun provideMdbListRetrofit(baseOkHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl("https://api.mdblist.com/")
            .client(baseOkHttpClient)
            .addConverterFactory(jellyseerrJson.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideMdbListApiService(@MdbListClient retrofit: Retrofit): MdbListApiService {
        return retrofit.create(MdbListApiService::class.java)
    }
}
