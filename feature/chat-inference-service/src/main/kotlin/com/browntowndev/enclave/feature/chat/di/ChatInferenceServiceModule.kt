package com.browntowndev.enclave.feature.chat.di

import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.feature.chat.service.ChatInferenceExecutorRouter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatInferenceServiceModule {
    @Binds
    @Singleton
    abstract fun bindChatInferenceExecutorPort(impl: ChatInferenceExecutorRouter): ChatInferenceExecutorPort
}
