package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ChatCapablePresets(
    val localAssets: List<LocalModelAsset>,
    val apiAssets: List<ApiModelAsset>,
)

class GetChatCapablePresetsUseCase
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
        private val apiModelRepository: ApiModelRepositoryPort,
    ) {
        /** Emits whenever local or API model catalogs change. */
        operator fun invoke(): Flow<ChatCapablePresets> =
            combine(
                localModelRepository.observeAllLocalAssets(),
                apiModelRepository.observeAllCredentials(),
                apiModelRepository.observeAllConfigurations(),
            ) { localAssets, credentials, apiConfigs ->
                ChatCapablePresets(
                    localAssets = filterLocalAssets(localAssets),
                    apiAssets = groupApiAssets(credentials, apiConfigs),
                )
            }

        private fun filterLocalAssets(assets: List<LocalModelAsset>): List<LocalModelAsset> =
            assets.filter { asset ->
                asset.configurations.isNotEmpty() &&
                    asset.metadata.utilityType == null
            }

        private fun groupApiAssets(
            credentials: List<com.browntowndev.enclave.domain.model.config.ApiCredentials>,
            configs: List<com.browntowndev.enclave.domain.model.config.ApiModelConfiguration>,
        ): List<ApiModelAsset> =
            credentials.mapNotNull { cred ->
                val credConfigs = configs.filter { it.apiCredentialsId == cred.id }
                if (credConfigs.isNotEmpty()) {
                    ApiModelAsset(credentials = cred, configurations = credConfigs)
                } else {
                    null
                }
            }
    }
