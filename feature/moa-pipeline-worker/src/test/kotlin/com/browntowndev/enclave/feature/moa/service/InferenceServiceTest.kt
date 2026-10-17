package com.browntowndev.enclave.feature.moa.service

import android.app.NotificationManager
import android.content.Intent
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakePipelineStateRepository
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressPersister
import com.browntowndev.enclave.feature.inference.InferenceEventBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-28: this suite now runs the real [InferenceService] under Robolectric.
 * Framework interactions are asserted through shadows (stopped-by-self,
 * foreground state), Enclave ports are :core:testing fakes, and pipeline
 * emissions are observed on the real [InferenceEventBus]. Thermal headroom is
 * scripted through [HeadroomPowerManagerShadow].
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [HeadroomPowerManagerShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServiceTest {
    private lateinit var service: InferenceService

    private val logger = FakeLoggingPort()
    private val inferenceFactory = FakeInferenceFactory()
    private val activeModelProvider = FakeActiveModelProvider()
    private val inferenceEventBus = InferenceEventBus()
    private val pipelineStateRepository = FakePipelineStateRepository()
    private val testDispatcher = StandardTestDispatcher()

    private fun testActiveConfig(contextWindow: Int? = null) =
        ActiveModelConfiguration(
            id = LocalModelConfigurationId("test-config"),
            isLocal = true,
            name = "Test Model",
            modelId = "test-model",
            systemPrompt = null,
            temperature = null,
            topK = null,
            topP = null,
            maxTokens = null,
            minP = null,
            repetitionPenalty = null,
            contextWindow = contextWindow,
            thinkingEnabled = false,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        HeadroomPowerManagerShadow.headroomForecast = 0.5f
        activeModelProvider.configuration = testActiveConfig()

        // Bare construction + manual injection, mirroring the chat-inference-service
        // suite: onCreate is never invoked because it triggers Hilt injection, which
        // requires a @HiltAndroidApp Application. The base context is attached so
        // framework calls (getSystemService, startForeground) reach Robolectric.
        service = InferenceService()
        val attachBaseContextMethod =
            android.content.ContextWrapper::class.java.getDeclaredMethod(
                "attachBaseContext",
                android.content.Context::class.java,
            )
        attachBaseContextMethod.isAccessible = true
        attachBaseContextMethod.invoke(service, RuntimeEnvironment.getApplication())

        // Replace serviceScope with test dispatcher so pipeline runs synchronously under test control
        service.serviceScope = CoroutineScope(testDispatcher + SupervisorJob())

        service.notificationManager =
            RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        service.logger = logger
        service.inferenceFactoryProvider = dagger.Lazy { inferenceFactory }
        service.activeModelProvider = activeModelProvider
        service.inferenceEventBus = inferenceEventBus
        service.chatGenerationProgressPersister =
            ChatGenerationProgressPersister(
                chatRepository = FakeChatRepository(),
                messageRepository = FakeMessageRepository(),
                embeddingEngine = FakeEmbeddingEngine(),
                extractedUrlTracker = FakeExtractedUrlTracker(),
            )
        service.pipelineStateRepository = pipelineStateRepository
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun startIntent(chatId: String, userMessage: String): Intent =
        Intent().apply {
            action = InferenceService.ACTION_START
            putExtra(InferenceService.EXTRA_CHAT_ID, chatId)
            putExtra(InferenceService.EXTRA_USER_MESSAGE, userMessage)
        }

    /** Opens the chat's pipeline stream and collects its states; cancel the returned job before the test ends. */
    private fun TestScope.collectPipelineStates(chatId: String): Pair<MutableList<MessageGenerationState>, Job> {
        val states = mutableListOf<MessageGenerationState>()
        val job =
            launch {
                inferenceEventBus.openPipelineRequest(chatId).toList(states)
            }
        testDispatcher.scheduler.runCurrent()
        return states to job
    }

    @Test
    @DisplayName("ACTION_START executes full pipeline with all model types")
    fun actionStartExecutesFullPipeline() =
        runTest(testDispatcher) {
            // Hold the pipeline inside its first step so the foreground state is
            // observable mid-run (completion removes the foreground notification).
            val gate = CompletableDeferred<Unit>()
            inferenceFactory.blockGate = gate
            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            testDispatcher.scheduler.advanceTimeBy(500)

            assertNotNull(shadowOf(service).lastForegroundNotification)

            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.DRAFT_ONE),
                "Expected DRAFT_ONE in ${inferenceFactory.invokedModelTypes}",
            )
            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.DRAFT_TWO),
                "Expected DRAFT_TWO in ${inferenceFactory.invokedModelTypes}",
            )
            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.MAIN),
                "Expected MAIN in ${inferenceFactory.invokedModelTypes}",
            )
            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.FINAL_SYNTHESIS),
                "Expected FINAL_SYNTHESIS in ${inferenceFactory.invokedModelTypes}",
            )
            assertTrue(shadowOf(service).isStoppedBySelf)
        }

    @Test
    @DisplayName("ACTION_STOP cancels pipeline and stops service")
    fun actionStopCancelsPipeline() =
        runTest(testDispatcher) {
            val intentStop =
                Intent().apply {
                    action = InferenceService.ACTION_STOP
                }

            service.onStartCommand(intentStop, 0, 1)
            advanceUntilIdle()

            assertTrue(shadowOf(service).isForegroundStopped)
            assertTrue(shadowOf(service).isStoppedBySelf)
        }

    @Test
    @DisplayName("Scenario 2.1: Invalid State JSON on Resume stops service")
    fun actionResumeWithInvalidStateJsonStopsService() =
        runTest(testDispatcher) {
            val intent =
                Intent().apply {
                    action = InferenceService.ACTION_RESUME
                    putExtra(InferenceService.EXTRA_CHAT_ID, "chat-123")
                    putExtra(InferenceService.EXTRA_STATE_JSON, "invalid-json")
                }

            service.onStartCommand(intent, 0, 1)
            advanceUntilIdle()

            assertTrue(shadowOf(service).isStoppedBySelf)
            assertTrue(inferenceFactory.invokedModelTypes.isEmpty())
        }

    @Test
    @DisplayName("Scenario 2.2: Concurrency - ACTION_START cancels active pipeline job")
    fun actionStartCancelsActiveJob() =
        runTest(testDispatcher) {
            // Given: the factory gate holds the first job inside its first step
            val gate = CompletableDeferred<Unit>()
            inferenceFactory.blockGate = gate

            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            testDispatcher.scheduler.advanceTimeBy(500) // let first job run and block

            // Start second job with new chat ID
            service.onStartCommand(startIntent("chat-456", "world"), 0, 2)
            gate.complete(Unit) // unblock (the first job is already cancelled)
            advanceUntilIdle()

            // Then: the surviving job ran to completion and stopped the service
            assertTrue(shadowOf(service).isStoppedBySelf)
        }

    @Test
    @DisplayName("Scenario 2.3: Processing state is emitted before model starts")
    fun processingStateIsEmittedBeforeGeneration() =
        runTest(testDispatcher) {
            val (states, collectJob) = collectPipelineStates("chat-123")

            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            advanceUntilIdle()
            collectJob.cancel()

            assertTrue(
                states.any { it is MessageGenerationState.Processing },
                "Expected a Processing state in $states",
            )
        }

    @Test
    @DisplayName("Scenario 2.4: Token budget falls back on zero context window")
    fun tokenBudgetFallsBackOnZeroContext() =
        runTest(testDispatcher) {
            activeModelProvider.configuration = testActiveConfig(contextWindow = 0)

            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            advanceUntilIdle()

            // Pipeline still runs to completion (budget does not throw)
            assertTrue(inferenceFactory.invokedModelTypes.contains(ModelType.DRAFT_ONE))
        }

    @Test
    @DisplayName("Scenario 2.6: Thermal Watchdog Fallback cancels pipeline mid-token")
    fun thermalWatchdogFallbackCancelsPipeline() =
        runTest(testDispatcher) {
            // Given: Headroom forecasts severe throttling (>= 0.95)
            HeadroomPowerManagerShadow.headroomForecast = 0.96f
            val (states, collectJob) = collectPipelineStates("chat-123")

            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            advanceUntilIdle()
            collectJob.cancel()

            // Then: Pipeline fails and emits Failed state with thermal shutdown message
            assertTrue(
                states.any {
                    it is MessageGenerationState.Failed &&
                        it.error.message == "response was killed by Android due to thermal shutdown"
                },
                "Expected thermal Failed state in $states",
            )
            assertTrue(shadowOf(service).isStoppedBySelf)
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
            val intent =
                Intent().apply {
                    action = InferenceService.ACTION_RESUME
                    putExtra(InferenceService.EXTRA_CHAT_ID, "chat-123")
                    putExtra(InferenceService.EXTRA_STATE_JSON, savedState.toJson())
                }

            val gate = CompletableDeferred<Unit>()
            inferenceFactory.blockGate = gate
            service.onStartCommand(intent, 0, 1)
            testDispatcher.scheduler.advanceTimeBy(500)

            assertNotNull(shadowOf(service).lastForegroundNotification)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(
                inferenceFactory.invokedModelTypes.contains(ModelType.DRAFT_ONE),
                "Resume must skip completed DRAFT_ONE",
            )
            assertFalse(
                inferenceFactory.invokedModelTypes.contains(ModelType.DRAFT_TWO),
                "Resume must skip completed DRAFT_TWO",
            )
            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.MAIN),
                "Expected SYNTHESIS (MAIN) in ${inferenceFactory.invokedModelTypes}",
            )
            assertTrue(
                inferenceFactory.invokedModelTypes.contains(ModelType.FINAL_SYNTHESIS),
                "Expected FINAL_SYNTHESIS in ${inferenceFactory.invokedModelTypes}",
            )
        }

    @Test
    @DisplayName("MOA3: Full pipeline completion publishes terminal Finished to InferenceEventBus")
    fun fullPipelineCompletionPublishesFinished() =
        runTest(testDispatcher) {
            val (states, collectJob) = collectPipelineStates("chat-123")

            service.onStartCommand(startIntent("chat-123", "hello"), 0, 1)
            advanceUntilIdle()

            assertTrue(
                states.contains(MessageGenerationState.Finished(ModelType.FINAL_SYNTHESIS)),
                "Expected terminal Finished in $states",
            )
            // The service itself cleared the stream on completion (checked before
            // cancelling the collector, whose completion would also remove it).
            assertFalse(inferenceEventBus.hasPipelineStream("chat-123"))
            collectJob.cancel()
        }
}
