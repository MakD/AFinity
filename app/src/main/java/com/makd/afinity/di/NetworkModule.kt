package com.makd.afinity.di

import android.content.Context
import com.makd.afinity.BuildConfig
import com.makd.afinity.core.AppConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
                if (message.contains("ERROR") || message.contains("FAILED") || message.contains("-->") || message.contains("<--")) {
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
}