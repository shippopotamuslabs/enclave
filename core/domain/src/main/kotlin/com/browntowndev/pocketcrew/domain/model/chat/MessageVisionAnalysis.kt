package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

data class MessageVisionAnalysis(
    val id: String,
    val userMessageId: MessageId,
    val imageUri: String,
    val promptText: String,
    val analysisText: String,
    val modelType: ModelType,
    val createdAt: Long,
    val updatedAt: Long,
)
