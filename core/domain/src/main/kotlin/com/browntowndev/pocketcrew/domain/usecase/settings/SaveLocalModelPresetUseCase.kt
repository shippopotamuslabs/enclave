package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import javax.inject.Inject

class SaveLocalModelPresetUseCase @Inject constructor(
    private val saveLocalModelConfigurationUseCase: SaveLocalModelConfigurationUseCase,
) {
    suspend operator fun invoke(
        localModelId: Long,
        draft: LocalModelPresetDraft,
    ): Result<Long> {
        if (draft.isSystemPreset) {
            return Result.failure(IllegalStateException("System presets are read-only"))
        }

        return saveLocalModelConfigurationUseCase(
            LocalModelConfiguration(
                id = draft.id,
                localModelId = localModelId,
                displayName = draft.displayName,
                maxTokens = draft.maxTokens.toIntOrNull() ?: 4096,
                contextWindow = draft.contextWindow.toIntOrNull() ?: 4096,
                temperature = draft.temperature,
                topP = draft.topP,
                topK = draft.topK.toIntOrNull(),
                minP = draft.minP,
                repetitionPenalty = draft.repetitionPenalty,
                thinkingEnabled = draft.thinkingEnabled,
                systemPrompt = draft.systemPrompt,
            )
        )
    }
}
