package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PrepareModelDeletionUseCase
    @Inject
    constructor(
        private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
        private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
        private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
        private val deleteLocalModelUseCase: DeleteLocalModelUseCase,
        private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase,
        private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
        private val getMediaProvidersUseCase: GetMediaProvidersUseCase,
    ) {
        suspend operator fun invoke(target: ModelDeletionTarget): PreparedModelDeletion =
            when (target) {
                is ModelDeletionTarget.LocalModelAsset -> prepareLocalModelAsset(target.id)
                is ModelDeletionTarget.LocalModelPreset -> prepareLocalModelPreset(target.id)
                is ModelDeletionTarget.ApiProvider -> prepareApiProvider(target.id)
                is ModelDeletionTarget.ApiPreset -> prepareApiPreset(target.id)
                is ModelDeletionTarget.ModelPack -> prepareModelPack(target.packId)
            }

        private suspend fun prepareModelPack(packId: String): PreparedModelDeletion {
            val catalog = resolveModelCatalog().getOrNull() ?: return PreparedModelDeletion.Ready(ModelDeletionTarget.ModelPack(packId))
            val pack = catalog.packById(packId) ?: return PreparedModelDeletion.Ready(ModelDeletionTarget.ModelPack(packId))
            val diffusionId =
                pack.members
                    .firstOrNull { it.role == ModelPackRole.DIFFUSION }
                    ?.asset
                    ?.metadata
                    ?.id
                    ?: return PreparedModelDeletion.Ready(ModelDeletionTarget.ModelPack(packId))
            return when (val prepared = prepareLocalModelAsset(diffusionId)) {
                PreparedModelDeletion.BlockedLastModel -> prepared
                is PreparedModelDeletion.Ready ->
                    PreparedModelDeletion.Ready(
                        target = ModelDeletionTarget.ModelPack(packId),
                        modelTypesNeedingReassignment = prepared.modelTypesNeedingReassignment,
                        reassignmentCandidates = prepared.reassignmentCandidates,
                    )
            }
        }

        private suspend fun prepareLocalModelAsset(id: LocalModelId): PreparedModelDeletion {
            if (deleteLocalModelUseCase.isLastModel(id)) {
                return PreparedModelDeletion.BlockedLastModel
            }

            val localAssets = getLocalModelAssetsUseCase().first()
            val apiAssets = getApiModelAssetsUseCase().first()
            val needingReassignment = deleteLocalModelUseCase.getModelTypesNeedingReassignment(id)
            if (needingReassignment.isEmpty()) {
                return PreparedModelDeletion.Ready(target = ModelDeletionTarget.LocalModelAsset(id))
            }

            val deletedAsset = localAssets.find { it.metadata.id == id }
            val requiresApiVisionOnly = ModelType.VISION in needingReassignment
            val requiresVisionCompatibility =
                requiresApiVisionOnly || deletedAsset?.metadata?.isMultimodal == true
            return PreparedModelDeletion.Ready(
                target = ModelDeletionTarget.LocalModelAsset(id),
                modelTypesNeedingReassignment = needingReassignment,
                reassignmentCandidates =
                    buildReassignmentCandidates(
                        localAssets = localAssets,
                        apiAssets = apiAssets,
                        excludeLocalModelId = id,
                        requireVisionCompatibility = requiresVisionCompatibility,
                        requireApiVisionOnly = requiresApiVisionOnly,
                        modelTypesNeedingReassignment = needingReassignment,
                    ),
            )
        }

        private suspend fun prepareLocalModelPreset(id: LocalModelConfigurationId): PreparedModelDeletion {
            val defaults = getDefaultModelsUseCase().first()
            val needingReassignment = defaults.filter { it.localConfigId == id }.map { it.modelType }
            if (needingReassignment.isEmpty()) {
                return PreparedModelDeletion.Ready(target = ModelDeletionTarget.LocalModelPreset(id))
            }

            val localAssets = getLocalModelAssetsUseCase().first()
            val apiAssets = getApiModelAssetsUseCase().first()
            val deletedAsset = localAssets.find { asset -> asset.configurations.any { it.id == id } }
            val requiresApiVisionOnly = ModelType.VISION in needingReassignment
            val requiresVisionCompatibility =
                requiresApiVisionOnly || deletedAsset?.metadata?.isMultimodal == true
            return PreparedModelDeletion.Ready(
                target = ModelDeletionTarget.LocalModelPreset(id),
                modelTypesNeedingReassignment = needingReassignment,
                reassignmentCandidates =
                    buildReassignmentCandidates(
                        localAssets = localAssets,
                        apiAssets = apiAssets,
                        excludeLocalConfigId = id,
                        requireVisionCompatibility = requiresVisionCompatibility,
                        requireApiVisionOnly = requiresApiVisionOnly,
                        modelTypesNeedingReassignment = needingReassignment,
                    ),
            )
        }

        private suspend fun prepareApiProvider(id: ApiCredentialsId): PreparedModelDeletion {
            if (deleteApiCredentialsUseCase.isLastModel(id)) {
                return PreparedModelDeletion.BlockedLastModel
            }

            val needingReassignment = deleteApiCredentialsUseCase.getModelTypesNeedingReassignment(id)
            if (needingReassignment.isEmpty()) {
                return PreparedModelDeletion.Ready(target = ModelDeletionTarget.ApiProvider(id))
            }

            return PreparedModelDeletion.Ready(
                target = ModelDeletionTarget.ApiProvider(id),
                modelTypesNeedingReassignment = needingReassignment,
                reassignmentCandidates =
                    buildReassignmentCandidates(
                        localAssets = getLocalModelAssetsUseCase().first(),
                        apiAssets = getApiModelAssetsUseCase().first(),
                        excludeApiCredentialsId = id,
                        requireVisionCompatibility = false,
                        requireApiVisionOnly = false,
                    ),
            )
        }

        private suspend fun prepareApiPreset(id: ApiModelConfigurationId): PreparedModelDeletion {
            val needingReassignment = deleteApiModelConfigurationUseCase.getModelTypesNeedingReassignment(id)
            if (needingReassignment.isEmpty()) {
                return PreparedModelDeletion.Ready(target = ModelDeletionTarget.ApiPreset(id))
            }

            val apiAssets = getApiModelAssetsUseCase().first()
            val deletedPreset = apiAssets.flatMap { it.configurations }.find { it.id == id }
            val requiresApiVisionOnly = ModelType.VISION in needingReassignment
            val requiresVisionCompatibility =
                requiresApiVisionOnly || deletedPreset?.isMultimodal == true

            return PreparedModelDeletion.Ready(
                target = ModelDeletionTarget.ApiPreset(id),
                modelTypesNeedingReassignment = needingReassignment,
                reassignmentCandidates =
                    buildReassignmentCandidates(
                        localAssets = getLocalModelAssetsUseCase().first(),
                        apiAssets = apiAssets,
                        excludeApiConfigId = id,
                        requireVisionCompatibility = requiresVisionCompatibility,
                        requireApiVisionOnly = requiresApiVisionOnly,
                    ),
            )
        }

        private suspend fun buildReassignmentCandidates(
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            excludeLocalModelId: LocalModelId? = null,
            excludeLocalConfigId: LocalModelConfigurationId? = null,
            excludeApiCredentialsId: ApiCredentialsId? = null,
            excludeApiConfigId: ApiModelConfigurationId? = null,
            requireVisionCompatibility: Boolean,
            requireApiVisionOnly: Boolean,
            modelTypesNeedingReassignment: List<ModelType> = emptyList(),
        ): List<ReassignmentCandidate> {
            val candidates = mutableListOf<ReassignmentCandidate>()

            if (!requireApiVisionOnly) {
                localAssets.forEach { asset ->
                    if (asset.metadata.id == excludeLocalModelId) return@forEach
                    if (requireVisionCompatibility && !asset.metadata.isMultimodal) return@forEach

                    asset.configurations.forEach { config ->
                        if (config.id == excludeLocalConfigId) return@forEach
                        candidates.add(
                            ReassignmentCandidate(
                                configId = config.id,
                                source = ModelSource.ON_DEVICE,
                                assetDisplayName = asset.metadata.displayName,
                                configDisplayName = config.displayName,
                                localModelId = asset.metadata.id,
                            ),
                        )
                    }
                }
            }

            apiAssets.forEach { asset ->
                if (asset.credentials.id == excludeApiCredentialsId) return@forEach

                asset.configurations.forEach { config ->
                    if (config.id == excludeApiConfigId) return@forEach
                    if (requireVisionCompatibility && !config.isMultimodal) return@forEach
                    candidates.add(
                        ReassignmentCandidate(
                            configId = config.id,
                            source = ModelSource.API,
                            assetDisplayName = asset.credentials.displayName,
                            configDisplayName = config.displayName,
                            providerName = asset.credentials.provider.name,
                            apiCredentialsId = asset.credentials.id,
                        ),
                    )
                }
            }

            if (ModelType.IMAGE_GENERATION in modelTypesNeedingReassignment) {
                val mediaProviders = getMediaProvidersUseCase().first()
                mediaProviders
                    .filter { it.capability == MediaCapability.IMAGE }
                    .forEach { provider ->
                        candidates.add(
                            ReassignmentCandidate(
                                mediaProviderId = provider.id,
                                source = ModelSource.API,
                                assetDisplayName = provider.displayName,
                                configDisplayName = provider.modelName ?: provider.capability.displayName,
                                providerName = provider.provider.name,
                            ),
                        )
                    }
            }

            return candidates
        }
    }
