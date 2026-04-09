package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import javax.inject.Inject

class GetRestorableLocalModelsUseCase @Inject constructor(
    private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
) {
    suspend operator fun invoke(): List<LocalModelAsset> = getLocalModelAssetsUseCase.getSoftDeletedModels()
}
