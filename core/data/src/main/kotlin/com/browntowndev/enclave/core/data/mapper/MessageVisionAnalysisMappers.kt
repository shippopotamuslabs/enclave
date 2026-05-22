package com.browntowndev.enclave.core.data.mapper

import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis

fun MessageVisionAnalysisEntity.toDomain(): MessageVisionAnalysis =
    MessageVisionAnalysis(
        id = id,
        userMessageId = userMessageId,
        imageUri = imageUri,
        promptText = promptText,
        analysisText = analysisText,
        modelType = modelType,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
