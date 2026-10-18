package com.browntowndev.enclave.app

import com.browntowndev.enclave.domain.port.inference.WhisperInferencePort
import com.shippopotamus.whisper.JniWhisperInference
import com.shippopotamus.whisper.WhisperEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for on-device speech-to-text. Replaces the module that used to live
 * in the in-repo `:whisper-android` module before the engine was extracted to its
 * own repo (ENC-53); the port is now provided via [WhisperInferenceAdapter].
 */
@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {
    @Provides
    @Singleton
    fun provideWhisperEngine(): WhisperEngine = JniWhisperInference()

    @Provides
    @Singleton
    fun provideWhisperInferencePort(engine: WhisperEngine): WhisperInferencePort = WhisperInferenceAdapter(engine)
}
