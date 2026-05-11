package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.domain.port.inference.PromptComposerPort
import com.browntowndev.pocketcrew.feature.inference.SearchToolPromptComposerImpl
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
    abstract fun bindPromptComposer(impl: SearchToolPromptComposerImpl): PromptComposerPort
}
