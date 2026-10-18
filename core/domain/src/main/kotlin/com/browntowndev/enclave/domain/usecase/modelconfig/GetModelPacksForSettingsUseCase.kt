package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.download.LocalImagePackIds
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendLabels
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
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
    /** SD 1.5 routes to the MNN CPU backend on this device (labels show "(CPU)"). */
    val sd15CpuFallbackActive: Boolean = false,
)

open class GetModelPacksForSettingsUseCase
    @Inject
    constructor(
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
        private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
        private val modelPackVerifier: ModelPackVerifierPort,
        private val localModelRepository: LocalModelRepositoryPort,
        private val utilityModelRepository: UtilityModelRepositoryPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
    ) {
        open suspend operator fun invoke(): List<ModelPackSettingsRow> {
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

            val capabilities = capabilityPort.getCapabilities()
            val sd15CpuFallbackActive = LocalImageGenerationBackendLabels.sd15CpuFallbackActive(capabilities)

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
                val (isEligible, disabledReason) = packEligibility(pack.packId, capabilities)
                val needsDownloadAction =
                    diffusionSoftDeleted ||
                        areUtilityRegistryEntriesAbsent ||
                        !completeness.isComplete
                val displayPack =
                    if (pack.packId == LocalImagePackIds.SD15) {
                        pack.copy(
                            displayName =
                                LocalImageGenerationBackendLabels.sd15DeviceAware(
                                    pack.displayName,
                                    sd15CpuFallbackActive,
                                ),
                        )
                    } else {
                        pack
                    }
                ModelPackSettingsRow(
                    pack = displayPack,
                    completeness = completeness,
                    isDiffusionSoftDeleted = diffusionSoftDeleted,
                    areUtilityRegistryEntriesAbsent = areUtilityRegistryEntriesAbsent,
                    showDownloadAction = isEligible && needsDownloadAction,
                    disabledReason = disabledReason,
                    isEligible = isEligible,
                    sd15CpuFallbackActive = sd15CpuFallbackActive,
                )
            }
        }

        private fun packEligibility(
            packId: String,
            capabilities: LocalImageGenerationCapabilities,
        ): Pair<Boolean, String?> {
            return when (packId) {
                LocalImagePackIds.SDXL -> {
                    val isEligible = capabilities.sdxlNpuSupported == LocalImageGenTriState.YES
                    val disabledReason =
                        if (isEligible) {
                            null
                        } else {
                            capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN]
                                ?: packBlockedReason(capabilities)
                        }
                    isEligible to disabledReason
                }
                LocalImagePackIds.SD15 -> {
                    val isEligible =
                        capabilities.sd15NpuSupported == LocalImageGenTriState.YES ||
                            capabilities.sd15CpuSupported == LocalImageGenTriState.YES
                    val disabledReason = if (isEligible) null else packBlockedReason(capabilities)
                    isEligible to disabledReason
                }
                LocalImagePackIds.JUGGERNAUT -> {
                    val isEligible = capabilities.juggernautXlNpuSupported == LocalImageGenTriState.YES
                    val disabledReason =
                        if (isEligible) {
                            null
                        } else {
                            capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.JUGGERNAUT_XL_QNN]
                                ?: packBlockedReason(capabilities)
                        }
                    isEligible to disabledReason
                }
                else -> true to null
            }
        }

        private fun packBlockedReason(capabilities: LocalImageGenerationCapabilities): String? =
            capabilities.disabledReasonsByModelId.values.firstOrNull()
                ?: capabilities.diagnostics.firstOrNull { diagnostic ->
                    diagnostic.contains("Insufficient RAM") || diagnostic.contains("Requires arm64")
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
