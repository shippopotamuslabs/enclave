package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort

class FakeInferenceFactory : InferenceFactoryPort {
    val resolvedTypes = mutableListOf<ModelType>()
    val executedTypes = mutableListOf<ModelType>()
    var serviceToReturn: LlmInferencePort? = null
    val serviceMap = mutableMapOf<ModelType, LlmInferencePort>()
    var exceptionToThrow: Throwable? = null

    override suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T
    ): T {
        exceptionToThrow?.let { throw it }
        resolvedTypes.add(modelType)
        executedTypes.add(modelType)
        val service = serviceMap[modelType] ?: serviceToReturn
            ?: throw IllegalStateException("Service for $modelType not configured")
        return block(service)
    }

    override suspend fun <T> withInferenceServiceByConfigId(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T
    ): T {
        exceptionToThrow?.let { throw it }
        val service = serviceToReturn
            ?: throw IllegalStateException("Service for configId $configId not configured")
        return block(service)
    }
}
