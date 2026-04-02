package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface GetApiModelAssetsUseCase {
    operator fun invoke(): Flow<List<ApiModelAsset>>
}

class GetApiModelAssetsUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
) : GetApiModelAssetsUseCase {
    override fun invoke(): Flow<List<ApiModelAsset>> {
        return combine(
            apiModelRepository.observeAllCredentials(),
            apiModelRepository.observeAllConfigurations()
        ) { credentials, allConfigs ->
            credentials.map { creds ->
                ApiModelAsset(
                    credentials = creds,
                    configurations = allConfigs.filter { it.apiCredentialsId == creds.id }
                )
            }
        }
    }
}