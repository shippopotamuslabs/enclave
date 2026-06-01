package com.browntowndev.enclave.domain.model.profile

data class ConversationRecord(
    val externalId: String,
    val title: String,
    val provider: ConversationProvider,
    val createdAtMs: Long?,
    val messages: List<ConversationMessage>,
) {
    val userMessages: List<ConversationMessage>
        get() = messages.filter { it.role == MessageRole.USER }

    val assistantMessages: List<ConversationMessage>
        get() = messages.filter { it.role == MessageRole.ASSISTANT }
}
