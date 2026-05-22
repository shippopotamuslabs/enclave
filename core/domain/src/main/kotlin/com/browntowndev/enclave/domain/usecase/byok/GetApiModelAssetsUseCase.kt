package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface GetApiModelAssetsUseCase {
    operator fun invoke(): Flow<List<ApiModelAsset>>
}

class GetApiModelAssetsUseCaseImpl
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
    ) : GetApiModelAssetsUseCase {
        override fun invoke(): Flow<List<ApiModelAsset>> =
            combine(
                apiModelRepository.observeAllCredentials(),
                apiModelRepository.observeAllConfigurations(),
            ) { credentials, allConfigs ->
                credentials.map { creds ->
                    ApiModelAsset(
                        credentials = creds,
                        configurations = allConfigs.filter { it.apiCredentialsId == creds.id },
                    )
                }
            }
    }
