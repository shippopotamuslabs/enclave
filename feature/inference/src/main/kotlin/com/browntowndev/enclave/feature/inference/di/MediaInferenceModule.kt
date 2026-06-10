package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCancelPort
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.media.MusicGenerationPort
import com.browntowndev.enclave.domain.port.media.VideoGenerationPort
import com.browntowndev.enclave.feature.inference.media.GpuCapabilityPortImpl
import com.browntowndev.enclave.feature.inference.media.local.LocalImageGenerationCancelPortImpl
import com.browntowndev.enclave.feature.inference.media.LocalImageGenerationCapabilityPortImpl
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.MusicGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.VideoGenerationPortImpl
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
    abstract fun bindLocalImageGenerationCancelPort(
        impl: LocalImageGenerationCancelPortImpl,
    ): LocalImageGenerationCancelPort

    @Binds
    @Singleton
    abstract fun bindGpuCapabilityPort(impl: GpuCapabilityPortImpl): GpuCapabilityPort

    @Binds
    @Singleton
    abstract fun bindLocalImageGenerationCapabilityPort(
        impl: LocalImageGenerationCapabilityPortImpl,
    ): LocalImageGenerationCapabilityPort

    @Binds
    @Singleton
    abstract fun bindImageGenerationPort(impl: ImageGenerationPortImpl): ImageGenerationPort

    @Binds
    @Singleton
    abstract fun bindVideoGenerationPort(impl: VideoGenerationPortImpl): VideoGenerationPort

    @Binds
    @Singleton
    abstract fun bindMusicGenerationPort(impl: MusicGenerationPortImpl): MusicGenerationPort
}
