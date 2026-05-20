package com.browntowndev.pocketcrew.app

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.browntowndev.pocketcrew.core.ui.util.StudioMediaFetcher
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        cryptoManager: CryptoManagerPort,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Add our custom fetcher for decrypted media
                // We add it before the default OkHttpNetworkFetcherFactory
                // so it can intercept the specific Firebase Storage URLs
                add(StudioMediaFetcher.Factory(cryptoManager, okHttpClient))
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }.build()
}
