package com.browntowndev.enclave.feature.studio.di

import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.feature.studio.service.AndroidStudioGenerationDispatcher
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
