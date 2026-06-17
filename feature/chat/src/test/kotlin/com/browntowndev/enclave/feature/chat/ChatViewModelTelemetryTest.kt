package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.chat.stats.ChatTelemetryUiMapper
import com.browntowndev.enclave.feature.chat.stats.ResponseStatsSheetUiState
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.coroutines.CoroutineContext

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTelemetryTest {
    @Test
    fun `TEL_001 response stats sheet shows exact remote provider telemetry values`() =
        runTest {
            val useCase =
                ObserveMessageTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        messageRows = mapOf(TelemetryTestFixtures.mOpenAi1 to listOf(TelemetryTestFixtures.telOpenAi1)),
                    ),
                )
            val mapper = ChatTelemetryUiMapper()

            useCase(TelemetryTestFixtures.mOpenAi1).test {
                val sheet =
                    mapper.mapResponseStats(awaitItem()).copy(
                        isVisible = true,
                        title = "Response statistics",
                    )
                assertResponseStats(sheet)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `response stats click does not leak another messages in-memory telemetry rows`() =
        runTest {
            val chatId = TelemetryTestFixtures.chat1
            val assistantMessageId = TelemetryTestFixtures.mOpenAi1
            val clickedMessageId = TelemetryTestFixtures.mLegacy1
            val turnKey = ActiveChatTurnKey(chatId, assistantMessageId)
            val messagesFlow = MutableStateFlow<List<Message>>(emptyList())
            val turnStore = ActiveChatTurnStore()

            val settingsUseCases = mockk<SettingsUseCases>(relaxed = true)
            val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
            val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
            val chatUseCases = mockk<ChatUseCases>(relaxed = true)
            val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)
            val toolExecutionEventPort = mockk<ToolExecutionEventPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>()
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _: CoroutineContext, _ -> }

            every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
            every { settingsUseCases.assignments } returns assignmentUseCases
            every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
            every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
            coEvery { chatUseCases.getChat(chatId) } returns messagesFlow
            every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)

            val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
            every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
            every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                    cancelInferenceUseCase = mockk(relaxed = true),
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = mockk(relaxed = true),
                    activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    activeChatTurnSnapshotPort = turnStore,
                    playbackController = mockk(relaxed = true),
                    studioRepository = studioRepository,
                    shareChatUseCase = mockk(relaxed = true),
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = mockk<StageImageAttachmentUseCase>(relaxed = true),
                    inferenceTelemetryRepositoryPort = FakeInferenceTelemetryRepositoryPort(messageRows = emptyMap()),
                )
            val viewModel = harness.viewModel
            harness.inferenceOrchestrator.seedRequestedTurnKey(turnKey)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            messagesFlow.value =
                listOf(
                    Message(
                        id = assistantMessageId,
                        chatId = chatId,
                        role = Role.ASSISTANT,
                        content = Content(text = ""),
                        messageState = MessageState.GENERATING,
                        createdAt = 1L,
                    ),
                )
            turnStore.publish(
                turnKey,
                AccumulatedMessages(
                    messages =
                        mapOf(
                            assistantMessageId to
                                MessageSnapshot(
                                    messageId = assistantMessageId,
                                    modelType = ModelType.FAST,
                                    content = "answer",
                                    thinkingRaw = "",
                                    messageState = MessageState.GENERATING,
                                    telemetryRows = listOf(TelemetryTestFixtures.telOpenAi1),
                                ),
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.onResponseStatsClick(clickedMessageId)
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.responseStatsSheet
            assertEquals("Telemetry unavailable for this response", sheet.availabilityMessage)
            assertEquals(2, sheet.metricRows.size)
        }

    private fun assertResponseStats(sheet: ResponseStatsSheetUiState) {
        assertEquals("Response statistics", sheet.title)
        assertEquals("OpenAI", sheet.providerLabel)
        assertEquals("gpt-4.1", sheet.modelLabel)
        assertEquals("400 ms", sheet.metricRows.first { it.label == "Time to first token" }.value)
        assertEquals("50.0 tok/s", sheet.metricRows.first { it.label == "Output speed" }.value)
        assertEquals("200", sheet.metricRows.first { it.label == "Output tokens" }.value)
        assertEquals("80", sheet.metricRows.first { it.label == "Reasoning tokens" }.value)
        assertEquals("1000", sheet.metricRows.first { it.label == "Input tokens" }.value)
        assertEquals("400", sheet.metricRows.first { it.label == "Cache read input tokens" }.value)
        assertEquals("$0.002500", sheet.metricRows.first { it.label == "Cost" }.value)
        assertEquals("Exact", sheet.metricRows.first { it.label == "Cost status" }.value)
    }

    private fun InferenceOrchestrator.seedRequestedTurnKey(key: ActiveChatTurnKey?) {
        val field = InferenceOrchestrator::class.java.getDeclaredField("_requestedTurnKey")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<ActiveChatTurnKey?>
        flow.value = key
    }
}