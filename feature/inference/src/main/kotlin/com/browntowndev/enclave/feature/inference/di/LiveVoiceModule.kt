package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.feature.inference.liveVoice.LiveVoicePortImpl
import com.browntowndev.enclave.feature.inference.media.RealtimeAudioCaptureImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveVoiceModule {
    @Binds
    @Singleton
    abstract fun bindLiveVoicePort(impl: LiveVoicePortImpl): LiveVoicePort

    @Binds
    @Singleton
    abstract fun bindRealtimeAudioCapturePort(impl: RealtimeAudioCaptureImpl): RealtimeAudioCapturePort
}
