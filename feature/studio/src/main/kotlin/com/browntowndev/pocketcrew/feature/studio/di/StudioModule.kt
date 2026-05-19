package com.browntowndev.pocketcrew.feature.studio.di

import com.browntowndev.pocketcrew.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.pocketcrew.feature.studio.service.AndroidStudioGenerationDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StudioModule {
    @Binds
    @Singleton
    abstract fun bindStudioGenerationDispatcher(impl: AndroidStudioGenerationDispatcher): StudioGenerationDispatcherPort
}
