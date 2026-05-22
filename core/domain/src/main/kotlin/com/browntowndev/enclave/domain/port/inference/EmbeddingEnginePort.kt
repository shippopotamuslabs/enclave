package com.browntowndev.enclave.domain.port.inference

interface EmbeddingEnginePort {
    suspend fun getEmbedding(text: String): FloatArray

    suspend fun close()
}
