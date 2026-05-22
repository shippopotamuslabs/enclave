package com.browntowndev.enclave.domain.model.memory

/**
 * Tuning for automatic memory retrieval injected into chat prompts.
 *
 * Embeddings are L2-normalized (384-dim MiniLM). sqlite-vec uses L2 distance, so for unit
 * vectors: L2² = 2 × (1 − cosine_similarity). [MAX_L2_DISTANCE] ≈ 0.85 corresponds to
 * cosine similarity ≥ ~0.64.
 */
object MemoryRetrievalPolicy {
    const val CHAT_RETRIEVAL_LIMIT = 5

    /** Maximum L2 distance for a memory to be considered relevant. */
    const val MAX_L2_DISTANCE = 0.85f

    /** Categories always injected via [MemoriesRepository.getCoreMemories]. */
    val CHAT_EXCLUDED_CATEGORIES: Set<MemoryCategory> =
        setOf(
            MemoryCategory.CORE_IDENTITY,
            MemoryCategory.PREFERENCES,
        )

    /**
     * Initial k-NN pool size before category/distance filtering. Must exceed [CHAT_RETRIEVAL_LIMIT]
     * so excluded core memories do not consume all slots.
     */
    const val SEARCH_CANDIDATE_POOL = 25
}
