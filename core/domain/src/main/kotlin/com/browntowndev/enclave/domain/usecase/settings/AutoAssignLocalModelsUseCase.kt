package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Ensures every [ModelType] slot ends up referencing an entity that still exists. Runs on
 * logout so guest mode keeps working with whatever local/API/TTS/media assets remain on
 * the device. Slots with a still-valid assignment are untouched; dangling or empty slots
 * are filled from the first available candidate, preferring local configs over API for
 * text slots. Slots with no viable candidate are left as-is.
 */
open class AutoAssignLocalModelsUseCase
    @Inject
    constructor(
        private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
        private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
        private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
        private val getTtsProvidersUseCase: GetTtsProvidersUseCase,
        private val getMediaProvidersUseCase: GetMediaProvidersUseCase,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
    ) {
        open suspend operator fun invoke() {
            val currentAssignments = getDefaultModelsUseCase().first().associateBy { it.modelType }
            val localAssets = getLocalModelAssetsUseCase().first()
            val apiAssets = getApiModelAssetsUseCase().first()
            val ttsProviders = getTtsProvidersUseCase().first()
            val mediaProviders = getMediaProvidersUseCase().first()
            val capabilities = capabilityPort.getCapabilities()

            ModelType.entries
                .filter { it != ModelType.UNASSIGNED }
                .forEach { modelType ->
                    val existing = currentAssignments[modelType]
                    if (
                        existing != null &&
                        isAssignmentValid(
                            existing,
                            localAssets,
                            apiAssets,
                            ttsProviders,
                            mediaProviders,
                            capabilities,
                        )
                    ) {
                        return@forEach
                    }
                    val candidate =
                        pickCandidate(
                            modelType,
                            localAssets,
                            apiAssets,
                            ttsProviders,
                            mediaProviders,
                            capabilities,
                        ) ?: return@forEach
                    setDefaultModelUseCase(
                        modelType = modelType,
                        localConfigId = candidate.localConfigId,
                        apiConfigId = candidate.apiConfigId,
                        ttsProviderId = candidate.ttsProviderId,
                        mediaProviderId = candidate.mediaProviderId,
                    )
                }
        }

        private fun isAssignmentValid(
            assignment: DefaultModelAssignment,
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            ttsProviders: List<TtsProviderAsset>,
            mediaProviders: List<MediaProviderAsset>,
            capabilities: com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities,
        ): Boolean {
            val localExists =
                assignment.localConfigId?.let { id ->
                    localAssets.any { asset -> asset.configurations.any { it.id == id } } &&
                        isLocalImageConfigEligible(assignment.modelType, id, localAssets, capabilities)
                } ?: false
            val apiExists =
                assignment.apiConfigId?.let { id ->
                    apiAssets.any { asset -> asset.configurations.any { it.id == id } }
                } ?: false
            val ttsExists = assignment.ttsProviderId?.let { id -> ttsProviders.any { it.id == id } } ?: false
            val mediaExists = assignment.mediaProviderId?.let { id -> mediaProviders.any { it.id == id } } ?: false
            val localImageGenerationDisabled =
                assignment.modelType == ModelType.IMAGE_GENERATION &&
                    assignment.localConfigId != null &&
                    !LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
            if (localImageGenerationDisabled) {
                return false
            }
            return localExists || apiExists || ttsExists || mediaExists
        }

        private fun pickCandidate(
            modelType: ModelType,
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            ttsProviders: List<TtsProviderAsset>,
            mediaProviders: List<MediaProviderAsset>,
            capabilities: com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities,
        ): Candidate? =
            when (modelType) {
                ModelType.VISION -> firstMultimodalApi(apiAssets)?.let { Candidate(apiConfigId = it) }
                ModelType.TTS -> ttsProviders.firstOrNull()?.id?.let { Candidate(ttsProviderId = it) }
                ModelType.IMAGE_GENERATION ->
                    firstLocalImageGenerationConfig(localAssets, capabilities)?.let { Candidate(localConfigId = it) }
                        ?: mediaProviders.firstOrNull { it.capability == MediaCapability.IMAGE }?.id?.let {
                            Candidate(mediaProviderId = it)
                        }
                ModelType.IMAGE_EDIT ->
                    firstLocalImageEditConfig(localAssets, capabilities)?.let { Candidate(localConfigId = it) }
                        ?: mediaProviders.firstOrNull { it.capability == MediaCapability.IMAGE }?.id?.let {
                            Candidate(mediaProviderId = it)
                        }
                ModelType.VIDEO_GENERATION, ModelType.IMAGE_TO_VIDEO ->
                    mediaProviders.firstOrNull { it.capability == MediaCapability.VIDEO }?.id?.let {
                        Candidate(mediaProviderId = it)
                    }
                ModelType.MUSIC_GENERATION ->
                    mediaProviders.firstOrNull { it.capability == MediaCapability.MUSIC }?.id?.let {
                        Candidate(mediaProviderId = it)
                    }
                ModelType.UNASSIGNED -> null
                else -> firstTextCandidate(localAssets, apiAssets)
            }

        private fun firstTextCandidate(
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
        ): Candidate? {
            val localId =
                localAssets
                    .firstOrNull()
                    ?.configurations
                    ?.firstOrNull()
                    ?.id
            if (localId != null) return Candidate(localConfigId = localId)
            val apiId =
                apiAssets
                    .asSequence()
                    .flatMap { it.configurations.asSequence() }
                    .firstOrNull()
                    ?.id
            return apiId?.let { Candidate(apiConfigId = it) }
        }

        private fun firstLocalImageGenerationConfig(
            localAssets: List<LocalModelAsset>,
            capabilities: com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities,
        ): LocalModelConfigurationId? {
            if (!LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED) {
                return null
            }
            // Prefer the highest-priority installable model (SDXL over SD1.5) for the slot.
            return localAssets
                .asSequence()
                .flatMap { asset -> asset.configurations.asSequence().map { asset to it } }
                .filter { (_, config) ->
                    ModelType.IMAGE_GENERATION in config.defaultAssignments &&
                        isLocalImageConfigEligible(
                            ModelType.IMAGE_GENERATION,
                            config.id,
                            localAssets,
                            capabilities,
                        )
                }.maxByOrNull { (asset, _) ->
                    LocalImageGenerationModelRegistry.imageGenerationSlotPriority(
                        LocalImageGenerationModelRegistry.descriptorFor(asset)?.id,
                    )
                }?.second
                ?.id
        }

        private fun firstLocalImageEditConfig(
            localAssets: List<LocalModelAsset>,
            capabilities: com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities,
        ): LocalModelConfigurationId? {
            if (!LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED) {
                return null
            }
            return localAssets
                .asSequence()
                .flatMap { it.configurations.asSequence() }
                .firstOrNull { config ->
                    ModelType.IMAGE_EDIT in config.defaultAssignments &&
                        isLocalImageConfigEligible(
                            ModelType.IMAGE_EDIT,
                            config.id,
                            localAssets,
                            capabilities,
                        )
                }?.id
        }

        private fun isLocalImageConfigEligible(
            modelType: ModelType,
            configId: LocalModelConfigurationId,
            localAssets: List<LocalModelAsset>,
            capabilities: com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities,
        ): Boolean {
            if (modelType != ModelType.IMAGE_GENERATION && modelType != ModelType.IMAGE_EDIT) {
                return true
            }
            val asset =
                localAssets.firstOrNull { candidate ->
                    candidate.configurations.any { it.id == configId }
                } ?: return false
            val modelId = LocalImageGenerationModelRegistry.descriptorFor(asset)?.id ?: return true
            return when (modelId) {
                LocalImageGenerationModelId.SD15_QNN ->
                    capabilities.sd15NpuSupported == LocalImageGenTriState.YES
                LocalImageGenerationModelId.SD15_CPU ->
                    capabilities.sd15CpuSupported == LocalImageGenTriState.YES
                LocalImageGenerationModelId.SDXL_QNN ->
                    capabilities.sdxlNpuSupported == LocalImageGenTriState.YES
                LocalImageGenerationModelId.JUGGERNAUT_XL_QNN ->
                    capabilities.juggernautXlNpuSupported == LocalImageGenTriState.YES
            }
        }

        private fun firstMultimodalApi(apiAssets: List<ApiModelAsset>): ApiModelConfigurationId? =
            apiAssets
                .asSequence()
                .flatMap { it.configurations.asSequence() }
                .firstOrNull { it.isMultimodal }
                ?.id

        private data class Candidate(
            val localConfigId: LocalModelConfigurationId? = null,
            val apiConfigId: ApiModelConfigurationId? = null,
            val ttsProviderId: TtsProviderId? = null,
            val mediaProviderId: MediaProviderId? = null,
        )
    }
