package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.chat.BreakIteratorSentenceBoundaryDetector
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.chat.GetChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.SafetyProbe
import com.browntowndev.pocketcrew.domain.usecase.chat.SafetyProbeImpl
import com.browntowndev.pocketcrew.domain.usecase.chat.SentenceBoundaryDetector
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
    abstract fun bindSentenceBoundaryDetector(
        impl: BreakIteratorSentenceBoundaryDetector
    ): SentenceBoundaryDetector
}
