package com.makd.afinity.di

import android.content.Context
import com.makd.afinity.BuildConfig
import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.network.AudiobookshelfApiService
import com.makd.afinity.data.network.JellyseerrApiService
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JellyseerrClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudiobookshelfRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideClientInfo(): ClientInfo = ClientInfo(
        name = AppConstants.APP_NAME,
        version = AppConstants.VERSION_NAME
    )

    @Provides
    @Singleton
    fun provideDeviceInfo(@ApplicationContext context: Context): DeviceInfo = androidDevice(context)

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val dispatcher = Dispatcher(Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "Jellyfin-OkHttp").apply {
                isDaemon = false
            }
        }).apply {
            maxRequests = 45
            maxRequestsPerHost = 10
        }

        val connectionPool = ConnectionPool(
            maxIdleConnections = 10,
            keepAliveDuration = 30,
            timeUnit = TimeUnit.SECONDS
        )

        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "http_cache"),
                    maxSize = 50L * 1024L * 1024L
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
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                if (message.contains("ERROR") || message.contains("FAILED") || message.contains("-->") || message.contains(
                        "<--"
                    )
                ) {
                    Timber.tag("Jellyfin-HTTP").d(message)
                }
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val dispatcher = Dispatcher(Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "Download-OkHttp").apply {
                isDaemon = false
            }
        }).apply {
            maxRequests = 5
            maxRequestsPerHost = 2
        }

        val connectionPool = ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )

        val builder = OkHttpClient.Builder()
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
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                if (message.contains("ERROR") || message.contains("FAILED") || message.contains("-->") || message.contains(
                        "<--"
                    )
                ) {
                    Timber.tag("Download-HTTP").d(message)
                }
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
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
        okHttpFactory: OkHttpFactory
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
        securePreferencesRepository: SecurePreferencesRepository
    ): OkHttpClient {
        return baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                val savedUrl = securePreferencesRepository.getCachedJellyseerrServerUrl()
                val currentBaseUrl = try {
                    if (!savedUrl.isNullOrBlank()) {
                        normalizeJellyseerrUrl(savedUrl)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

                if (currentBaseUrl == null) {
                    throw IOException("Jellyseerr server URL not configured. Please configure the server URL first.")
                }

                val newUrl = currentBaseUrl.toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegments(originalRequest.url.encodedPath.removePrefix("/"))
                    ?.apply {
                        for (i in 0 until originalRequest.url.querySize) {
                            addQueryParameter(
                                originalRequest.url.queryParameterName(i),
                                originalRequest.url.queryParameterValue(i)
                            )
                        }
                    }
                    ?.build()
                    ?: throw IOException("Failed to build Jellyseerr URL")

                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .apply {
                        val cookie = securePreferencesRepository.getCachedJellyseerrCookie()
                        cookie?.let {
                            addHeader("Cookie", it)
                        }
                        addHeader("Content-Type", "application/json")
                    }
                    .build()

                chain.proceed(newRequest)
            }
            .build()
    }

    private val jellyseerrJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideJellyseerrRetrofit(
        @JellyseerrClient okHttpClient: OkHttpClient
    ): Retrofit {
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

    @Provides
    @Singleton
    @AudiobookshelfClient
    fun provideAudiobookshelfOkHttpClient(
        baseOkHttpClient: OkHttpClient,
        securePreferencesRepository: SecurePreferencesRepository
    ): OkHttpClient {
        return baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                val savedUrl = securePreferencesRepository.getCachedAudiobookshelfServerUrl()
                val currentBaseUrl = try {
                    if (!savedUrl.isNullOrBlank()) {
                        normalizeAudiobookshelfUrl(savedUrl)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

                if (currentBaseUrl == null) {
                    throw IOException("Audiobookshelf server URL not configured. Please configure the server URL first.")
                }

                val newUrl = currentBaseUrl.toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegments(originalRequest.url.encodedPath.removePrefix("/"))
                    ?.apply {
                        for (i in 0 until originalRequest.url.querySize) {
                            addQueryParameter(
                                originalRequest.url.queryParameterName(i),
                                originalRequest.url.queryParameterValue(i)
                            )
                        }
                    }
                    ?.build()
                    ?: throw IOException("Failed to build Audiobookshelf URL")

                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .apply {
                        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
                        token?.let {
                            addHeader("Authorization", "Bearer $it")
                        }
                        addHeader("Content-Type", "application/json")
                    }
                    .build()

                val response = chain.proceed(newRequest)
                if (newUrl.encodedPath.contains("/play")) {
                    val responseBody = response.body
                    val source = responseBody?.source()
                    source?.request(Long.MAX_VALUE)
                    val buffer = source?.buffer?.clone()
                    val responseString = buffer?.readUtf8()
                    timber.log.Timber.d("ABS Play Response [${response.code}]: $responseString")
                }

                response
            }
            .build()
    }

    private val audiobookshelfJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @AudiobookshelfRetrofit
    fun provideAudiobookshelfRetrofit(
        @AudiobookshelfClient okHttpClient: OkHttpClient
    ): Retrofit {
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
}