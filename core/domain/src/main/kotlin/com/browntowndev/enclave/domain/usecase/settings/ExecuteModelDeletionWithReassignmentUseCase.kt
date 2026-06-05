package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase
import javax.inject.Inject

class ExecuteModelDeletionWithReassignmentUseCase
    @Inject
    constructor(
        private val deleteLocalModelUseCase: DeleteLocalModelUseCase,
        private val deleteLocalModelConfigurationUseCase: DeleteLocalModelConfigurationUseCase,
        private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase,
        private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
        private val deleteModelPackUseCase: DeleteModelPackUseCase,
    ) {
        suspend operator fun invoke(
            target: ModelDeletionTarget,
            modelTypesNeedingReassignment: List<ModelType>,
            replacementLocalConfigId: LocalModelConfigurationId?,
            replacementApiConfigId: ApiModelConfigurationId?,
            replacementMediaProviderId: MediaProviderId? = null,
        ): Result<Unit> {
            val isReplacementMissing = replacementLocalConfigId == null &&
                replacementApiConfigId == null &&
                replacementMediaProviderId == null
            if (modelTypesNeedingReassignment.isNotEmpty() && isReplacementMissing) {
                return Result.failure(IllegalArgumentException("A replacement model is required"))
            }

            return Result.runCatching {
                when (target) {
                    is ModelDeletionTarget.LocalModelAsset -> {
                        deleteLocalModelUseCase(
                            modelId = target.id,
                            replacementLocalConfigId = replacementLocalConfigId,
                            replacementApiConfigId = replacementApiConfigId,
                            replacementMediaProviderId = replacementMediaProviderId,
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

                    is ModelDeletionTarget.ModelPack -> {
                        deleteModelPackUseCase(
                            packId = target.packId,
                            replacementLocalConfigId = replacementLocalConfigId,
                            replacementApiConfigId = replacementApiConfigId,
                            replacementMediaProviderId = replacementMediaProviderId,
                        ).getOrThrow()
                    }
                }
            }
        }
    }
