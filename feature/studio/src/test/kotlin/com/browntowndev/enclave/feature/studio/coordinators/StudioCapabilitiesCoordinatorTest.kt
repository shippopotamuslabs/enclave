package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioCapabilitiesCoordinatorTest {

    private val coordinator = StudioCapabilitiesCoordinator()

    @Test
    fun `STU-007 switching SDXL to SD15 clamps stale resolution and clears SDXL step selection`() =
        runTest {
            val settingsFlow: MutableStateFlow<GenerationSettings> = MutableStateFlow(
                ImageGenerationSettings(
                    imageResolution = "1024x1024",
                    diffusionSteps = 24
                )
            )
            val sd15Caps = ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedImageResolutions = listOf("512x512"),
                supportedImageSteps = emptyList(),
                defaultImageStep = 20,
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false
            )

            coordinator.observe(
                scope = this,
                settings = settingsFlow,
                capabilitiesFlow = flowOf(sd15Caps)
            )

            val updated = settingsFlow.value as ImageGenerationSettings
            assertEquals("512x512", updated.imageResolution)
            assertNull(updated.diffusionSteps)
        }

    @Test
    fun `STU-008 switching SD15 to SDXL clamps stale resolution and steps to the SDXL defaults`() =
        runTest {
            val settingsFlow: MutableStateFlow<GenerationSettings> = MutableStateFlow(
                ImageGenerationSettings(
                    imageResolution = "512x512",
                    diffusionSteps = null
                )
            )
            val sdxlCaps = ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedImageResolutions = listOf("1024x1024"),
                supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                defaultImageStep = 16,
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false
            )

            coordinator.observe(
                scope = this,
                settings = settingsFlow,
                capabilitiesFlow = flowOf(sdxlCaps)
            )

            val updated = settingsFlow.value as ImageGenerationSettings
            assertEquals("1024x1024", updated.imageResolution)
            assertEquals(16, updated.diffusionSteps)
        }
}
