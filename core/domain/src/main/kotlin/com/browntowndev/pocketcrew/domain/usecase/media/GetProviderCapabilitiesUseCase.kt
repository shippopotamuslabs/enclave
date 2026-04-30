package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import javax.inject.Inject

class GetProviderCapabilitiesUseCase @Inject constructor() {
    operator fun invoke(providerId: String?): ProviderCapabilities {
        if (providerId.isNullOrBlank()) {
            return ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsReferenceImage = false,
                supportsVideo = false,
                supportsMusic = false
            )
        }

        val id = providerId.lowercase()
        return when {
            id.contains("openai") -> ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE, AspectRatio.NINE_SIXTEEN),
                supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
                supportedVideoQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
                supportedVideoResolutions = listOf("480p", "720p", "1080p"),
                supportedVideoDurations = listOf(4, 8, 12),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = false
            )
            id.contains("google") -> ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.NINE_SIXTEEN, AspectRatio.SIXTEEN_NINE),
                supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.ULTRA),
                supportedVideoQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
                supportedVideoResolutions = listOf("720p", "1080p", "4k"),
                supportedVideoDurations = listOf(5, 6, 8),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = false
            )
            id.contains("xai") || id.contains("grok") -> ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.THREE_FOUR, AspectRatio.FOUR_THREE, AspectRatio.NINE_SIXTEEN, AspectRatio.SIXTEEN_NINE, AspectRatio.TWO_THREE, AspectRatio.THREE_TWO),
                supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
                supportedVideoQualities = listOf(GenerationQuality.QUALITY),
                supportedVideoResolutions = listOf("720p", "1080p"),
                supportedVideoDurations = listOf(5),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = false
            )
            else -> ProviderCapabilities(
                supportedAspectRatios = AspectRatio.entries.toList(),
                supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY, GenerationQuality.HD),
                supportedVideoQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY, GenerationQuality.HD),
                supportedVideoResolutions = listOf("480p", "720p", "1080p"),
                supportedVideoDurations = listOf(5, 10, 15),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = true
            )
        }
    }
}
