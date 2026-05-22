package com.browntowndev.enclave.whisper

import com.browntowndev.enclave.domain.port.inference.WhisperInferencePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {
    @Provides
    @Singleton
    fun provideWhisperInferencePort(): WhisperInferencePort = JniWhisperInference()
}
