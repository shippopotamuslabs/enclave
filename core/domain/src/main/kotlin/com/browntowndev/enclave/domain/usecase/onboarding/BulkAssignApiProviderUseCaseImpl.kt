package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import javax.inject.Inject

class BulkAssignApiProviderUseCaseImpl
    @Inject
    constructor(
        private val saveApiCredentialsUseCase: SaveApiCredentialsUseCase,
        private val saveApiModelConfigurationUseCase: SaveApiModelConfigurationUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
    ) : BulkAssignApiProviderUseCase {
        override suspend fun invoke(
            credentials: ApiCredentials,
            apiKey: String,
            modelId: String,
            displayName: String,
            targetSlots: List<ModelType>,
        ): Result<ApiCredentialsId> =
            runCatching {
                val credentialsId = saveApiCredentialsUseCase(credentials, apiKey)

                val config = ApiModelConfiguration(
                    apiCredentialsId = credentialsId,
                    modelId = modelId,
                    displayName = displayName,
                    isMultimodal = false,
                )
                val saveConfigResult = saveApiModelConfigurationUseCase(config)
                val configId = saveConfigResult.getOrThrow()

                for (slot in targetSlots) {
                    setDefaultModelUseCase(
                        modelType = slot,
                        localConfigId = null,
                        apiConfigId = configId,
                    )
                }

                credentialsId
            }
    }
