package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId

/**
 * Ephemeral per-generation model override selected from the Studio model drilldown.
 * Carried on [MediaGenerationRequest]; never persisted to default-model slots
 * (setDefault is never invoked).
 */
sealed interface StudioModelOverride {
    val displayName: String

    /** On-device stable-diffusion pack. Maps to [ImageGenerationTarget.LocalStableDiffusion]. */
    data class Local(
        val configId: LocalModelConfigurationId,
        override val displayName: String,
    ) : StudioModelOverride

    /** API/text-style config (reserved for parity; media-generation API configs route here). */
    data class Api(
        val configId: ApiModelConfigurationId,
        override val displayName: String,
    ) : StudioModelOverride

    /** Remote media provider (fal, etc.). Applies to image/video/music remote slots. */
    data class MediaProvider(
        val providerId: MediaProviderId,
        override val displayName: String,
    ) : StudioModelOverride
}
