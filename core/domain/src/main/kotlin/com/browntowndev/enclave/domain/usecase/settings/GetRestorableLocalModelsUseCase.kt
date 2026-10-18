package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import javax.inject.Inject

open class GetRestorableLocalModelsUseCase
    @Inject
    constructor(
        private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
    ) {
        open suspend operator fun invoke(): List<LocalModelAsset> = getLocalModelAssetsUseCase.getSoftDeletedModels()
    }
