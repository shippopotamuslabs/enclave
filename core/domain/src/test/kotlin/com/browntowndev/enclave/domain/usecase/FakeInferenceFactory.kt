package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort

class FakeInferenceFactory : InferenceFactoryPort {
    val resolvedTypes = mutableListOf<ModelType>()
    val executedTypes = mutableListOf<ModelType>()
    val resolvedLocalOverrideConfigIds = mutableListOf<LocalModelConfigurationId>()
    var serviceToReturn: LlmInferencePort? = null
    val serviceMap = mutableMapOf<ModelType, LlmInferencePort>()
    var localOverrideService: LlmInferencePort? = null
    var exceptionToThrow: Throwable? = null

    override suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        exceptionToThrow?.let { throw it }
        resolvedTypes.add(modelType)
        executedTypes.add(modelType)
        val service =
            serviceMap[modelType] ?: serviceToReturn
                ?: throw IllegalStateException("Service for $modelType not configured")
        return block(service)
    }

    override suspend fun <T> withInferenceServiceForLocalConfig(
        configId: LocalModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        exceptionToThrow?.let { throw it }
        resolvedLocalOverrideConfigIds.add(configId)
        val service =
            localOverrideService ?: serviceToReturn
                ?: throw IllegalStateException("Local override service for $configId not configured")
        return block(service)
    }

    override suspend fun <T> withRawApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        val service = serviceToReturn ?: throw IllegalStateException("withRawApiServiceForConfig: service not configured")
        return block(service)
    }

    override suspend fun <T> withApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        val service = serviceToReturn ?: throw IllegalStateException("withApiServiceForConfig: service not configured")
        return block(service)
    }
}
