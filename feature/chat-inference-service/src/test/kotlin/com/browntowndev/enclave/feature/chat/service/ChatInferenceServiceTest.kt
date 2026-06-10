package com.browntowndev.enclave.feature.chat.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.port.inference.NoopOnDeviceInferenceCancellationPort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressPersister
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressSession
import com.browntowndev.enclave.domain.usecase.chat.ChatHistoryRehydrator
import com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Exercises [ChatInferenceService] without Hilt component creation (feature module has no
 * @HiltAndroidApp). Dependencies are injected manually; [ChatInferenceService.onCreate] is not
 * invoked because it triggers Hilt injection.
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

    private val inferenceFactory = mockk<InferenceFactoryPort>()
    private val activeModelProvider = mockk<ActiveModelProviderPort>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
    private val embeddingEnginePort = mockk<EmbeddingEnginePort>(relaxed = true)
    private val promptComposer = mockk<PromptComposerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val searchIntentClassifier = mockk<SearchIntentClassifierPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
    private val activeChatTurnSnapshotPort = mockk<ActiveChatTurnSnapshotPort>(relaxed = true)
    private val defaultModelRepository = mockk<com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort>(relaxed = true)
    private val mediaProviderRepository = mockk<com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort>(relaxed = true)
    private val mediaToolDefinitionFactory = mockk<com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory>(relaxed = true)
    private val getProviderCapabilitiesUseCase =
        mockk<com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase>(relaxed = true)
    private val chatGenerationProgressPersister = mockk<ChatGenerationProgressPersister>()
    private val monotonicToolActivation = mockk<MonotonicToolActivationPort>(relaxed = true)
    private val committedInferencePrompts = mockk<CommittedInferencePromptPort>(relaxed = true)
    private val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)

    private val llmInferenceService = mockk<LlmInferencePort>()
    private val progressSession = mockk<ChatGenerationProgressSession>()

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

        val activeConfig =
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
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns activeConfig
        every { settingsRepository.settingsFlow } returns flowOf(SettingsData())
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()
        coEvery { messageRepository.getMessageById(any()) } returns null
        coEvery { messageRepository.resolveLatestImageBearingUserMessage(any(), any()) } returns null
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()
        coEvery { memoriesRepository.searchMemories(any(), any(), any(), any()) } returns emptyList()
        coEvery { embeddingEnginePort.getEmbedding(any()) } returns floatArrayOf(0.1f, 0.2f)
        coEvery { promptComposer.compose(any(), any(), any(), any(), any(), any(), any()) } returns "composed-prompt"

        val accumulated = AccumulatedMessages(messages = emptyMap())
        coEvery { progressSession.applyState(any()) } returns accumulated
        coEvery { progressSession.flush(any()) } just runs
        coEvery { progressSession.accumulatedMessages() } returns accumulated

        coEvery {
            chatGenerationProgressPersister.startSession(any(), any(), any(), any())
        } returns progressSession

        coEvery { inferenceFactory.withInferenceService<Any?>(any(), any()) } coAnswers {
            val block = args[1] as suspend (LlmInferencePort) -> Any?
            block(llmInferenceService)
        }
        every { llmInferenceService.sendPrompt(any(), any(), any()) } returns
            flowOf(InferenceEvent.Finished(ModelType.FAST))
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

    @Test
    @DisplayName("CIS1: Plain success path applies Finished state")
    fun plainSuccessPathAppliesFinishedState() =
        runTest(testDispatcher) {
            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            verify { llmInferenceService.sendPrompt(any(), any(), any()) }
            coVerify(exactly = 1) {
                progressSession.applyState(match { it is MessageGenerationState.Finished })
            }
        }

    @Test
    @DisplayName("CIS2: userHasImage emits Processing before stream")
    fun cis2_userHasImageEmitsProcessingBeforeStream() =
        runTest(testDispatcher) {
            val appliedStates = mutableListOf<MessageGenerationState>()
            coEvery { progressSession.applyState(any()) } answers {
                appliedStates.add(firstArg())
                AccumulatedMessages(messages = emptyMap())
            }

            service.onStartCommand(startIntent(userHasImage = true), 0, 1)
            advanceUntilIdle()

            assertTrue(appliedStates.isNotEmpty(), "expected at least one applyState call")
            assertTrue(
                appliedStates.first() is MessageGenerationState.Processing,
                "first state should be Processing but was ${appliedStates.first()::class.simpleName}",
            )
            coVerify {
                progressSession.applyState(match { it is MessageGenerationState.Processing })
            }
        }

    @Test
    @DisplayName("CIS5: ACTION_STOP while job active defers stopSelf to finally block")
    fun cis5_actionStopWhileJobActiveUsesFinallyStopSelf() =
        runTest(testDispatcher) {
            val streamStarted = CompletableDeferred<Unit>()
            every { llmInferenceService.sendPrompt(any(), any(), any()) } returns
                flow {
                    streamStarted.complete(Unit)
                    awaitCancellation()
                }

            val spiedService = spyk(service, recordPrivateCalls = false)
            spiedService.onStartCommand(startIntent(), 0, 1)
            streamStarted.await()
            runCurrent()

            spiedService.onStartCommand(
                Intent(ChatInferenceService.ACTION_STOP),
                0,
                2,
            )
            advanceUntilIdle()

            verify(atLeast = 1) { spiedService.stopSelf(1) }
        }

    @Test
    @DisplayName("CIS3: ACTION_START invokes startForeground for ongoing notification")
    fun cis3_actionStartInvokesStartForeground() =
        runTest(testDispatcher) {
            val spiedService = spyk(service, recordPrivateCalls = false)

            spiedService.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            verify { spiedService.startForeground(ONGOING_NOTIFICATION_ID, any()) }
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
            every {
                llmInferenceService.sendPrompt(any(), any(), any())
            } returns
                flowOf(
                    InferenceEvent.Thinking("Thinking chunk", ModelType.FAST),
                    InferenceEvent.PartialResponse("Response text", ModelType.FAST),
                    InferenceEvent.Finished(ModelType.FAST),
                )

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            coVerify { activeChatTurnSnapshotPort.publish(any(), any()) }
            coVerify { progressSession.applyState(match { it is MessageGenerationState.Finished }) }
        }

    @Test
    @DisplayName("Scenario 3.2: Rehydration Failure Resilience continues generation")
    fun testRehydrationFailureResilience() =
        runTest(testDispatcher) {
            coEvery { messageRepository.getMessagesForChat(any()) } throws RuntimeException("DB offline")

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            verify { llmInferenceService.sendPrompt(any(), any(), any()) }
            coVerify { progressSession.applyState(match { it is MessageGenerationState.Finished }) }
        }

    @Test
    @DisplayName("Scenario 3.4: Decrypt Exception publishes failure state")
    fun testDecryptExceptionPublishesFailureState() =
        runTest(testDispatcher) {
            coEvery {
                inferenceFactory.withInferenceService<Any?>(any(), any())
            } throws
                ActiveModelConfigurationDecryptException(
                    ModelType.FAST,
                    "Decrypt key error",
                )

            service.onStartCommand(startIntent(), 0, 1)
            advanceUntilIdle()

            coVerify {
                progressSession.applyState(match { it is MessageGenerationState.Failed })
            }
        }

    @Test
    @DisplayName("Scenario 3.5: Job Cancellation Cleanup flushes state to db")
    fun testJobCancellationCleanupFlushesState() =
        runTest(testDispatcher) {
            val blockStarted = CompletableDeferred<Unit>()
            coEvery {
                inferenceFactory.withInferenceService<Any?>(any(), any())
            } coAnswers {
                blockStarted.complete(Unit)
                val block = args[1] as suspend (LlmInferencePort) -> Any?
                block(llmInferenceService)
            }
            every {
                llmInferenceService.sendPrompt(any(), any(), any())
            } returns
                flow {
                    emit(InferenceEvent.PartialResponse("partial", ModelType.FAST))
                    awaitCancellation()
                }

            service.onStartCommand(startIntent(), 0, 1)
            blockStarted.await()

            service.onStartCommand(Intent(ChatInferenceService.ACTION_STOP), 0, 2)
            advanceUntilIdle()

            coVerify(timeout = 2000) { progressSession.flush(markIncompleteAsCancelled = true) }
        }
}
