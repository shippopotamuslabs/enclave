package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MediaGenerationRequestMapperOverrideTest {
    @Test
    @DisplayName("S04-3 — fromStudio threads override into the built request")
    fun s04_fromStudioThreadsOverride() {
        val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")

        val request =
            MediaGenerationRequestMapper.fromStudio(
                prompt = "prompt",
                capability = MediaCapability.IMAGE,
                settings = ImageGenerationSettings(),
                override = override,
            )

        assertEquals(override, request.override)
    }

    @Test
    @DisplayName("S04-3b — fromStudio without override leaves request.override null")
    fun s04_fromStudioWithoutOverride() {
        val request =
            MediaGenerationRequestMapper.fromStudio(
                prompt = "prompt",
                capability = MediaCapability.IMAGE,
                settings = ImageGenerationSettings(),
            )

        assertNull(request.override)
    }
}
