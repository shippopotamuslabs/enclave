package com.browntowndev.enclave.domain.port.inference

object NoopToolSearchEmbeddingCachePort : ToolSearchEmbeddingCachePort {
    override suspend fun ensureStaticToolsWarmed(embeddingEngine: EmbeddingEnginePort) = Unit

    override suspend fun normalizedEmbeddingFor(
        toolName: String,
        searchableText: String,
        embeddingEngine: EmbeddingEnginePort,
    ): FloatArray = embeddingEngine.getEmbedding(searchableText)
}
