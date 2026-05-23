package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject

interface SetDefaultModelUseCase {
    suspend operator fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null,
    )
}

class SetDefaultModelUseCaseImpl
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val apiModelRepository: ApiModelRepositoryPort,
    ) : SetDefaultModelUseCase {
        override suspend fun invoke(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
        ) {
            if (modelType == ModelType.VISION) {
                require(localConfigId == null) {
                    "Vision slot is API-only."
                }
                val configId = apiConfigId ?: throw IllegalArgumentException("Vision slot requires an API model assignment.")
                val config =
                    apiModelRepository.getConfigurationById(configId)
                        ?: throw IllegalArgumentException("Vision slot requires a valid API model configuration.")
                require(config.isMultimodal) {
                    "Vision slot requires a multimodal API model."
                }
            }

            if (modelType == ModelType.TTS) {
                require(localConfigId == null && apiConfigId == null && mediaProviderId == null) {
                    "TTS slot is TTS-only."
                }
                requireNotNull(ttsProviderId) {
                    "TTS slot requires a TTS provider assignment."
                }
            }

            if (modelType == ModelType.IMAGE_GENERATION ||
                modelType == ModelType.VIDEO_GENERATION ||
                modelType == ModelType.MUSIC_GENERATION
            ) {
                require(localConfigId == null && apiConfigId == null && ttsProviderId == null) {
                    "Media generation slots are Media-only."
                }
                requireNotNull(mediaProviderId) {
                    "Media generation slot requires a media provider assignment."
                }
            }

            defaultModelRepository.setDefault(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId)
        }
    }
