package com.browntowndev.pocketcrew.inference.llama

import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig

/**
 * Represents the role of a message in a chat conversation.
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT;

    companion object {
        /**
         * Converts domain Role to inference ChatRole.
         */
        fun fromDomainRole(role: Role): ChatRole = when (role) {
            Role.SYSTEM -> SYSTEM
            Role.USER -> USER
            Role.ASSISTANT -> ASSISTANT
        }
    }
}

/**
 * A single message in a chat conversation.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String
)
