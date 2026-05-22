package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StudioGenerationCoordinatorTest {
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val dispatcher = mockk<StudioGenerationDispatcherPort>(relaxed = true)
    private val ephemeralMediaFlow = MutableStateFlow(emptyList<com.browntowndev.enclave.domain.port.repository.StudioMediaAsset>())
    private val generationErrorFlow = MutableStateFlow<String?>(null)

    private lateinit var coordinator: StudioGenerationCoordinator

    @BeforeEach
    fun setup() {
        every { studioRepository.observeEphemeralMedia() } returns ephemeralMediaFlow
        every { studioRepository.observeGenerationError() } returns generationErrorFlow
        coordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
    }

    @Test
    fun beginGenerationAttempt_startsDispatcherAndMarksGenerating() {
        coordinator.beginGenerationAttempt(
            submittedPrompt = "prompt",
            mediaType = MediaCapability.IMAGE,
            continualMode = false,
        )

        assertTrue(coordinator.isGenerating.value)
        verify { dispatcher.startGeneration("prompt", MediaCapability.IMAGE) }
    }

    @Test
    fun stopGeneration_resetsContinualGenerationActive() {
        coordinator.beginGenerationAttempt("prompt", MediaCapability.IMAGE, continualMode = true)
        coordinator.stopGeneration()

        assertFalse(coordinator.isContinualGenerationActive.value)
    }
}
