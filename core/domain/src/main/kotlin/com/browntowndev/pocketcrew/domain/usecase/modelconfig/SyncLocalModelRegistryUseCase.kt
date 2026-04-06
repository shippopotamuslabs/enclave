package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject

class SyncLocalModelRegistryUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
    private val logger: LoggingPort
) {
    suspend operator fun invoke(
        modelType: ModelType,
        asset: LocalModelAsset
    ): SlotResolvedLocalModel {
        return transactionProvider.runInTransaction {
            val assetId = localModelRepository.upsertLocalAsset(asset)
            val primaryConfig = asset.configurations.firstOrNull()
                ?: throw IllegalArgumentException("Asset must contain at least one configuration")
            
            // Check if there is an existing assignment for this model type
            val existingAssignment = defaultModelRepository.getDefault(modelType)
            var reusableConfigId: Long? = null
            
            if (existingAssignment?.localConfigId != null) {
                val existingAsset = localModelRepository.getAssetByConfigId(existingAssignment.localConfigId)
                if (existingAsset?.metadata?.id == assetId) {
                    reusableConfigId = existingAssignment.localConfigId
                }
            }
            
            if (reusableConfigId == null) {
                reusableConfigId = findMatchingConfigId(assetId, primaryConfig)
            }
            
            val configToPersist = primaryConfig.copy(
                id = reusableConfigId ?: 0L,
                localModelId = assetId
            )
            
            logger.debug(
                "SyncLocalModelRegistryUseCase",
                "syncLocalModelRegistry($modelType): assetId=$assetId sha=${asset.metadata.sha256} " +
                    "file=${asset.metadata.localFileName} preset=${primaryConfig.displayName} " +
                    "thinking=${primaryConfig.thinkingEnabled} reusedConfigId=${reusableConfigId ?: 0L}"
            )
            
            val configId = localModelRepository.upsertLocalConfiguration(configToPersist)
            
            if (existingAssignment == null) {
                defaultModelRepository.setDefault(modelType, localConfigId = configId, apiConfigId = null)
            }
            
            val finalAsset = localModelRepository.getAssetById(assetId)
                ?: throw IllegalStateException("Failed to load asset after activation")
            val finalConfig = finalAsset.configurations.find { it.id == configId }
                ?: throw IllegalStateException("Failed to load config after activation")
                
            SlotResolvedLocalModel(modelType, finalAsset, finalConfig)
        }
    }

    private suspend fun findMatchingConfigId(
        assetId: Long,
        desiredConfig: LocalModelConfiguration
    ): Long? {
        val asset = localModelRepository.getAssetById(assetId) ?: return null
        return asset.configurations
            .firstOrNull { entity ->
                entity.displayName == desiredConfig.displayName &&
                    entity.temperature == desiredConfig.temperature &&
                    entity.topK == (desiredConfig.topK ?: 40) &&
                    entity.topP == desiredConfig.topP &&
                    entity.minP == desiredConfig.minP &&
                    entity.repetitionPenalty == desiredConfig.repetitionPenalty &&
                    entity.maxTokens == desiredConfig.maxTokens &&
                    entity.contextWindow == desiredConfig.contextWindow &&
                    entity.thinkingEnabled == desiredConfig.thinkingEnabled &&
                    entity.systemPrompt == desiredConfig.systemPrompt &&
                    entity.isSystemPreset == desiredConfig.isSystemPreset
            }
            ?.id
    }
}
