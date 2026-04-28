package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
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
}
