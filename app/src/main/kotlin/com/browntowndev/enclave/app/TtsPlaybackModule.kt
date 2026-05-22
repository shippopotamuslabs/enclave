package com.browntowndev.enclave.app

import com.browntowndev.enclave.app.tts.InMemoryTtsPlaybackRegistry
import com.browntowndev.enclave.app.tts.TtsPlaybackController
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackRegistryPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the Media3 TTS playback dependencies.
 *
 * This module provides:
 * - [TtsPlaybackRegistryPort] → [InMemoryTtsPlaybackRegistry]
 * - [TtsPlaybackControllerPort] → [TtsPlaybackController]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsPlaybackModule {
    @Binds
    @Singleton
    abstract fun bindTtsPlaybackRegistry(impl: InMemoryTtsPlaybackRegistry): TtsPlaybackRegistryPort

    @Binds
    @Singleton
    abstract fun bindTtsPlaybackController(impl: TtsPlaybackController): TtsPlaybackControllerPort
}
