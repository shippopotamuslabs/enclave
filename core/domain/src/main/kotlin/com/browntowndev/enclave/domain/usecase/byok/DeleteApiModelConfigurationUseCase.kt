package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface DeleteApiModelConfigurationUseCase {
    suspend operator fun invoke(
        configurationId: ApiModelConfigurationId,
        replacementLocalConfigId: LocalModelConfigurationId? = null,
        replacementApiConfigId: ApiModelConfigurationId? = null,
    ): Result<Unit>

    suspend fun getModelTypesNeedingReassignment(configurationId: ApiModelConfigurationId): List<ModelType>
}

class DeleteApiModelConfigurationUseCaseImpl
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val transactionProvider: TransactionProvider,
    ) : DeleteApiModelConfigurationUseCase {
        override suspend fun invoke(
            configurationId: ApiModelConfigurationId,
            replacementLocalConfigId: LocalModelConfigurationId?,
            replacementApiConfigId: ApiModelConfigurationId?,
        ): Result<Unit> =
            Result.runCatching {
                val needingReassignment = getModelTypesNeedingReassignment(configurationId)

                if (needingReassignment.isNotEmpty()) {
                    require(replacementLocalConfigId != null || replacementApiConfigId != null) {
                        "Reassignment is required but no replacement was provided"
                    }

                    needingReassignment.forEach { modelType ->
                        defaultModelRepository.setDefault(
                            modelType = modelType,
                            localConfigId = replacementLocalConfigId,
                            apiConfigId = replacementApiConfigId,
                        )
                    }
                }

                transactionProvider.runInTransaction {
                    apiModelRepository.deleteConfiguration(configurationId)
                }
            }

        override suspend fun getModelTypesNeedingReassignment(configurationId: ApiModelConfigurationId): List<ModelType> =
            defaultModelRepository
                .observeDefaults()
                .first()
                .filter { it.apiConfigId == configurationId }
                .map { it.modelType }
    }
