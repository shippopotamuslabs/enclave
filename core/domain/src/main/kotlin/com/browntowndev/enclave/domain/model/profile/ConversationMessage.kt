package com.browntowndev.enclave.domain.model.profile

data class ConversationMessage(
    val role: MessageRole,
    val content: String,
    val createdAtMs: Long?,
    val orderIndex: Int,
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
    UNKNOWN,
}
