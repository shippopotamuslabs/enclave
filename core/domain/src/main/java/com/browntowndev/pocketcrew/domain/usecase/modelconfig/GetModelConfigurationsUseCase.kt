package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.config.toUi
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case to get model configurations as a flow.
 * Follows 2026 Compose best practices for consuming flows in ViewModels.
 */
class GetModelConfigurationsUseCase @Inject constructor(
    private val modelRegistry: ModelRegistryPort
) {
    /**
     * Returns a Flow of all model configurations for UI consumption.
     */
    operator fun invoke(): Flow<List<ModelConfigurationUi>> {
        return modelRegistry.observeRegisteredModels().map { typeToNameMap ->
            typeToNameMap.keys.mapNotNull { modelType ->
                modelRegistry.getRegisteredModel(modelType)?.toUi()
            }
        }
    }

    /**
     * Returns a Flow of a single model configuration for UI consumption.
     */
    fun observeSingle(modelType: ModelType): Flow<ModelConfigurationUi?> {
        return modelRegistry.observeModel(modelType).map { config ->
            config?.toUi()
        }
    }
}
