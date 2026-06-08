package com.browntowndev.enclave.domain.port.inference

/**
 * Process-lifetime cache of L2-normalized MiniLM embeddings for tool discovery.
 * Static [com.browntowndev.enclave.domain.model.inference.ToolDefinition] entries are warmed once;
 * dynamic catalog entries (e.g. provider-specific media tools) embed on first sight.
 */
interface ToolSearchEmbeddingCachePort {
    /** Embeds every static tool definition once; safe to call repeatedly. */
    suspend fun ensureStaticToolsWarmed(embeddingEngine: EmbeddingEnginePort)

    /** Returns a cached normalized embedding or computes, stores, and returns one. */
    suspend fun normalizedEmbeddingFor(
        toolName: String,
        searchableText: String,
        embeddingEngine: EmbeddingEnginePort,
    ): FloatArray
}
