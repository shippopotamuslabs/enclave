package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface DeleteApiModelConfigurationUseCase {
    suspend operator fun invoke(
        configurationId: Long,
        replacementLocalConfigId: Long? = null,
        replacementApiConfigId: Long? = null
    ): Result<Unit>

    suspend fun getModelTypesNeedingReassignment(configurationId: Long): List<ModelType>
}

class DeleteApiModelConfigurationUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
) : DeleteApiModelConfigurationUseCase {
    override suspend fun invoke(
        configurationId: Long,
        replacementLocalConfigId: Long?,
        replacementApiConfigId: Long?
    ): Result<Unit> {
        return Result.runCatching {
            val needingReassignment = getModelTypesNeedingReassignment(configurationId)

            if (needingReassignment.isNotEmpty()) {
                require(replacementLocalConfigId != null || replacementApiConfigId != null) {
                    "Reassignment is required but no replacement was provided"
                }

                needingReassignment.forEach { modelType ->
                    defaultModelRepository.setDefault(
                        modelType = modelType,
                        localConfigId = replacementLocalConfigId,
                        apiConfigId = replacementApiConfigId
                    )
                }
            }

            transactionProvider.runInTransaction {
                apiModelRepository.deleteConfiguration(configurationId)
            }
        }
    }

    override suspend fun getModelTypesNeedingReassignment(configurationId: Long): List<ModelType> {
        return defaultModelRepository.observeDefaults()
            .first()
            .filter { it.apiConfigId == configurationId }
            .map { it.modelType }
    }
}