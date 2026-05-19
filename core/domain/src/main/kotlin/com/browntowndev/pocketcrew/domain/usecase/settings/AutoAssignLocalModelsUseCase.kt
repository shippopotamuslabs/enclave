package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to automatically assign the first available local model to all logical text slots.
 * This is used during logout to ensure the app remains functional for guest users without
 * cloud configurations.
 */
class AutoAssignLocalModelsUseCase
    @Inject
    constructor(
        private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
        private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
    ) {
        suspend operator fun invoke() {
            val currentAssignments = getDefaultModelsUseCase().first()
            val assignedTypes = currentAssignments.map { it.modelType }.toSet()

            val localAssets = getLocalModelAssetsUseCase().first()
            if (localAssets.isEmpty()) return

            // Pick the first available local configuration
            val firstAsset = localAssets.first()
            val firstConfig = firstAsset.configurations.firstOrNull() ?: return

            // Auto-assign to all LLM-based slots that are currently empty
            ModelType.entries
                .filter { it.isTextModel && it !in assignedTypes }
                .forEach { modelType ->
                    setDefaultModelUseCase(
                        modelType = modelType,
                        localConfigId = firstConfig.id,
                        apiConfigId = null,
                        ttsProviderId = null,
                        mediaProviderId = null,
                    )
                }
        }
    }
