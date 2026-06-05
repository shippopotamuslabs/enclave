package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import javax.inject.Inject

private val CHAT_SLOT_TYPES = setOf(ModelType.MAIN, ModelType.THINKING)

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
        private val localModelRepository: LocalModelRepositoryPort,
        private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
        private val gpuCapabilityPort: GpuCapabilityPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
    ) : SetDefaultModelUseCase {
        companion object {
            const val PACK_NOT_COMPLETE_MESSAGE =
                "Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning."
        }

        override suspend fun invoke(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
        ) {
            if (modelType in CHAT_SLOT_TYPES && apiConfigId != null) {
                val config =
                    apiModelRepository.getConfigurationById(apiConfigId)
                        ?: throw IllegalArgumentException("Chat slot requires a valid API model configuration.")
                val credentials =
                    apiModelRepository.getCredentialsById(config.apiCredentialsId)
                        ?: throw IllegalArgumentException("Chat slot requires valid API credentials.")
                require(credentials.provider.supportsChat) {
                    "'${credentials.provider.name}' does not support chat."
                }
            }

            if (modelType == ModelType.VISION) {
                require(localConfigId == null) {
                    "Vision slot is API-only."
                }
                val configId = apiConfigId ?: throw IllegalArgumentException("Vision slot requires an API model assignment.")
                val config =
                    apiModelRepository.getConfigurationById(configId)
                        ?: throw IllegalArgumentException("Vision slot requires a valid API model configuration.")
                val credentials =
                    apiModelRepository.getCredentialsById(config.apiCredentialsId)
                        ?: throw IllegalArgumentException("Vision slot requires valid API credentials.")
                require(credentials.provider.supportsChat) {
                    "'${credentials.provider.name}' does not support vision."
                }
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

            if (modelType == ModelType.IMAGE_GENERATION) {
                // Image generation supports either a remote media provider or a local
                // on-device diffusion model (e.g. bundled SD 1.5 GGUF).
                require(apiConfigId == null && ttsProviderId == null) {
                    "Image generation slot accepts a local model or a media provider."
                }
                require(localConfigId != null || mediaProviderId != null) {
                    "Image generation slot requires a local model or a media provider assignment."
                }
                require(localConfigId == null || mediaProviderId == null) {
                    "Image generation slot cannot assign both a local model and a media provider."
                }
                if (localConfigId != null) {
                    require(gpuCapabilityPort.isLocalImageGenerationSupported()) {
                        LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
                    }
                    val asset =
                        localModelRepository.getAssetByConfigId(localConfigId)
                            ?: throw IllegalArgumentException(
                                "Image generation slot requires a registered local model configuration.",
                            )
                    require(LocalStableDiffusionModelPolicy.supports(asset)) {
                        "Image generation slot requires a supported local diffusion model."
                    }
                    requireImageGenerationPackComplete(asset)
                }
            }

            if (modelType == ModelType.VIDEO_GENERATION ||
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

        private suspend fun requireImageGenerationPackComplete(asset: com.browntowndev.enclave.domain.model.config.LocalModelAsset) {
            val catalog =
                resolveModelCatalog().getOrElse {
                    throw IllegalArgumentException("Unable to verify model pack download status.")
                }
            val packId =
                catalog.packs
                    .firstOrNull { pack ->
                        pack.members.any { member ->
                            member.role == ModelPackRole.DIFFUSION &&
                                member.asset.metadata.sha256
                                    .equals(asset.metadata.sha256, ignoreCase = true)
                        }
                    }?.packId
                    ?: return
            val completeness =
                verifyModelPackCompleteness(packId).getOrElse {
                    throw IllegalArgumentException("Unable to verify model pack download status.")
                }
            require(completeness.isComplete) { PACK_NOT_COMPLETE_MESSAGE }
        }
    }
