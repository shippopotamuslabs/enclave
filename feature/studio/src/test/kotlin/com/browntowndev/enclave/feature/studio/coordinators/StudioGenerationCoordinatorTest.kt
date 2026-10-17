package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.feature.studio.ModerationEvent
import com.browntowndev.enclave.feature.studio.StudioGenerationErrorMessages
import com.browntowndev.enclave.feature.studio.VideoGenerationState
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class StudioGenerationCoordinatorTest {
    private val studioRepository = FakeStudioRepository()
    private val dispatcher = FakeStudioGenerationDispatcher()
    private val ephemeralMediaFlow get() = studioRepository.ephemeralState
    private val generationErrorFlow get() = studioRepository.generationErrorState
    private val generationProgressFlow get() = studioRepository.generationProgressState
    private val activeGenerationFlow get() = studioRepository.activeGenerationState

    private lateinit var coordinator: StudioGenerationCoordinator

    @BeforeEach
    fun setup() {
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
            val started = dispatcher.startedCalls.single()
            assertEquals("prompt", started.prompt)
            assertEquals(MediaCapability.IMAGE, started.mediaType)
            assertEquals(ImageGenerationSettings(), started.settings)

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
        assertTrue(dispatcher.startedCalls.isEmpty())
    }

    @Test
    fun rejectPromptAsModerated_resetsFlagsAndEmitsEventWithoutError() =
        runTest {
            coordinator.startObservers(this)
            coordinator.beginGenerationAttempt(
                submittedPrompt = "test prompt",
                mediaType = MediaCapability.IMAGE,
                continualMode = true,
                settings = ImageGenerationSettings(),
            )
            advanceUntilIdle()
            assertTrue(coordinator.isGenerating.value)

            coordinator.moderationEvents.test {
                coordinator.rejectPromptAsModerated("test prompt")

                val event = awaitItem()
                assertTrue(event is ModerationEvent.Blocked)
                assertEquals("test prompt", (event as ModerationEvent.Blocked).prompt)
                cancelAndIgnoreRemainingEvents()
            }

            assertFalse(coordinator.isGenerating.value)
            assertFalse(coordinator.isContinualGenerationActive.value)
            assertNull(coordinator.error.value)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
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
        coordinator.startDetailVideoGeneration(sourceAsset)

        val started = dispatcher.startedCalls.single()
        assertEquals("Animate this", started.prompt)
        assertEquals(MediaCapability.VIDEO, started.mediaType)
        val settings = started.settings as VideoGenerationSettings
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
            assertEquals(1, dispatcher.startedCalls.size)

            activeGenerationFlow.value = null
            advanceUntilIdle()
            assertFalse(coordinator.isGenerating.value)
            assertTrue(coordinator.isContinualGenerationActive.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-1", MediaCapability.IMAGE)
            assertEquals(2, dispatcher.startedCalls.size)
            advanceUntilIdle()
            assertTrue(coordinator.isGenerating.value)

            activeGenerationFlow.value = null
            advanceUntilIdle()
            assertFalse(coordinator.isGenerating.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-2", MediaCapability.IMAGE)
            assertEquals(3, dispatcher.startedCalls.size)

            coordinator.stopGeneration()
            assertFalse(coordinator.isContinualGenerationActive.value)
            assertFalse(coordinator.isGenerating.value)

            coordinator.onGenerativeScrollThresholdVisible("anchor-3", MediaCapability.IMAGE)
            assertEquals(3, dispatcher.startedCalls.size)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun `continual re-generation forwards the stored override to the dispatcher`() =
        runTest {
            // Use the real coordinator with a recording fake dispatcher (Fakes preferred over MockK).
            val fakeContinualDispatcher = FakeStudioGenerationDispatcher()
            val continualCoordinator = StudioGenerationCoordinator(studioRepository, fakeContinualDispatcher)
            continualCoordinator.startObservers(this)

            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")
            continualCoordinator.beginGenerationAttempt(
                submittedPrompt = "continual prompt",
                mediaType = MediaCapability.IMAGE,
                continualMode = true,
                settings = ImageGenerationSettings(),
                override = override,
            )
            advanceUntilIdle()
            // Sanity: the initial attempt forwarded the override and armed continual mode.
            assertEquals(override, fakeContinualDispatcher.lastStarted?.override)
            assertTrue(continualCoordinator.isContinualGenerationActive.value)
            assertTrue(continualCoordinator.isGenerating.value)

            // Simulate the first generation completing so the continual _isGenerating guard clears.
            activeGenerationFlow.value = null
            advanceUntilIdle()
            assertFalse(continualCoordinator.isGenerating.value)
            assertTrue(continualCoordinator.isContinualGenerationActive.value)

            // Trigger the continual re-generation path (spec §5 task #14).
            continualCoordinator.onGenerativeScrollThresholdVisible("anchor-1", MediaCapability.IMAGE)
            advanceUntilIdle()

            // The continual re-generation must forward the stored override (not null).
            // If the continual path dropped the override, lastStarted.override would be null and this fails.
            assertEquals(override, fakeContinualDispatcher.lastStarted?.override)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun observeGenerationError_mapsFalPolicyFailureAsModerationEvent() =
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

            coordinator.moderationEvents.test {
                generationErrorFlow.value =
                    "fal video generation failed: could not fetch queue result from " +
                    "https://queue.fal.run/requests/1 " +
                    "(fal queue result failed: 422 Output video has sensitive content.)"
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is ModerationEvent.Blocked)
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(coordinator.error.value)
            val videoError = coordinator.videoGenerationState.value as VideoGenerationState.Error
            assertEquals("Media failed moderation.", videoError.message)

            coroutineContext[Job]?.cancelChildren(CancellationException("test complete"))
        }

    @Test
    fun observeGenerationError_mapsNonModerationFailureAsError() =
        runTest {
            coordinator.startObservers(this)

            generationErrorFlow.value = "fal image generation failed: timeout"
            advanceUntilIdle()

            val expected = StudioGenerationErrorMessages.toSnackbarMessage("fal image generation failed: timeout")
            assertEquals(expected, coordinator.error.value)

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
