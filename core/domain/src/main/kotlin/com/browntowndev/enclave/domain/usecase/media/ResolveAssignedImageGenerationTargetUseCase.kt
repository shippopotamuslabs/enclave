package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.domain.model.inference.ModelType
import java.io.File
import javax.inject.Inject

class ResolveAssignedImageGenerationTargetUseCase
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val localModelRepository: LocalModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val utilityModelFilePort: UtilityModelFilePort,
        private val gpuCapabilityPort: GpuCapabilityPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
        private val selectBackend: SelectLocalImageGenerationBackendUseCase,
    ) {
        suspend operator fun invoke(): Result<ImageGenerationTarget> =
            runCatching {
                val assignment =
                    defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION)
                        ?: throw IllegalStateException(NO_ASSIGNMENT)

                when {
                    assignment.mediaProviderId != null -> resolveRemote(assignment.mediaProviderId)
                    assignment.localConfigId != null -> resolveLocal(assignment.localConfigId)
                    else -> throw IllegalStateException(NO_ASSIGNMENT)
                }
            }

        private suspend fun resolveRemote(mediaProviderId: MediaProviderId): ImageGenerationTarget {
            val provider =
                mediaProviderRepository.getMediaProvider(mediaProviderId)
                    ?: throw IllegalStateException(NO_ASSIGNMENT)
            return ImageGenerationTarget.Remote(
                providerAssetId = provider.id,
                providerDisplayName = provider.displayName,
            )
        }

        private suspend fun resolveLocal(localConfigId: LocalModelConfigurationId): ImageGenerationTarget {
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
            check(selection.backend != com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind.NONE) {
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

            return ImageGenerationTarget.LocalStableDiffusion(
                localConfigId = localConfigId,
                displayName = asset.displayLabel(),
                modelId = descriptor.id,
                modelDir = effectiveModelDir,
                catalogSha256 = asset.metadata.sha256,
                selectedBackend = selection.backend,
                capabilities = LocalImageGenerationModelPolicy.providerCapabilitiesFor(descriptor.id),
                deviceCapabilities = deviceCapabilities,
            )
        }

        private fun LocalModelAsset.displayLabel(): String =
            metadata.displayName.takeIf { it.isNotBlank() }
                ?: configurations.firstOrNull()?.displayName.orEmpty()

        private companion object {
            const val NO_ASSIGNMENT = "No image generation model assigned."
            const val NOT_DOWNLOADED = "Image generation model is not fully downloaded."
            const val NOT_COMPATIBLE = "Assigned local model is not compatible with image generation."
        }
    }
