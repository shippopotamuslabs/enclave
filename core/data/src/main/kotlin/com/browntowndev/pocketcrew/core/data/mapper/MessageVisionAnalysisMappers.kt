package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis

fun MessageVisionAnalysisEntity.toDomain(): MessageVisionAnalysis = MessageVisionAnalysis(
    id = id,
    userMessageId = userMessageId,
    imageUri = imageUri,
    promptText = promptText,
    analysisText = analysisText,
    modelType = modelType,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
