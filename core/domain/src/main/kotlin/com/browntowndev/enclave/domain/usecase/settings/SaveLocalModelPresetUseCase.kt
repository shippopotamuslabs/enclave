package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import javax.inject.Inject

class SaveLocalModelPresetUseCase
    @Inject
    constructor(
        private val saveLocalModelConfigurationUseCase: SaveLocalModelConfigurationUseCase,
    ) {
        suspend operator fun invoke(
            localModelId: LocalModelId,
            draft: LocalModelPresetDraft,
        ): Result<LocalModelConfigurationId> {
            if (draft.isSystemPreset) {
                return Result.failure<LocalModelConfigurationId>(IllegalStateException("System presets are read-only"))
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
                    reasoningBudget =
                        draft.reasoningBudget.toIntOrNull()
                            ?.coerceIn(1, draft.contextWindow.toIntOrNull() ?: 4096)
                            ?: LocalModelConfiguration.defaultReasoningBudget(
                                draft.contextWindow.toIntOrNull() ?: 4096,
                            ),
                    systemPrompt = draft.systemPrompt,
                ),
            )
        }
    }
