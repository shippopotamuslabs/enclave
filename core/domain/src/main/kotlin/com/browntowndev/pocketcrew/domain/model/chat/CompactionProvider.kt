package com.browntowndev.pocketcrew.domain.model.chat

/**
 * Interface for context compaction providers.
 * Designed to be extensible for configured API models or future on-device ONNX models.
 */
interface CompactionProvider {
    val id: String
    val name: String
    
    /**
     * Compacts the given chat history into a more concise representation.
     * 
     * @param history The list of chat messages to compact.
     * @return The compacted text content.
     */
    suspend fun compact(history: List<ChatMessage>): String
}

/**
 * Types of available compaction providers.
 */
enum class CompactionProviderType {
    DISABLED,
    API_MODEL,
    TINY_ONNX // Future proofing
}
