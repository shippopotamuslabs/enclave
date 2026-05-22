package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.chat.Role

/**
 * Represents the role of a message in a chat conversation.
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    ;

    companion object {
        /**
         * Converts domain Role to inference ChatRole.
         */
        fun fromDomainRole(role: Role): ChatRole =
            when (role) {
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
    val content: String,
)
