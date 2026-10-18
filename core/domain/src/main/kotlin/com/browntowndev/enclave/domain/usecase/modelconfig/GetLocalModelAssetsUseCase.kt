package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetLocalModelAssetsUseCase {
    open operator fun invoke(): Flow<List<LocalModelAsset>>

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
