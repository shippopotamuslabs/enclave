package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetProviderCapabilitiesUseCaseTest {
    private val useCase = GetProviderCapabilitiesUseCase()

    @Test
    fun invoke_xaiProvider_supportsSpeedAndQualityImageModes() {
        val capabilities = useCase(ApiProvider.XAI.name)

        assertEquals(
            listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
            capabilities.supportedImageQualities,
        )
    }

    @Test
    fun invoke_openAiProvider_exposesSoraVideoControls() {
        val capabilities = useCase(ApiProvider.OPENAI.name)

        assertEquals(listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE, AspectRatio.NINE_SIXTEEN), capabilities.supportedAspectRatios)
        assertEquals(listOf("480p", "720p", "1080p"), capabilities.supportedVideoResolutions)
        assertEquals(listOf(4, 8, 12), capabilities.supportedVideoDurations)
        assertEquals(true, capabilities.supportsReferenceImage)
        assertEquals(true, capabilities.supportsVideo)
    }
}
