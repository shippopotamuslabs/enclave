package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort

class FakeInferenceFactory : InferenceFactoryPort {
    val resolvedTypes = mutableListOf<ModelType>()
    var serviceToReturn: LlmInferencePort? = null
    val serviceMap = mutableMapOf<ModelType, LlmInferencePort>()
    var exceptionToThrow: Throwable? = null

    override suspend fun getInferenceService(modelType: ModelType): LlmInferencePort {
        exceptionToThrow?.let { throw it }
        resolvedTypes.add(modelType)
        return serviceMap[modelType] ?: serviceToReturn ?: throw IllegalStateException("Service for $modelType not configured")
    }
}
