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
class AutoAssignLocalModelsUseCase
    @Inject
    constructor(
        private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
        private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
        private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
        private val getTtsProvidersUseCase: GetTtsProvidersUseCase,
        private val getMediaProvidersUseCase: GetMediaProvidersUseCase,
    ) {
        suspend operator fun invoke() {
            val currentAssignments = getDefaultModelsUseCase().first().associateBy { it.modelType }
            val localAssets = getLocalModelAssetsUseCase().first()
            val apiAssets = getApiModelAssetsUseCase().first()
            val ttsProviders = getTtsProvidersUseCase().first()
            val mediaProviders = getMediaProvidersUseCase().first()

            ModelType.entries
                .filter { it != ModelType.UNASSIGNED }
                .forEach { modelType ->
                    val existing = currentAssignments[modelType]
                    if (existing != null && isAssignmentValid(existing, localAssets, apiAssets, ttsProviders, mediaProviders)) {
                        return@forEach
                    }
                    val candidate =
                        pickCandidate(modelType, localAssets, apiAssets, ttsProviders, mediaProviders)
                            ?: return@forEach
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
        ): Boolean {
            val localExists =
                assignment.localConfigId?.let { id ->
                    localAssets.any { asset -> asset.configurations.any { it.id == id } }
                } ?: false
            val apiExists =
                assignment.apiConfigId?.let { id ->
                    apiAssets.any { asset -> asset.configurations.any { it.id == id } }
                } ?: false
            val ttsExists = assignment.ttsProviderId?.let { id -> ttsProviders.any { it.id == id } } ?: false
            val mediaExists = assignment.mediaProviderId?.let { id -> mediaProviders.any { it.id == id } } ?: false
            return localExists || apiExists || ttsExists || mediaExists
        }

        private fun pickCandidate(
            modelType: ModelType,
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            ttsProviders: List<TtsProviderAsset>,
            mediaProviders: List<MediaProviderAsset>,
        ): Candidate? =
            when (modelType) {
                ModelType.VISION -> firstMultimodalApi(apiAssets)?.let { Candidate(apiConfigId = it) }
                ModelType.TTS -> ttsProviders.firstOrNull()?.id?.let { Candidate(ttsProviderId = it) }
                ModelType.IMAGE_GENERATION ->
                    mediaProviders.firstOrNull { it.capability == MediaCapability.IMAGE }?.id?.let {
                        Candidate(mediaProviderId = it)
                    }
                ModelType.VIDEO_GENERATION ->
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
