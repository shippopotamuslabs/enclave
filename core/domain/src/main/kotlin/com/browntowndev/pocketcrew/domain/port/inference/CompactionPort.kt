package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType

/**
 * Port for executing context compaction.
 */
interface CompactionPort {
    /**
     * Compacts the history using the active provider configured in settings.
     * 
     * @param history The messages to compact.
     * @return The compacted text, or null if compaction is disabled or fails.
     */
    suspend fun compactHistory(history: List<ChatMessage>): String?
}
