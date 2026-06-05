package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import javax.inject.Inject

data class ModelPackSettingsRow(
    val pack: ModelPack,
    val completeness: ModelPackCompleteness,
    /** Diffusion member has zero configs (user soft-deleted the slot model). */
    val isDiffusionSoftDeleted: Boolean,
    /** Pack utility types have no registry row (never installed or already soft-deleted). */
    val areUtilityRegistryEntriesAbsent: Boolean,
    val showDownloadAction: Boolean,
    val disabledReason: String? = null,
    val isEligible: Boolean = true,
)

class GetModelPacksForSettingsUseCase
    @Inject
    constructor(
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
        private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
        private val modelPackVerifier: ModelPackVerifierPort,
        private val localModelRepository: LocalModelRepositoryPort,
        private val utilityModelRepository: UtilityModelRepositoryPort,
    ) {
        suspend operator fun invoke(): List<ModelPackSettingsRow> {
            if (!LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED) {
                return emptyList()
            }

            val catalog = resolveModelCatalog().getOrNull()
            val packs = catalog?.packs.orEmpty()
            if (packs.isEmpty()) {
                return emptyList()
            }

            val softDeletedDiffusionShas =
                localModelRepository
                    .getSoftDeletedModels()
                    .map { it.metadata.sha256 }
                    .toSet()

            return packs.map { pack ->
                val completeness =
                    if (catalog != null) {
                        verifyModelPackCompleteness(pack.packId).getOrNull()
                    } else {
                        modelPackVerifier.verifyPack(pack)
                    } ?: incompleteCompleteness(pack)
                val diffusionSha =
                    pack.members
                        .firstOrNull { it.role == ModelPackRole.DIFFUSION }
                        ?.asset
                        ?.metadata
                        ?.sha256
                val diffusionSoftDeleted = diffusionSha != null && diffusionSha in softDeletedDiffusionShas
                val areUtilityRegistryEntriesAbsent =
                    pack.members
                        .filter { it.role != ModelPackRole.DIFFUSION }
                        .all { member ->
                            val utilityType = member.asset.metadata.utilityType ?: return@all true
                            utilityModelRepository.getByUtilityType(utilityType) == null
                        }
                ModelPackSettingsRow(
                    pack = pack,
                    completeness = completeness,
                    isDiffusionSoftDeleted = diffusionSoftDeleted,
                    areUtilityRegistryEntriesAbsent = areUtilityRegistryEntriesAbsent,
                    showDownloadAction =
                        diffusionSoftDeleted ||
                            areUtilityRegistryEntriesAbsent ||
                            !completeness.isComplete,
                )
            }
        }

        private fun incompleteCompleteness(pack: ModelPack): ModelPackCompleteness =
            ModelPackCompleteness(
                packId = pack.packId,
                installedCount = 0,
                totalCount = pack.members.size,
                isComplete = false,
                members = emptyList(),
            )
    }
