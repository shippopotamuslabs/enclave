package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.domain.port.inference.CommittedInferenceTranscriptPort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.search.InMemoryToolSearchEmbeddingCache
import com.browntowndev.enclave.domain.usecase.chat.InMemoryCommittedInferenceTranscriptStore
import com.browntowndev.enclave.domain.usecase.chat.InMemoryMonotonicToolActivationStore
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

    @Binds
    @Singleton
    abstract fun bindMonotonicToolActivation(impl: InMemoryMonotonicToolActivationStore): MonotonicToolActivationPort

    @Binds
    @Singleton
    abstract fun bindCommittedInferenceTranscript(impl: InMemoryCommittedInferenceTranscriptStore): CommittedInferenceTranscriptPort

    @Binds
    @Singleton
    abstract fun bindToolSearchEmbeddingCache(impl: InMemoryToolSearchEmbeddingCache): ToolSearchEmbeddingCachePort
}
