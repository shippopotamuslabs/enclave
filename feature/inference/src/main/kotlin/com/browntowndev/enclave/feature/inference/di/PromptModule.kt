package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.usecase.chat.SearchToolPromptComposer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PromptModule {
    @Binds
    @Singleton
    abstract fun bindPromptComposer(impl: SearchToolPromptComposer): PromptComposerPort
}
