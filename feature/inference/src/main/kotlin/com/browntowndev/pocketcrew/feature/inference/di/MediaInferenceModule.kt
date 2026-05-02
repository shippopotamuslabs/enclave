package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.MusicGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.pocketcrew.feature.inference.media.MusicGenerationPortImpl
import com.browntowndev.pocketcrew.feature.inference.media.VideoGenerationPortImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaInferenceModule {

    @Binds
    @Singleton
    abstract fun bindImageGenerationPort(
        impl: ImageGenerationPortImpl
    ): ImageGenerationPort

    @Binds
    @Singleton
    abstract fun bindVideoGenerationPort(
        impl: VideoGenerationPortImpl
    ): VideoGenerationPort

    @Binds
    @Singleton
    abstract fun bindMusicGenerationPort(
        impl: MusicGenerationPortImpl
    ): MusicGenerationPort
}
