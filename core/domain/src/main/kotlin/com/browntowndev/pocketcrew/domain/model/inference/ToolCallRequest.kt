package com.browntowndev.pocketcrew.domain.model.inference

data class ToolCallRequest(
    val toolName: String,
    val argumentsJson: String,
    val provider: String,
    val modelType: ModelType,
)
