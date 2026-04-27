package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SetDefaultModelUseCase {
    suspend operator fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null
    )
}

class SetDefaultModelUseCaseImpl @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val localModelRepository: LocalModelRepositoryPort,
    private val apiModelRepository: ApiModelRepositoryPort,
) : SetDefaultModelUseCase {
    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?
    ) {
        if (modelType == ModelType.VISION) {
            require(localConfigId == null) {
                "Vision slot is API-only."
            }
            val configId = apiConfigId ?: throw IllegalArgumentException("Vision slot requires an API model assignment.")
            val config = apiModelRepository.getConfigurationById(configId)
                ?: throw IllegalArgumentException("Vision slot requires a valid API model configuration.")
            val credentials = apiModelRepository.getCredentialsById(config.apiCredentialsId)
                ?: throw IllegalArgumentException("Vision slot requires a valid API model provider.")
            require(credentials.isMultimodal) {
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

        if (modelType == ModelType.IMAGE_GENERATION || modelType == ModelType.VIDEO_GENERATION || modelType == ModelType.MUSIC_GENERATION) {
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
