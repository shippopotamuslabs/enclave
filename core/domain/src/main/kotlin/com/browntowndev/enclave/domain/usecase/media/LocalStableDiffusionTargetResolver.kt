package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import java.io.File
import javax.inject.Inject

/**
 * Resolves a local stable-diffusion assignment into a concrete on-disk launch directory and
 * [ImageGenerationTarget.LocalStableDiffusion].
 */
class LocalStableDiffusionTargetResolver
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
        private val utilityModelFilePort: UtilityModelFilePort,
        private val gpuCapabilityPort: GpuCapabilityPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
        private val selectBackend: SelectLocalImageGenerationBackendUseCase,
    ) {
        suspend fun resolve(localConfigId: LocalModelConfigurationId): ImageGenerationTarget.LocalStableDiffusion {
            check(gpuCapabilityPort.isLocalImageGenerationSupported()) {
                LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
            }

            val asset = requireNotNull(localModelRepository.getAssetByConfigId(localConfigId)) { NOT_DOWNLOADED }
            require(LocalImageGenerationModelPolicy.supports(asset)) { NOT_COMPATIBLE }

            val descriptor =
                requireNotNull(LocalImageGenerationModelRegistry.descriptorFor(asset)) { NOT_COMPATIBLE }

            val modelFilePath =
                requireNotNull(utilityModelFilePort.resolveLocalModelPath(asset.metadata)) { NOT_DOWNLOADED }

            val deviceCapabilities = capabilityPort.getCapabilities()
            val selection =
                selectBackend(
                    family = descriptor.family,
                    capabilities = deviceCapabilities,
                    requestedModelId = descriptor.id,
                )
            check(selection.backend != LocalImageGenerationBackendKind.NONE) {
                selection.reasons.joinToString("; ").ifBlank { LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE }
            }

            val effectiveModelDir =
                when (selection.backend) {
                    LocalImageGenerationBackendKind.QNN_NPU ->
                        LocalImageGenerationPaths.resolveQnnLaunchDir(
                            resolvedStoragePath = modelFilePath,
                            family = descriptor.family,
                            relativePackSubdir = descriptor.relativeModelDir,
                        )
                    LocalImageGenerationBackendKind.CPU_MNN -> {
                        val modelsDir =
                            File(modelFilePath).parentFile?.absolutePath ?: modelFilePath
                        LocalImageGenerationPaths.resolveMnnModelDir(modelsDir)
                    }
                    else -> modelFilePath
                }

            val effectiveModelId =
                selection.modelId
                    ?: descriptor.id

            return ImageGenerationTarget.LocalStableDiffusion(
                localConfigId = localConfigId,
                displayName = asset.displayLabel(),
                modelId = effectiveModelId,
                modelDir = effectiveModelDir,
                catalogSha256 = asset.metadata.sha256,
                selectedBackend = selection.backend,
                capabilities = LocalImageGenerationModelPolicy.providerCapabilitiesFor(effectiveModelId),
                deviceCapabilities = deviceCapabilities,
            )
        }

        suspend fun resolveLaunchDir(localConfigId: LocalModelConfigurationId): String =
            resolve(localConfigId).modelDir

        private fun LocalModelAsset.displayLabel(): String =
            metadata.displayName.takeIf { it.isNotBlank() }
                ?: configurations.firstOrNull()?.displayName.orEmpty()

        companion object {
            const val NOT_DOWNLOADED = "Image generation model is not fully downloaded."
            const val NOT_COMPATIBLE = "Assigned local model is not compatible with image generation."
        }
    }
