package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import javax.inject.Inject

class SaveApiPresetUseCase @Inject constructor(
    private val saveApiModelConfigurationUseCase: SaveApiModelConfigurationUseCase,
) {
    suspend operator fun invoke(
        provider: ApiProvider,
        parentCredentialsId: ApiCredentialsId,
        defaultReasoningEffort: ApiReasoningEffort?,
        draft: ApiPresetDraft,
    ): Result<ApiModelConfigurationId> {
        return saveApiModelConfigurationUseCase(
            ApiModelConfiguration(
                id = draft.id,
                apiCredentialsId = draft.credentialsId.takeIf { it.value.isNotEmpty() } ?: parentCredentialsId,
                displayName = draft.displayName,
                maxTokens = draft.maxTokens.toIntOrNull() ?: 4096,
                contextWindow = draft.contextWindow.toIntOrNull() ?: 4096,
                temperature = draft.temperature,
                topP = draft.topP,
                topK = draft.topK.toIntOrNull() ?: 40,
                minP = draft.minP,
                frequencyPenalty = draft.frequencyPenalty,
                presencePenalty = draft.presencePenalty,
                systemPrompt = draft.systemPrompt,
                reasoningEffort = draft.reasoningEffort ?: defaultReasoningEffort,
                customHeaders = draft.customHeaders
                    .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
                    .associate { (key, value) -> key to value },
                openRouterRouting = if (provider == ApiProvider.OPENROUTER) {
                    draft.openRouterRouting
                } else {
                    OpenRouterRoutingConfiguration()
                },
            )
        )
    }
}
