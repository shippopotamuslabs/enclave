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
    ) {
        suspend operator fun invoke(target: ModelDeletionTarget): PreparedModelDeletion =
            when (target) {
                is ModelDeletionTarget.LocalModelAsset -> prepareLocalModelAsset(target.id)
                is ModelDeletionTarget.LocalModelPreset -> prepareLocalModelPreset(target.id)
                is ModelDeletionTarget.ApiProvider -> prepareApiProvider(target.id)
                is ModelDeletionTarget.ApiPreset -> prepareApiPreset(target.id)
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

        private fun buildReassignmentCandidates(
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            excludeLocalModelId: LocalModelId? = null,
            excludeLocalConfigId: LocalModelConfigurationId? = null,
            excludeApiCredentialsId: ApiCredentialsId? = null,
            excludeApiConfigId: ApiModelConfigurationId? = null,
            requireVisionCompatibility: Boolean,
            requireApiVisionOnly: Boolean,
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

            return candidates
        }
    }
