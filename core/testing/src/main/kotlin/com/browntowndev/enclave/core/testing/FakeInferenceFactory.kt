package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.CompletableDeferred

/**
 * [InferenceFactoryPort] fake (ENC-28). Records which model types / config ids
 * were invoked and runs the caller's block against [llm]. When [blockGate] is
 * set, invocations suspend on it first (concurrency tests use it to hold a
 * pipeline step open).
 */
class FakeInferenceFactory(
    val llm: FakeLlmInferencePort = FakeLlmInferencePort(),
) : InferenceFactoryPort {
    val invokedModelTypes = mutableListOf<ModelType>()
    val invokedLocalConfigIds = mutableListOf<LocalModelConfigurationId>()
    val invokedApiConfigIds = mutableListOf<ApiModelConfigurationId>()

    var blockGate: CompletableDeferred<Unit>? = null

    override suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        invokedModelTypes += modelType
        blockGate?.await()
        return block(llm)
    }

    override suspend fun <T> withInferenceServiceForLocalConfig(
        configId: LocalModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        invokedLocalConfigIds += configId
        blockGate?.await()
        return block(llm)
    }

    override suspend fun <T> withRawApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        invokedApiConfigIds += configId
        blockGate?.await()
        return block(llm)
    }

    override suspend fun <T> withApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T {
        invokedApiConfigIds += configId
        blockGate?.await()
        return block(llm)
    }
}
