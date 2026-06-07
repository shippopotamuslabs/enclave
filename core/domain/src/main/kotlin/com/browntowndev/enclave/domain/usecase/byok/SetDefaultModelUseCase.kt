package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilityPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import java.io.File
import javax.inject.Inject

private val CHAT_SLOT_TYPES = setOf(ModelType.MAIN, ModelType.THINKING)

interface SetDefaultModelUseCase {
    suspend operator fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null,
        liveVoiceProviderId: LiveVoiceProviderId? = null,
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
        private val localStableDiffusionTargetResolver: LocalStableDiffusionTargetResolver,
    ) : SetDefaultModelUseCase {
        companion object {
            const val PACK_NOT_COMPLETE_MESSAGE =
                "Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning."
            const val IMG2IMG_ENCODER_MISSING_MESSAGE =
                "Local image edit requires vae_encoder.bin in the model pack."
        }

        override suspend fun invoke(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
            liveVoiceProviderId: LiveVoiceProviderId?,
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
                require(localConfigId == null && apiConfigId == null && mediaProviderId == null && liveVoiceProviderId == null) {
                    "TTS slot is TTS-only."
                }
                requireNotNull(ttsProviderId) {
                    "TTS slot requires a TTS provider assignment."
                }
            }

            if (modelType == ModelType.LIVE_VOICE) {
                require(localConfigId == null && apiConfigId == null && ttsProviderId == null && mediaProviderId == null) {
                    "Live Voice slot is LIVE_VOICE-only."
                }
                requireNotNull(liveVoiceProviderId) {
                    "Live Voice slot requires a Live Voice provider assignment."
                }
            }

            if (modelType == ModelType.IMAGE_GENERATION) {
                validateLocalOrMediaImageSlot(
                    slotLabel = "Image generation",
                    localConfigId = localConfigId,
                    apiConfigId = apiConfigId,
                    ttsProviderId = ttsProviderId,
                    mediaProviderId = mediaProviderId,
                    requireImg2ImgEncoder = false,
                )
            }

            if (modelType == ModelType.IMAGE_EDIT) {
                validateLocalOrMediaImageSlot(
                    slotLabel = "Image edit",
                    localConfigId = localConfigId,
                    apiConfigId = apiConfigId,
                    ttsProviderId = ttsProviderId,
                    mediaProviderId = mediaProviderId,
                    requireImg2ImgEncoder = true,
                )
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

            defaultModelRepository.setDefault(
                modelType,
                localConfigId,
                apiConfigId,
                ttsProviderId,
                mediaProviderId,
                liveVoiceProviderId,
            )
        }

        private suspend fun validateLocalOrMediaImageSlot(
            slotLabel: String,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
            requireImg2ImgEncoder: Boolean,
        ) {
            require(apiConfigId == null && ttsProviderId == null) {
                "$slotLabel slot accepts a local model or a media provider."
            }
            require(localConfigId != null || mediaProviderId != null) {
                "$slotLabel slot requires a local model or a media provider assignment."
            }
            require(localConfigId == null || mediaProviderId == null) {
                "$slotLabel slot cannot assign both a local model and a media provider."
            }
            if (localConfigId != null) {
                require(gpuCapabilityPort.isLocalImageGenerationSupported()) {
                    LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
                }
                val asset =
                    localModelRepository.getAssetByConfigId(localConfigId)
                        ?: throw IllegalArgumentException(
                            "$slotLabel slot requires a registered local model configuration.",
                        )
                require(LocalStableDiffusionModelPolicy.supports(asset)) {
                    "$slotLabel slot requires a supported local diffusion model."
                }
                requireLocalImageModelAssignmentAllowed(asset)
                requireImageGenerationPackComplete(asset)
                if (requireImg2ImgEncoder) {
                    val launchDir = localStableDiffusionTargetResolver.resolveLaunchDir(localConfigId)
                    require(LocalImageGenerationPaths.hasImg2ImgCapability(File(launchDir))) {
                        IMG2IMG_ENCODER_MISSING_MESSAGE
                    }
                }
            }
        }

        private suspend fun requireImageGenerationPackComplete(asset: com.browntowndev.enclave.domain.model.config.LocalModelAsset) {
            val packId =
                LocalImageGenerationModelRegistry.descriptorFor(asset)?.packId
                    ?: run {
                        val catalog =
                            resolveModelCatalog().getOrElse {
                                throw IllegalArgumentException("Unable to verify model pack download status.")
                            }
                        catalog.packs
                            .firstOrNull { pack ->
                                pack.members.any { member ->
                                    member.role == ModelPackRole.DIFFUSION &&
                                        member.asset.metadata.sha256
                                            .equals(asset.metadata.sha256, ignoreCase = true)
                                }
                            }?.packId
                    }
                    ?: return
            val completeness =
                verifyModelPackCompleteness(packId).getOrElse {
                    throw IllegalArgumentException("Unable to verify model pack download status.")
                }
            require(completeness.isComplete) { PACK_NOT_COMPLETE_MESSAGE }
        }

        private fun requireLocalImageModelAssignmentAllowed(asset: com.browntowndev.enclave.domain.model.config.LocalModelAsset) {
            val modelId = LocalImageGenerationModelRegistry.descriptorFor(asset)?.id ?: return
            if (modelId != LocalImageGenerationModelId.SDXL_QNN &&
                modelId != LocalImageGenerationModelId.JUGGERNAUT_XL_QNN
            ) {
                return
            }
            val capabilities = capabilityPort.getCapabilities()
            val supported =
                when (modelId) {
                    LocalImageGenerationModelId.SDXL_QNN ->
                        capabilities.sdxlNpuSupported == LocalImageGenTriState.YES
                    LocalImageGenerationModelId.JUGGERNAUT_XL_QNN ->
                        capabilities.juggernautXlNpuSupported == LocalImageGenTriState.YES
                    else -> true
                }
            if (!supported) {
                val reason =
                    capabilities.disabledReasonsByModelId[modelId]
                        ?: LocalImageGenerationCapabilityPolicy.SDXL_BLOCKED_REASON
                throw IllegalArgumentException(reason)
            }
        }
    }
