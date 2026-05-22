package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.feature.studio.VideoGenerationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
            settings = ImageGenerationSettings(),
        )

        assertTrue(coordinator.isGenerating.value)
        verify { dispatcher.startGeneration("prompt", MediaCapability.IMAGE, ImageGenerationSettings()) }
    }

    @Test
    fun stopGeneration_resetsContinualGenerationActive() {
        coordinator.beginGenerationAttempt("prompt", MediaCapability.IMAGE, continualMode = true, settings = ImageGenerationSettings())
        coordinator.stopGeneration()

        assertFalse(coordinator.isContinualGenerationActive.value)
    }

    @Test
    fun startDetailVideoGeneration_passesReferenceImageUri_andSetsLoadingState() {
        val sourceAsset =
            StudioMediaAsset(
                id = "source-1",
                localUri = "file://reference.jpg",
                prompt = "Animate this",
                mediaType = MediaCapability.IMAGE.name,
                createdAt = 1L,
            )
        val settingsSlot = slot<GenerationSettings>()

        coordinator.startDetailVideoGeneration(sourceAsset)

        verify {
            dispatcher.startGeneration(
                "Animate this",
                MediaCapability.VIDEO,
                capture(settingsSlot),
            )
        }
        val settings = settingsSlot.captured as VideoGenerationSettings
        assertEquals("file://reference.jpg", settings.referenceImageUri)
        assertEquals(VideoGenerationState.Loading("source-1"), coordinator.videoGenerationState.value)
    }

    @Test
    fun ephemeralMediaUpdate_afterDetailVideoGen_setsSuccessState() =
        runTest {
            val sourceAsset =
                StudioMediaAsset(
                    id = "source-1",
                    localUri = "file://reference.jpg",
                    prompt = "Animate this",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                )
            val generatedVideo =
                StudioMediaAsset(
                    id = "video-1",
                    localUri = "file://generated.mp4",
                    prompt = "Animate this",
                    mediaType = MediaCapability.VIDEO.name,
                    createdAt = 2L,
                )

            coordinator.startObservers(this)
            coordinator.startDetailVideoGeneration(sourceAsset)
            assertEquals(VideoGenerationState.Loading("source-1"), coordinator.videoGenerationState.value)

            ephemeralMediaFlow.value = listOf(sourceAsset, generatedVideo)
            advanceUntilIdle()

            assertEquals(
                VideoGenerationState.Success("source-1", "file://generated.mp4"),
                coordinator.videoGenerationState.value,
            )

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }
}
