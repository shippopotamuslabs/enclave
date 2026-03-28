package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

/**
 * Validates input and saves an API model configuration.
 */
class SaveApiModelUseCase @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
) {
    suspend operator fun invoke(
        id: Long = 0,
        displayName: String,
        provider: ApiProvider,
        modelId: String,
        apiKey: String,
        baseUrl: String? = null,
        isVision: Boolean = false,
        thinkingEnabled: Boolean = false,
        maxTokens: Int = 4096,
        contextWindow: Int = 4096,
        temperature: Double = 0.7,
        topP: Double = 0.95,
        topK: Int? = null,
        frequencyPenalty: Double = 0.0,
        presencePenalty: Double = 0.0,
        stopSequences: List<String> = emptyList(),
    ): Long {
        require(displayName.isNotBlank()) { "display name cannot be blank" }
        require(modelId.isNotBlank()) { "model ID cannot be blank" }
        if (id == 0L) {
            require(apiKey.isNotBlank()) { "API key cannot be blank for new models" }
        }
        require(maxTokens > 0) { "max tokens must be > 0" }
        require(contextWindow > 0) { "context window must be > 0" }
        require(temperature in 0.0..2.0) { "temperature must be between 0.0 and 2.0" }
        require(topP in 0.0..1.0) { "top_p must be between 0.0 and 1.0" }
        require(frequencyPenalty in -2.0..2.0) { "frequency penalty must be between -2.0 and 2.0" }
        require(presencePenalty in -2.0..2.0) { "presence penalty must be between -2.0 and 2.0" }
        require(topK == null || (topK >= 1 && topK <= 100)) { "top_k must be between 1 and 100" }
        require(stopSequences.size <= 5) { "max 5 stop sequences allowed" }

        val config = ApiModelConfig(
            id = id,
            displayName = displayName,
            provider = provider,
            modelId = modelId,
            baseUrl = baseUrl,
            isVision = isVision,
            thinkingEnabled = thinkingEnabled,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
            temperature = temperature,
            topP = topP,
            topK = topK,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            stopSequences = stopSequences
        )

        return apiModelRepository.save(config, apiKey)
    }
}
