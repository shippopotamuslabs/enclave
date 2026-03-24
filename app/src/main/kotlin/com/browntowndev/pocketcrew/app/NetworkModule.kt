package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = ModelConfig.CONCURRENT_DOWNLOADS
            maxRequestsPerHost = ModelConfig.CONCURRENT_DOWNLOADS
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)  // 5 minutes for large downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
