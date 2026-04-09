package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_MINUTES = 5L
    private const val WRITE_TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher =
            Dispatcher().apply {
                maxRequests = ModelConfig.CONCURRENT_DOWNLOADS
                maxRequestsPerHost = ModelConfig.CONCURRENT_DOWNLOADS
            }

        return OkHttpClient
            .Builder()
            .dispatcher(dispatcher)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_MINUTES, TimeUnit.MINUTES) // 5 minutes for large downloads
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCallFactory(okHttpClient: OkHttpClient): Call.Factory = okHttpClient
}
