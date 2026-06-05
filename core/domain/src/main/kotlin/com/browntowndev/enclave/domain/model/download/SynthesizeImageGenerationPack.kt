package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy

/**
 * Builds a single IMAGE_GENERATION pack from local registry rows when remote catalog is unavailable.
 * Used only as a fallback; member metadata (SHA/size) comes from registry, not remote config.
 */
object SynthesizeImageGenerationPack {
    const val FALLBACK_PACK_ID = "sd15-t2i"

    fun fromLocalAssets(localAssets: List<LocalModelAsset>): ModelPack? {
        val diffusion = localAssets.firstOrNull { LocalStableDiffusionModelPolicy.supports(it) } ?: return null
        return ModelPack(
            packId = FALLBACK_PACK_ID,
            displayName = diffusion.metadata.displayName.ifBlank { "Stable Diffusion 1.5" },
            slot = ModelType.IMAGE_GENERATION,
            members = listOf(ModelPackMember(ModelPackRole.DIFFUSION, diffusion)),
        )
    }
}
