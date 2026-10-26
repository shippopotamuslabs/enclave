package com.browntowndev.enclave.feature.chat.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakePromptComposer
import com.browntowndev.enclave.core.testing.FakeSearchIntentClassifier
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.usecase.chat.InMemoryMonotonicToolActivationStore
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.NoopOnDeviceInferenceCancellationPort
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressPersister
import com.browntowndev.enclave.domain.usecase.chat.ChatHistoryRehydrator
import com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparer
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManagerImpl
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Exercises [ChatInferenceService] without Hilt component creation (feature module has no
 * @HiltAndroidApp). Dependencies are injected manually; [ChatInferenceService.onCreate] is not
 * invoked because it triggers Hilt injection.
 *
 * ENC-28: all Enclave ports are :core:testing fakes and the progress persister,
 * rehydrator, and request preparer are the real objects wired over those fakes.
 * Session behavior is asserted through its observable effects: the snapshots the
 * service publishes for every applied state, the messages persisted into the
 * fake repositories on terminal states, and the URL tracker's clears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatInferenceServiceTest {
    companion object {
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val ONGOING_NOTIFICATION_ID = 1001
    }

    private lateinit var service: ChatInferenceService
    private val testDispatcher = StandardTestDispatcher()

    private val inferenceFactory = FakeInferenceFactory()
    private val llm = inferenceFactory.llm
    private val activeModelProvider = FakeActiveModelProvider()
    private val messageRepository = FakeMessageRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val memoriesRepository = FakeMemoriesRepository()
    private val embeddingEnginePort = FakeEmbeddingEngine()
    private val promptComposer = FakePromptComposer()
    private val loggingPort = FakeLoggingPort()
    private val searchIntentClassifier = FakeSearchIntentClassifier()
    private val toolExecutor = FakeToolExecutor()
    private val activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort()
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase())
    private val getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase()
    private val extractedUrlTracker = FakeExtractedUrlTracker()
    private val chatRepository = FakeChatRepository()
    private val chatGenerationProgressPersister =
        ChatGenerationProgressPersister(
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            embeddingEngine = embeddingEnginePort,
            extractedUrlTracker = extractedUrlTracker,
        )
    private val monotonicToolActivation = InMemoryMonotonicToolActivationStore()
    private val committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort
    private val inferenceLockManager = InferenceLockManagerImpl()

    @BeforeEach
    fun setUp() {
        service = ChatInferenceService()

        val baseContext = RuntimeEnvironment.getApplication()
        val attachBaseContextMethod =
            android.content.ContextWrapper::class.java.getDeclaredMethod(
                "attachBaseContext",
                Context::class.java,
            )
        attachBaseContextMethod.isAccessible = true
        attachBaseContextMethod.invoke(service, baseContext)

        service.inferenceFactory = inferenceFactory
        service.activeModelProvider = activeModelProvider
        service.messageRepository = messageRepository
        service.settingsRepository = settingsRepository
        service.memoriesRepository = memoriesRepository
        service.embeddingEnginePort = embeddingEnginePort
        service.promptComposer = promptComposer
        service.loggingPort = loggingPort
        service.searchIntentClassifier = searchIntentClassifier
        service.toolExecutor = toolExecutor
        service.activeChatTurnSnapshotPort = activeChatTurnSnapshotPort
        service.defaultModelRepository = defaultModelRepository
        service.mediaProviderRepository = mediaProviderRepository
        service.mediaToolDefinitionFactory = mediaToolDefinitionFactory
        service.getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase
        service.chatGenerationProgressPersister = chatGenerationProgressPersister
        service.monotonicToolActivation = monotonicToolActivation
        service.committedInferencePrompts = committedInferencePrompts
        service.onDeviceInferenceCancellation = NoopOnDeviceInferenceCancellationPort
        service.inferenceLockManager = inferenceLockManager

        val scopeField = ChatInferenceService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, CoroutineScope(testDispatcher + SupervisorJob()))

        val historyRehydrator =
            ChatHistoryRehydrator(
                messageRepository = messageRepository,
                loggingPort = loggingPort,
                committedInferencePrompts = committedInferencePrompts,
            )
        val inferenceRequestPreparer =
            ChatInferenceRequestPreparer(
                activeModelProvider,
                settingsRepository,
                messageRepository,
                memoriesRepository,
                embeddingEnginePort,
                promptComposer = promptComposer,
                defaultModelRepository,
                mediaProviderRepository,
                mediaToolDefinitionFactory,
                getProviderCapabilitiesUseCase,
                loggingPort,
                searchIntentClassifier = searchIntentClassifier,
                toolExecutor = toolExecutor,
                monotonicToolActivation = monotonicToolActivation,
            )

        val hrField = ChatInferenceService::class.java.getDeclaredField("historyRehydrator")
        hrField.isAccessible = true
        hrField.set(service, historyRehydrator)

        val irpField = ChatInferenceService::class.java.getDeclaredField("inferenceRequestPreparer")
        irpField.isAccessible = true
        irpField.set(service, inferenceRequestPreparer)

        val channelsMethod = ChatInferenceService::class.java.getDeclaredMethod("createNotificationChannels")
        channelsMethod.isAccessible = true
        channelsMethod.invoke(service)

        activeModelProvider.configuration =
            ActiveModelConfiguration(
                modelId = "test-model",
                id = LocalModelConfigurationId("fast"),
                isLocal = true,
                name = "Fast",
                systemPrompt = "System",
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 100,
                minP = 0.0,
                repetitionPenalty = 1.0,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = false,
            )
        promptComposer.resultOverride = "composed-prompt"
    }

    @AfterEach
    fun tearDown() {
        if (!::service.isInitialized) return
        // ENC-221: no coroutine launched on the test-replaced scope may leak
        // into the next test. Cancel the scope's job, then drain the test
        // scheduler so every outstanding child finishes cancelling here.
        val scopeField = ChatInferenceService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        val replacedScope = scopeField.get(service) as CoroutineScope
        replacedScope.coroutineContext[Job]?.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun startIntent(userHasImage: Boolean = false): Intent =
        Intent(ChatInferenceService.ACTION_START).apply {
            putExtra(ChatInferenceService.EXTRA_PROMPT, "test-prompt")
            putExtra(ChatInferenceService.EXTRA_USER_MESSAGE_ID, "u1")
            putExtra(ChatInferenceService.EXTRA_ASSISTANT_MESSAGE_ID, "a1")
            putExtra(ChatInferenceService.EXTRA_CHAT_ID, "chat-1")
            putExtra(ChatInferenceService.EXTRA_USER_HAS_IMAGE, userHasImage)
            putExtra(ChatInferenceService.EXTRA_MODEL_TYPE, ModelType.FAST.name)
        }

    private fun processLifecycleRegistry(): LifecycleRegistry = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    private fun shadowNotificationManager(): ShadowNotificationManager {
        val notificationManager =
            RuntimeEnvironment.getApplication().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return Shadows.shadowOf(notificationManager)
    }

    /** Snapshots of the assistant message (a1) from every state the service applied, in order. */
    private fun assistantSnapshots(): List<MessageSnapshot> =
        activeChatTurnSnapshotPort.publishes.mapNotNull { it.second.messages[MessageId("a1")] }

    @Test
    @DisplayName("CIS1: Plain success path applies Finished state")
    fun plainSuccessPathAppliesFinishedState() =
        runTest(testDispatcher) {
            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            assertTrue(llm.recordedPrompts.isNotEmpty(), "expected sendPrompt to be called")
            assertEquals(
                1,
                assistantSnapshots().count { it.isComplete },
                "expected exactly one terminal (Finished) state applied; snapshots=${assistantSnapshots()}",
            )
            assertTrue(
                chatRepository.persistAllMessageDataCalls.any {
                    it.messageId == MessageId("a1") && it.messageState == MessageState.COMPLETE
                },
                "expected the finished assistant message persisted; calls=${chatRepository.persistAllMessageDataCalls}",
            )
        }

    @Test
    @DisplayName("CIS2: userHasImage emits Processing before stream")
    fun cis2_userHasImageEmitsProcessingBeforeStream() =
        runTest(testDispatcher) {
            service.onStartCommand(startIntent(userHasImage = true), 0, 1)
            advanceUntilIdle()

            val snapshots = assistantSnapshots()
            assertTrue(snapshots.isNotEmpty(), "expected at least one applied state")
            assertEquals(
                MessageState.PROCESSING,
                snapshots.first().messageState,
                "first applied state should be Processing",
            )
        }

    @Test
    @DisplayName("CIS5: ACTION_STOP while job active defers stopSelf to finally block")
    fun cis5_actionStopWhileJobActiveUsesFinallyStopSelf() =
        runTest(testDispatcher) {
            val streamStarted = CompletableDeferred<Unit>()
            llm.promptFlowOverride =
                flow {
                    streamStarted.complete(Unit)
                    awaitCancellation()
                }

            service.onStartCommand(startIntent(), 0, 1)
            streamStarted.await()
            runCurrent()

            service.onStartCommand(
                Intent(ChatInferenceService.ACTION_STOP),
                0,
                2,
            )
            advanceUntilIdle()

            assertTrue(shadowOf(service).isStoppedBySelf)
            assertEquals(1, shadowOf(service).stopSelfId)
        }

    @Test
    @DisplayName("CIS3: ACTION_START invokes startForeground for ongoing notification")
    fun cis3_actionStartInvokesStartForeground() =
        runTest(testDispatcher) {
            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            assertEquals(ONGOING_NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        }

    @Test
    @DisplayName("CIS4: maybeShowCompletionNotification posts when app is backgrounded")
    fun cis4_showsCompletionNotificationWhenBackgrounded() =
        runTest(testDispatcher) {
            val lifecycle = processLifecycleRegistry()
            val previousState = lifecycle.currentState
            try {
                lifecycle.currentState = Lifecycle.State.CREATED

                service.onStartCommand(startIntent(), 0, 1)
                advanceUntilIdle()

                val shadowNm = shadowNotificationManager()
                assertTrue(
                    shadowNm.getNotification(COMPLETION_NOTIFICATION_ID) != null,
                    "expected completion notification when ProcessLifecycleOwner is below STARTED",
                )
            } finally {
                lifecycle.currentState = previousState
            }
        }

    @Test
    @DisplayName("CIS4: completion notification skipped when app is foregrounded")
    fun cis4_skipsCompletionNotificationWhenForegrounded() =
        runTest(testDispatcher) {
            val lifecycle = processLifecycleRegistry()
            val previousState = lifecycle.currentState
            try {
                lifecycle.currentState = Lifecycle.State.RESUMED

                service.onStartCommand(startIntent(), 0, 1)
                advanceUntilIdle()

                val shadowNm = shadowNotificationManager()
                assertEquals(
                    null,
                    shadowNm.getNotification(COMPLETION_NOTIFICATION_ID),
                    "completion notification should be skipped when app is foregrounded",
                )
            } finally {
                lifecycle.currentState = previousState
            }
        }

    @Test
    @DisplayName("Scenario 3.1: Token Streaming Event Dispatch maps thinking and text events")
    fun testTokenStreamingEventDispatch() =
        runTest(testDispatcher) {
            llm.eventsToEmit =
                listOf(
                    InferenceEvent.Thinking("Thinking chunk", ModelType.FAST),
                    InferenceEvent.PartialResponse("Response text", ModelType.FAST),
                    InferenceEvent.Finished(ModelType.FAST),
                )

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            assertTrue(activeChatTurnSnapshotPort.publishes.isNotEmpty(), "expected snapshots published")
            assertEquals(1, assistantSnapshots().count { it.isComplete })
        }

    @Test
    @DisplayName("Scenario 3.2: Rehydration Failure Resilience continues generation")
    fun testRehydrationFailureResilience() =
        runTest(testDispatcher) {
            messageRepository.getMessagesForChatFailure = RuntimeException("DB offline")

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            assertTrue(llm.recordedPrompts.isNotEmpty(), "expected sendPrompt to be called")
            assertEquals(1, assistantSnapshots().count { it.isComplete })
        }

    @Test
    @DisplayName("Scenario 3.4: Decrypt Exception publishes failure state")
    fun testDecryptExceptionPublishesFailureState() =
        runTest(testDispatcher) {
            inferenceFactory.invocationFailure =
                ActiveModelConfigurationDecryptException(
                    ModelType.FAST,
                    "Decrypt key error",
                )

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            assertTrue(llm.recordedPrompts.isEmpty(), "generation must not start on decrypt failure")
            assertTrue(
                extractedUrlTracker.clearCount >= 1,
                "expected the Failed terminal state to be applied (terminal persist clears the tracker)",
            )
            assertTrue(
                chatRepository.persistAllMessageDataCalls.any { it.messageId == MessageId("a1") },
                "expected the failed assistant message persisted; calls=${chatRepository.persistAllMessageDataCalls}",
            )
        }

    @Test
    @DisplayName("Scenario 3.5: Job Cancellation Cleanup flushes state to db")
    fun testJobCancellationCleanupFlushesState() =
        runTest(testDispatcher) {
            val blockStarted = CompletableDeferred<Unit>()
            inferenceFactory.onEntered = { blockStarted.complete(Unit) }
            llm.promptFlowOverride =
                flow {
                    emit(InferenceEvent.PartialResponse("partial", ModelType.FAST))
                    awaitCancellation()
                }

            service.onStartCommand(startIntent(), 0, 1)
            blockStarted.await()

            service.onStartCommand(Intent(ChatInferenceService.ACTION_STOP), 0, 2)
            advanceUntilIdle()

            // flush(markIncompleteAsCancelled = true) persists the incomplete turn:
            // the assistant message lands in the repository only via that flush.
            assertTrue(
                chatRepository.persistAllMessageDataCalls.any { it.messageId == MessageId("a1") },
                "expected the cancelled assistant message flushed to the repository; calls=${chatRepository.persistAllMessageDataCalls}",
            )
            assertTrue(extractedUrlTracker.clearCount >= 1)
        }
}
