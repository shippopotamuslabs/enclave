package com.browntowndev.enclave.feature.moa.service

import android.app.NotificationManager
import android.content.Intent
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.feature.inference.InferenceEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServiceTest {
    private lateinit var service: InferenceService
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private val inferenceFactory = mockk<InferenceFactoryPort>()
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val inferenceEventBus = mockk<InferenceEventBus>(relaxed = true)
    private val llmInferenceService = mockk<LlmInferencePort>(relaxed = true)
    private val powerManager = mockk<android.os.PowerManager>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private var serviceCompletion = CompletableDeferred<Unit>()

    private val invokedModelTypes = java.util.Collections.synchronizedList(mutableListOf<ModelType>())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        serviceCompletion = CompletableDeferred<Unit>()
        invokedModelTypes.clear()

        service = spyk(InferenceService())

        // Replace serviceScope with test dispatcher so pipeline runs synchronously under test control
        service.serviceScope = CoroutineScope(testDispatcher + SupervisorJob())

        // Inject mocks
        service.notificationManager = notificationManager
        service.logger = logger
        val lazyFactory = mockk<dagger.Lazy<InferenceFactoryPort>>()
        every { lazyFactory.get() } returns inferenceFactory
        service.inferenceFactoryProvider = lazyFactory
        service.activeModelProvider = activeModelProvider
        service.inferenceEventBus = inferenceEventBus

        // Mock PowerManager
        every { service.getSystemService(android.content.Context.POWER_SERVICE) } returns powerManager
        every { powerManager.getThermalHeadroom(any()) } returns 1.0f

        // Mock active model config
        val mockActiveConfig = mockk<com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration>(relaxed = true)
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns mockActiveConfig

        // Stub Service methods
        every { service.startForeground(any(), any(), any()) } returns Unit
        every { service.stopForeground(any<Int>()) } returns Unit
        every { service.stopSelf() } answers {
            serviceCompletion.complete(Unit)
        }
        every { service.stopSelf(any()) } answers {
            serviceCompletion.complete(Unit)
        }

        // Mock internal methods to avoid Android framework calls
        every { service.createCancelPendingIntent() } returns mockk(relaxed = true)
        every { service.createProgressNotification(any(), any(), any()) } returns mockk(relaxed = true)

        // Default inference behavior - track model types via side effect
        coEvery {
            inferenceFactory.withInferenceService(any<ModelType>(), any<suspend (LlmInferencePort) -> Unit>())
        } coAnswers {
            invokedModelTypes.add(firstArg())
            val block = args[1] as suspend (LlmInferencePort) -> Unit
            block(llmInferenceService)
        }

        every {
            llmInferenceService.sendPrompt(any(), options = any(), closeConversation = any())
        } returns flowOf(InferenceEvent.Finished(ModelType.FAST))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("ACTION_START executes full pipeline with all model types")
    fun actionStartExecutesFullPipeline() =
        runTest(testDispatcher) {
            // Given
            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_START
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            // When
            service.onStartCommand(intent, 0, 1)

            // Advance dispatcher to execute the launched pipeline coroutine
            advanceUntilIdle()

            // Then - verify all pipeline steps were invoked
            assertTrue(
                invokedModelTypes.contains(ModelType.DRAFT_ONE),
                "Expected DRAFT_ONE in $invokedModelTypes",
            )
            assertTrue(
                invokedModelTypes.contains(ModelType.DRAFT_TWO),
                "Expected DRAFT_TWO in $invokedModelTypes",
            )
            assertTrue(
                invokedModelTypes.contains(ModelType.MAIN),
                "Expected MAIN in $invokedModelTypes",
            )
            assertTrue(
                invokedModelTypes.contains(ModelType.FINAL_SYNTHESIS),
                "Expected FINAL_SYNTHESIS in $invokedModelTypes",
            )

            verify { service.startForeground(any(), any(), any()) }
        }

    @Test
    @DisplayName("ACTION_STOP cancels pipeline and stops service")
    fun actionStopCancelsPipeline() =
        runTest(testDispatcher) {
            // Given
            val intentStop = mockk<Intent>(relaxed = true)
            every { intentStop.action } returns InferenceService.ACTION_STOP

            // When
            service.onStartCommand(intentStop, 0, 1)

            advanceUntilIdle()

            // Then
            verify { service.stopForeground(any<Int>()) }
            verify { service.stopSelf() }
        }

    @Test
    @DisplayName("Scenario 2.1: Invalid State JSON on Resume stops service")
    fun actionResumeWithInvalidStateJsonStopsService() =
        runTest(testDispatcher) {
            // Given
            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_RESUME
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_STATE_JSON) } returns "invalid-json"

            // When
            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            // Then
            verify { service.stopSelf() }
            assertTrue(invokedModelTypes.isEmpty())
        }

    @Test
    @DisplayName("Scenario 2.2: Concurrency - ACTION_START cancels active pipeline job")
    fun actionStartCancelsActiveJob() =
        runTest(testDispatcher) {
            // Given: First job delays when executing to simulate active generation
            val firstCompletable = CompletableDeferred<Unit>()
            coEvery {
                inferenceFactory.withInferenceService(any<ModelType>(), any<suspend (LlmInferencePort) -> Unit>())
            } coAnswers {
                invokedModelTypes.add(firstArg())
                firstCompletable.await() // block/suspend until cancelled or completed
                val block = args[1] as suspend (LlmInferencePort) -> Unit
                block(llmInferenceService)
            }

            val intent1 = mockk<Intent>(relaxed = true)
            every { intent1.action } returns InferenceService.ACTION_START
            every { intent1.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent1.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            // When: Start first job
            service.onStartCommand(intent1, 0, 1)
            testDispatcher.scheduler.advanceTimeBy(500) // let first job run and block

            // Start second job with new chat ID
            val intent2 = mockk<Intent>(relaxed = true)
            every { intent2.action } returns InferenceService.ACTION_START
            every { intent2.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-456"
            every { intent2.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "world"

            service.onStartCommand(intent2, 0, 2)
            firstCompletable.complete(Unit) // unblock first job (which should be cancelled)
            advanceUntilIdle()

            // Then: verify cancellation and termination of service
            verify { service.stopSelf() }
        }

    @Test
    @DisplayName("Scenario 2.3: Processing state is emitted before model starts")
    fun processingStateIsEmittedBeforeGeneration() =
        runTest(testDispatcher) {
            // Given
            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_START
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            // When
            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            // Then: verify processing state was emitted for at least one model step
            coVerify {
                inferenceEventBus.emitPipelineState("chat-123", any<MessageGenerationState.Processing>())
            }
        }

    @Test
    @DisplayName("Scenario 2.4: Token budget falls back on zero context window")
    fun tokenBudgetFallsBackOnZeroContext() =
        runTest(testDispatcher) {
            // Given: Mock config with zero context window
            val mockActiveConfig = mockk<com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration>(relaxed = true)
            every { mockActiveConfig.contextWindow } returns 0
            coEvery { activeModelProvider.getActiveConfiguration(any()) } returns mockActiveConfig

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_START
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            // When
            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            // Then: Pipeline still runs to completion (budget does not throw)
            assertTrue(invokedModelTypes.contains(ModelType.DRAFT_ONE))
        }

    @Test
    @DisplayName("Scenario 2.6: Thermal Watchdog Fallback cancels pipeline mid-token")
    fun thermalWatchdogFallbackCancelsPipeline() =
        runTest(testDispatcher) {
            // Given: Headroom is critically low (0.10f)
            every { powerManager.getThermalHeadroom(any()) } returns 0.10f

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_START
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            // When
            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            // Then: Pipeline fails and emits Failed state with thermal shutdown message
            coVerify {
                inferenceEventBus.emitPipelineState(
                    "chat-123",
                    match { state ->
                        state is MessageGenerationState.Failed &&
                            state.error.message == "response was killed by Android due to thermal shutdown"
                    },
                )
            }
            verify { service.stopSelf() }
        }

    @Test
    @DisplayName("MOA2: ACTION_RESUME with valid saved state JSON continues pipeline")
    fun actionResumeWithValidSavedStateContinuesPipeline() =
        runTest(testDispatcher) {
            val savedState =
                PipelineState(
                    chatId = "chat-123",
                    currentStep = PipelineStep.SYNTHESIS,
                    userMessage = "hello",
                    stepOutputs =
                        mapOf(
                            PipelineStep.DRAFT_ONE to "draft-one-output",
                            PipelineStep.DRAFT_TWO to "draft-two-output",
                        ),
                )
            val stateJson = savedState.toJson()

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_RESUME
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_STATE_JSON) } returns stateJson

            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            assertFalse(
                invokedModelTypes.contains(ModelType.DRAFT_ONE),
                "Resume must skip completed DRAFT_ONE",
            )
            assertFalse(
                invokedModelTypes.contains(ModelType.DRAFT_TWO),
                "Resume must skip completed DRAFT_TWO",
            )
            assertTrue(
                invokedModelTypes.contains(ModelType.MAIN),
                "Expected SYNTHESIS (MAIN) in $invokedModelTypes",
            )
            assertTrue(
                invokedModelTypes.contains(ModelType.FINAL_SYNTHESIS),
                "Expected FINAL_SYNTHESIS in $invokedModelTypes",
            )
            verify { service.startForeground(any(), any(), any()) }
        }

    @Test
    @DisplayName("MOA3: Full pipeline completion publishes terminal Finished to InferenceEventBus")
    fun fullPipelineCompletionPublishesFinished() =
        runTest(testDispatcher) {
            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns InferenceService.ACTION_START
            every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
            every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            coVerify {
                inferenceEventBus.emitPipelineState(
                    "chat-123",
                    MessageGenerationState.Finished(ModelType.FINAL_SYNTHESIS),
                )
            }
            coVerify { inferenceEventBus.clearPipelineRequest("chat-123") }
        }
}
