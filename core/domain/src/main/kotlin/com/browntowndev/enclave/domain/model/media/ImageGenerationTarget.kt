package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId

sealed interface ImageGenerationTarget {
    data class Remote(
        val providerAssetId: MediaProviderId,
        val providerDisplayName: String,
        val providerCapabilities: ProviderCapabilities? = null,
    ) : ImageGenerationTarget

    /**
     * On-device diffusion (QNN NPU or MNN CPU). [modelDir] holds the installed package directory.
     */
    data class LocalStableDiffusion(
        val localConfigId: LocalModelConfigurationId,
        val displayName: String,
        val modelId: LocalImageGenerationModelId,
        val modelDir: String,
        val catalogSha256: String,
        val selectedBackend: LocalImageGenerationBackendKind,
        val capabilities: ProviderCapabilities,
        val deviceCapabilities: LocalImageGenerationCapabilities,
    ) : ImageGenerationTarget
}

object LocalStableDiffusionModelPolicy {
    fun supports(asset: LocalModelAsset): Boolean = LocalImageGenerationModelRegistry.descriptorFor(asset) != null
}
