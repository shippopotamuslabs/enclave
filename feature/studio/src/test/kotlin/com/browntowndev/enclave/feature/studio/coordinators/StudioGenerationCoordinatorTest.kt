package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.feature.studio.StudioGenerationErrorMessages
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class StudioGenerationCoordinatorTest {
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val dispatcher = mockk<StudioGenerationDispatcherPort>(relaxed = true)
    private val ephemeralMediaFlow = MutableStateFlow(emptyList<com.browntowndev.enclave.domain.port.repository.StudioMediaAsset>())
    private val generationErrorFlow = MutableStateFlow<String?>(null)
    private val generationProgressFlow = MutableStateFlow<ImageGenerationProgress?>(null)
    private val activeGenerationFlow = MutableStateFlow<ActiveStudioGeneration?>(null)

    private lateinit var coordinator: StudioGenerationCoordinator

    @BeforeEach
    fun setup() {
        every { studioRepository.observeEphemeralMedia() } returns ephemeralMediaFlow
        every { studioRepository.observeGenerationError() } returns generationErrorFlow
        every { studioRepository.observeGenerationProgress() } returns generationProgressFlow
        every { studioRepository.observeActiveGeneration() } returns activeGenerationFlow
        every { studioRepository.reportActiveGeneration(any()) } answers {
            if (activeGenerationFlow.value != null) {
                false
            } else {
                activeGenerationFlow.value = firstArg()
                true
            }
        }
        every { studioRepository.clearActiveGeneration() } answers {
            activeGenerationFlow.value = null
        }
        coordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
    }

    @Test
    fun beginGenerationAttempt_startsDispatcherAndMarksGenerating() =
        runTest {
            coordinator.startObservers(this)
            coordinator.beginGenerationAttempt(
                submittedPrompt = "prompt",
                mediaType = MediaCapability.IMAGE,
                continualMode = false,
                settings = ImageGenerationSettings(),
            )
            advanceUntilIdle()

            assertTrue(coordinator.isGenerating.value)
            assertEquals("prompt", activeGenerationFlow.value?.prompt)
            verify { dispatcher.startGeneration("prompt", MediaCapability.IMAGE, ImageGenerationSettings(), any()) }

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun startObservers_activeGeneration_rehydratesGeneratingState() =
        runTest {
            val activeGeneration =
                ActiveStudioGeneration(
                    id = "gen-1",
                    prompt = "in progress",
                    mediaType = MediaCapability.VIDEO,
                    settings = VideoGenerationSettings(),
                )
            activeGenerationFlow.value = activeGeneration
            var restoredGeneration: ActiveStudioGeneration? = null

            coordinator.startObservers(this) { restoredGeneration = it }
            advanceUntilIdle()

            assertTrue(coordinator.isGenerating.value)
            assertEquals("in progress", coordinator.lastSubmittedPrompt.value)
            assertEquals(activeGeneration, restoredGeneration)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun beginGenerationAttempt_activeGeneration_rejectsDuplicateBeforeDispatch() {
        activeGenerationFlow.value =
            ActiveStudioGeneration(
                id = "existing-gen",
                prompt = "first",
                mediaType = MediaCapability.IMAGE,
                settings = ImageGenerationSettings(),
            )

        coordinator.beginGenerationAttempt(
            submittedPrompt = "second",
            mediaType = MediaCapability.IMAGE,
            continualMode = false,
            settings = ImageGenerationSettings(),
        )

        assertEquals("Another media generation is already in progress.", coordinator.error.value)
        verify(exactly = 0) { dispatcher.startGeneration(any(), any(), any(), any()) }
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
                any(),
            )
        }
        val settings = settingsSlot.captured as VideoGenerationSettings
        assertEquals("file://reference.jpg", settings.referenceImageUri)
        assertEquals(VideoGenerationState.Loading("source-1"), coordinator.videoGenerationState.value)
    }

    @Test
    fun `ST2 continual generation continues until stopGeneration`() =
        runTest {
            coordinator.startObservers(this)
            coordinator.beginGenerationAttempt(
                submittedPrompt = "continual prompt",
                mediaType = MediaCapability.IMAGE,
                continualMode = true,
                settings = ImageGenerationSettings(),
            )
            advanceUntilIdle()
            assertTrue(coordinator.isContinualGenerationActive.value)
            assertTrue(coordinator.isGenerating.value)
            verify(exactly = 1) { dispatcher.startGeneration(any(), any(), any(), any()) }

            activeGenerationFlow.value = null
            advanceUntilIdle()
            assertFalse(coordinator.isGenerating.value)
            assertTrue(coordinator.isContinualGenerationActive.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-1", MediaCapability.IMAGE)
            verify(exactly = 2) { dispatcher.startGeneration(any(), any(), any(), any()) }
            advanceUntilIdle()
            assertTrue(coordinator.isGenerating.value)

            activeGenerationFlow.value = null
            advanceUntilIdle()
            assertFalse(coordinator.isGenerating.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-2", MediaCapability.IMAGE)
            verify(exactly = 3) { dispatcher.startGeneration(any(), any(), any(), any()) }

            coordinator.stopGeneration()
            assertFalse(coordinator.isContinualGenerationActive.value)
            assertFalse(coordinator.isGenerating.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-3", MediaCapability.IMAGE)
            verify(exactly = 3) { dispatcher.startGeneration(any(), any(), any(), any()) }

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun observeGenerationError_mapsFalPolicyFailureForSnackbar() =
        runTest {
            coordinator.startObservers(this)
            coordinator.startDetailVideoGeneration(
                StudioMediaAsset(
                    id = "source-1",
                    localUri = "file://reference.jpg",
                    prompt = "Animate",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                ),
            )

            generationErrorFlow.value =
                "fal video generation failed: could not fetch queue result from " +
                "https://queue.fal.run/requests/1 " +
                "(fal queue result failed: 422 Output video has sensitive content.)"
            advanceUntilIdle()

            val expected = StudioGenerationErrorMessages.toSnackbarMessage(generationErrorFlow.value)
            assertEquals(expected, coordinator.error.value)
            val videoError = coordinator.videoGenerationState.value as VideoGenerationState.Error
            assertEquals(expected, videoError.message)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun observeGenerationProgress_mapsDomainEventsToPresentationLabels() =
        runTest {
            coordinator.startObservers(this)

            generationProgressFlow.value = ImageGenerationProgress.LoadingModel
            advanceUntilIdle()
            assertEquals("Loading model", coordinator.generationProgressLabel.value)

            generationProgressFlow.value = ImageGenerationProgress.SavingOutput
            advanceUntilIdle()
            assertEquals("Saving output", coordinator.generationProgressLabel.value)

            generationProgressFlow.value = null
            advanceUntilIdle()
            assertEquals(null, coordinator.generationProgressLabel.value)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
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
