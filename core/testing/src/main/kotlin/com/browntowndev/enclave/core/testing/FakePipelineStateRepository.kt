package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.port.repository.PipelineStateRepository

/** In-memory [PipelineStateRepository] fake (ENC-28): stores the latest state per chat and records calls. */
class FakePipelineStateRepository : PipelineStateRepository {
    val persistedStates = mutableMapOf<String, PipelineState>()
    val persistCalls = mutableListOf<Pair<String, PipelineState>>()
    val clearCalls = mutableListOf<String>()

    override suspend fun persistPipelineState(chatId: String, state: PipelineState) {
        persistCalls += chatId to state
        persistedStates[chatId] = state
    }

    override suspend fun getPipelineState(chatId: String): PipelineState? = persistedStates[chatId]

    override suspend fun clearPipelineState(chatId: String) {
        clearCalls += chatId
        persistedStates.remove(chatId)
    }
}
