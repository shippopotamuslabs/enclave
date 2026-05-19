package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetLocalModelAssetsUseCase {
    operator fun invoke(): Flow<List<LocalModelAsset>>

    suspend fun getSoftDeletedModels(): List<LocalModelAsset>
}

class GetLocalModelAssetsUseCaseImpl
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) : GetLocalModelAssetsUseCase {
        override fun invoke(): Flow<List<LocalModelAsset>> = localModelRepository.observeAllLocalAssets()

        override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = localModelRepository.getSoftDeletedModels()
    }
