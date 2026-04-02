package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetLocalModelAssetsUseCase {
    operator fun invoke(): Flow<List<LocalModelAsset>>
    suspend fun getSoftDeletedModels(): List<LocalModelAsset>
}

class GetLocalModelAssetsUseCaseImpl @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
) : GetLocalModelAssetsUseCase {
    override fun invoke(): Flow<List<LocalModelAsset>> {
        return modelRegistry.observeAssets()
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
        return modelRegistry.getSoftDeletedModels()
    }
}