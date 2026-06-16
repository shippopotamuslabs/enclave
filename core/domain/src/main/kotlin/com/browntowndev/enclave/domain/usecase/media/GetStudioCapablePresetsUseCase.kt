package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy
import com.browntowndev.enclave.domain.model.media.studioCapability
import com.browntowndev.enclave.domain.model.media.studioRequiredMediaTask
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class StudioCapablePresets(
    val localAssets: List<LocalModelAsset>,
    val mediaAssets: List<MediaProviderAsset>,
)

class GetStudioCapablePresetsUseCase
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
    ) {
        /** Emits whenever any of the three catalogs change, filtered for [modelType]. */
        operator fun invoke(modelType: ModelType): Flow<StudioCapablePresets> =
            combine(
                localModelRepository.observeAllLocalAssets(),
                mediaProviderRepository.getMediaProviders(),
            ) { localAssets, mediaProviders ->
                StudioCapablePresets(
                    localAssets = filterLocalAssets(localAssets, modelType),
                    mediaAssets = filterMediaProviders(mediaProviders, modelType),
                )
            }

        /**
         * On-device diffusion packs serve image slots only (IMAGE_GENERATION / IMAGE_EDIT).
         * Video and music slots never surface local assets.
         */
        private fun filterLocalAssets(
            assets: List<LocalModelAsset>,
            modelType: ModelType,
        ): List<LocalModelAsset> {
            if (modelType != ModelType.IMAGE_GENERATION && modelType != ModelType.IMAGE_EDIT) {
                return emptyList()
            }
            return assets.filter { asset ->
                asset.configurations.isNotEmpty() &&
                    asset.metadata.utilityType == null &&
                    LocalStableDiffusionModelPolicy.supports(asset)
            }
        }

        /**
         * Filter providers by the slot's capability and the relevant [MediaTask] (if any).
         * MUSIC_GENERATION has no associated task, so all MUSIC-capable providers are included.
         */
        private fun filterMediaProviders(
            providers: List<MediaProviderAsset>,
            modelType: ModelType,
        ): List<MediaProviderAsset> {
            val capability = modelType.studioCapability
            val requiredTask = modelType.studioRequiredMediaTask()
            return providers.filter { provider ->
                provider.capability == capability &&
                    (requiredTask == null || provider.tasks.contains(requiredTask))
            }
        }
    }
