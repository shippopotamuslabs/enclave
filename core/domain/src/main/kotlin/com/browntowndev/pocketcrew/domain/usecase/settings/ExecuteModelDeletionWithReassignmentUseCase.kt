package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelUseCase
import javax.inject.Inject

class ExecuteModelDeletionWithReassignmentUseCase @Inject constructor(
    private val deleteLocalModelUseCase: DeleteLocalModelUseCase,
    private val deleteLocalModelConfigurationUseCase: DeleteLocalModelConfigurationUseCase,
    private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase,
    private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase,
    private val setDefaultModelUseCase: SetDefaultModelUseCase,
) {
    suspend operator fun invoke(
        target: ModelDeletionTarget,
        modelTypesNeedingReassignment: List<ModelType>,
        replacementLocalConfigId: Long?,
        replacementApiConfigId: Long?,
    ): Result<Unit> {
        if (
            modelTypesNeedingReassignment.isNotEmpty() &&
            replacementLocalConfigId == null &&
            replacementApiConfigId == null
        ) {
            return Result.failure(IllegalArgumentException("A replacement model is required"))
        }

        return Result.runCatching {
            when (target) {
                is ModelDeletionTarget.LocalModelAsset -> {
                    deleteLocalModelUseCase(
                        modelId = target.id,
                        replacementLocalConfigId = replacementLocalConfigId,
                        replacementApiConfigId = replacementApiConfigId,
                    ).getOrThrow()
                }

                is ModelDeletionTarget.LocalModelPreset -> {
                    modelTypesNeedingReassignment.forEach { modelType ->
                        setDefaultModelUseCase(modelType, replacementLocalConfigId, replacementApiConfigId)
                    }
                    deleteLocalModelConfigurationUseCase(target.id).getOrThrow()
                }

                is ModelDeletionTarget.ApiProvider -> {
                    deleteApiCredentialsUseCase(
                        id = target.id,
                        replacementLocalConfigId = replacementLocalConfigId,
                        replacementApiConfigId = replacementApiConfigId,
                    ).getOrThrow()
                }

                is ModelDeletionTarget.ApiPreset -> {
                    deleteApiModelConfigurationUseCase(
                        configurationId = target.id,
                        replacementLocalConfigId = replacementLocalConfigId,
                        replacementApiConfigId = replacementApiConfigId,
                    ).getOrThrow()
                }
            }
        }
    }
}
