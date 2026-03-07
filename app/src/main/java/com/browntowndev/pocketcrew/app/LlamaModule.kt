package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import com.browntowndev.pocketcrew.inference.llama.JniLlamaEngine
import com.browntowndev.pocketcrew.inference.llama.LlamaChatSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module for llama.cpp dispatchers.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlamaDispatchersModule {

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

/**
 * Hilt module for llama.cpp engine and session manager.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlamaEngineModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(
        ioDispatcher: CoroutineDispatcher
    ): LlamaEnginePort {
        return JniLlamaEngine(ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideLlamaChatSessionManager(
        engine: LlamaEnginePort
    ): LlamaChatSessionManager {
        return LlamaChatSessionManager(engine)
    }
}
