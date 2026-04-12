package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Message
import javax.inject.Inject

/**
 * Merges a database message with an in-flight message.
 * 
 * Merge rules:
 * - If dbMessage is COMPLETE → use dbMessage (persisted final state)
 * - Otherwise → use inFlightMessage (has real-time content/state)
 * 
 * This is business logic that should live in a use case, not ViewModel.
 */
class MergeMessagesUseCase @Inject constructor() {
    
    /**
     * Merges database and in-flight messages.
     *
     * @param dbMessage Message from database (may be null if not yet persisted)
     * @param inFlightMessage Message from real-time flow (may be null if not started)
     * @return Merged message using the rules above
     */
    operator fun invoke(
        dbMessage: Message?,
        inFlightMessage: Message?
    ): Message? {
        // If no messages at all, return null
        if (dbMessage == null && inFlightMessage == null) {
            return null
        }
        
        // If only db message exists
        if (inFlightMessage == null) {
            return dbMessage
        }
        
        // If only in-flight exists
        if (dbMessage == null) {
            return inFlightMessage
        }
        
        // Both exist - apply merge rules
        return if (dbMessage.messageState == MessageState.COMPLETE) {
            // DB has the final persisted state, use it
            dbMessage
        } else {
            // DB is still processing (placeholder), use in-flight which has real content
            // We must copy createdAt from the dbMessage to preserve chronological sorting order
            inFlightMessage.copy(createdAt = dbMessage.createdAt)
        }
    }
}
