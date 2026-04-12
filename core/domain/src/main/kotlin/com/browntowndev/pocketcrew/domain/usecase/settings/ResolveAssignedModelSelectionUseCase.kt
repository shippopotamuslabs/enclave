package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ResolveAssignedModelSelectionUseCase @Inject constructor(
    private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
    private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
    private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
) {
    suspend operator fun invoke(modelType: ModelType): ResolvedAssignedModelSelection? {
        val assignment = getDefaultModelsUseCase()
            .first()
            .find { it.modelType == modelType }
            ?: return null

        assignment.localConfigId?.let { localConfigId ->
            val asset = getLocalModelAssetsUseCase()
                .first()
                .find { localAsset -> localAsset.configurations.any { it.id == localConfigId } }
                ?: return null
            return ResolvedAssignedModelSelection(
                localAsset = asset,
                localConfig = asset.configurations.find { it.id == localConfigId },
            )
        }

        assignment.apiConfigId?.let { apiConfigId ->
            val asset = getApiModelAssetsUseCase()
                .first()
                .find { apiAsset -> apiAsset.configurations.any { it.id == apiConfigId } }
                ?: return null
            return ResolvedAssignedModelSelection(
                apiAsset = asset,
                apiConfig = asset.configurations.find { it.id == apiConfigId },
            )
        }

        return null
    }
}
