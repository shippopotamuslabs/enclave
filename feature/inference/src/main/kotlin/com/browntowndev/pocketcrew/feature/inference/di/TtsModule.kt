package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.pocketcrew.feature.inference.tts.TtsServiceFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsServiceFactory(impl: TtsServiceFactory): TtsServiceFactoryPort
}
