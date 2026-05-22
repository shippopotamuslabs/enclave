package com.browntowndev.enclave.feature.chat.di

import com.browntowndev.enclave.data.chat.BreakIteratorSentenceBoundaryDetector
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCasesImpl
import com.browntowndev.enclave.domain.usecase.chat.SafetyProbe
import com.browntowndev.enclave.domain.usecase.chat.SafetyProbeImpl
import com.browntowndev.enclave.domain.usecase.chat.SentenceBoundaryDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatUseCasesModule {
    @Binds
    @Singleton
    abstract fun bindChatUseCases(impl: ChatUseCasesImpl): ChatUseCases

    @Binds
    @Singleton
    abstract fun bindSafetyProbe(impl: SafetyProbeImpl): SafetyProbe

    @Binds
    @Singleton
    abstract fun bindSentenceBoundaryDetector(impl: BreakIteratorSentenceBoundaryDetector): SentenceBoundaryDetector
}
