package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.chat.Role

/**
 * Domain model for chat messages used in LLM inference.
 * This is distinct from [Message] which is the full domain model stored in the database.
 * This is a lighter weight representation used for passing messages to inference services.
 *
 * @property role The role of the message sender
 * @property content The text content of the message
 * @property metadataJson Provider/agent metadata used only by inference rehydration.
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val metadataJson: String = "null",
)
