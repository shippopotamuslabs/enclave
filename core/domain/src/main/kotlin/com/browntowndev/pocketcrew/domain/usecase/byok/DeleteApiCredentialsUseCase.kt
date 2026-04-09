package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface DeleteApiCredentialsUseCase {
    suspend operator fun invoke(
        id: Long,
        replacementLocalConfigId: LocalModelConfigurationId? = null,
        replacementApiConfigId: ApiModelConfigurationId? = null
    ): Result<Unit>
    
    suspend fun getModelTypesNeedingReassignment(id: Long): List<ModelType>
    
    suspend fun isLastModel(id: Long): Boolean
}

class DeleteApiCredentialsUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val localModelRepository: LocalModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
) : DeleteApiCredentialsUseCase {
    override suspend fun invoke(
        id: Long,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?
    ): Result<Unit> {
        return Result.runCatching {
            if (isLastModel(id)) {
                throw IllegalStateException("Cannot delete the last model. At least one local or API model must remain.")
            }

            val needingReassignment = getModelTypesNeedingReassignment(id)

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
                apiModelRepository.deleteCredentials(id)
            }
        }
    }

    override suspend fun getModelTypesNeedingReassignment(id: Long): List<ModelType> {
        val modelConfigs = apiModelRepository.getConfigurationsForCredentials(id)
        val modelConfigIds = modelConfigs.map { it.id }.toSet()

        if (modelConfigIds.isEmpty()) return emptyList()

        return defaultModelRepository.observeDefaults()
            .first()
            .filter { default -> default.apiConfigId != null && default.apiConfigId in modelConfigIds }
            .map { it.modelType }
    }

    override suspend fun isLastModel(id: Long): Boolean {
        val registeredModels = localModelRepository.getAllLocalAssets()
        val allApiCredentials = apiModelRepository.getAllCredentials()
        
        val hasLocalModels = registeredModels.isNotEmpty()
        val hasOnlyOneApiModel = allApiCredentials.size == 1
        val thisIsTheOnlyApiModel = allApiCredentials.any { it.id == id }

        return hasOnlyOneApiModel && thisIsTheOnlyApiModel && !hasLocalModels
    }
}