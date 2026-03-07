package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.chat.SafetyProbe
import com.browntowndev.pocketcrew.domain.usecase.chat.SafetyProbeImpl
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
}
