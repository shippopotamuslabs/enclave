package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.app.tts.InMemoryTtsPlaybackRegistry
import com.browntowndev.pocketcrew.app.tts.TtsPlaybackController
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRegistryPort
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
