package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAnalysisInferenceAdapter
    @Inject
    constructor(
        private val inferenceFactory: InferenceFactoryPort,
        private val apiModelRepository: ApiModelRepositoryPort,
    ) : ProfileAnalysisInferencePort {
        override suspend fun completeTurn(
            configId: ApiModelConfigurationId,
            messages: List<AgentLoopMessage>,
            systemPrompt: String,
            tools: List<ToolDefinition>,
            onTextToken: suspend (String) -> Unit,
        ): AgentModelTurnResult =
            inferenceFactory.withRawApiServiceForConfig(configId) { service ->
                val rustService = service as RustCompatibleInferenceService
                val options = GenerationOptions(reasoningBudget = 0, activeTools = tools)
                rustService.completeSingleTurn(
                    sessionMessages = messages,
                    systemPrompt = systemPrompt,
                    availableTools = tools,
                    allowToolCall = true,
                    onTextToken = onTextToken,
                    onThinkingToken = {},
                    options = options,
                )
            }

        override suspend fun getContextWindowTokens(configId: ApiModelConfigurationId): Int =
            apiModelRepository.getConfigurationById(configId)?.contextWindow
                ?: DEFAULT_FALLBACK_CONTEXT_WINDOW

        companion object {
            /**
             * Conservative fallback used when the model config cannot be resolved.
             * A safe floor that avoids context overflows on smaller-window models.
             */
            const val DEFAULT_FALLBACK_CONTEXT_WINDOW = 32_000
        }
    }
