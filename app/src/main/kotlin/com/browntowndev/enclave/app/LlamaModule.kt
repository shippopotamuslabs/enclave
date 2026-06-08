package com.browntowndev.enclave.app

import com.browntowndev.enclave.domain.port.inference.LlamaEnginePort
import com.browntowndev.enclave.feature.inference.llama.JniLlamaEngine
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
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
 *
 * NOTE: Engine and session manager are NOT singletons - each pipeline step gets its own
 * isolated instance to prevent state conflicts between pipeline stages.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlamaEngineModule {
    @Provides
    fun provideLlamaEngine(
        ioDispatcher: CoroutineDispatcher,
    ): LlamaEnginePort = JniLlamaEngine(ioDispatcher)

    @Provides
    fun provideLlamaChatSessionManager(engine: LlamaEnginePort): LlamaChatSessionManager = LlamaChatSessionManager(engine)
}
