package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.port.download.ModelFileScannerPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import javax.inject.Inject

open class DeleteModelPackUseCase
    @Inject
    constructor(
        private val deleteLocalModel: DeleteLocalModelUseCase,
        private val utilityModelRepository: UtilityModelRepositoryPort,
        private val modelFileScanner: ModelFileScannerPort,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
    ) {
        open suspend operator fun invoke(
            packId: String,
            replacementLocalConfigId: LocalModelConfigurationId? = null,
            replacementApiConfigId: ApiModelConfigurationId? = null,
            replacementMediaProviderId: MediaProviderId? = null,
        ): Result<Unit> =
            runCatching {
                val catalog = resolveModelCatalog().getOrThrow()
                val pack =
                    catalog.packById(packId)
                        ?: throw IllegalArgumentException("Unknown model pack: $packId")

                val diffusion =
                    pack.members.firstOrNull { it.role == ModelPackRole.DIFFUSION }
                        ?: throw IllegalStateException("Pack $packId is missing diffusion member")
                deleteLocalModel(
                    modelId = diffusion.asset.metadata.id,
                    replacementLocalConfigId = replacementLocalConfigId,
                    replacementApiConfigId = replacementApiConfigId,
                    replacementMediaProviderId = replacementMediaProviderId,
                )

                pack.members
                    .filter { it.role != ModelPackRole.DIFFUSION }
                    .forEach { member ->
                        val utilityType =
                            member.asset.metadata.utilityType
                                ?: return@forEach
                        utilityModelRepository.getByUtilityType(utilityType)?.let { metadata ->
                            utilityModelRepository.softDelete(metadata.id)
                        }
                    }

                pack.members.forEach { member ->
                    modelFileScanner.deleteModelFileByLocalFileName(member.asset.metadata.localFileName)
                }
            }
    }
