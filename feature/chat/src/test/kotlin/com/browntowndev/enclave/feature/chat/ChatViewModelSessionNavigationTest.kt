package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Regression guard: opening an existing chat from History must load that chat's messages.
 * Unit tests on [InferenceOrchestrator.syncActiveChatFromNavigation] alone missed the
 * interaction with [ChatRoute]'s LaunchedEffect(null) wiping SavedStateHandle and
 * [activeChatIdFlow] preferring a stale session id.
 */
@ExtendWith(MainDispatcherRule::class)
@DisplayName("ChatViewModel session navigation")
class ChatViewModelSessionNavigationTest {
    private val navigationChatId = ChatId("existing-chat-from-history")
    private val staleSessionChatId = ChatId("stale-in-memory-session")
    private val existingMessages =
        listOf(
            Message(
                id = MessageId("msg-1"),
                chatId = navigationChatId,
                content = Content("Hello from history"),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                createdAt = 1L,
            ),
        )

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        savedStateHandle =
            SavedStateHandle(
                mapOf("chatId" to navigationChatId.value),
            )
        chatUseCases = FakeChatUseCases()
        chatUseCases.getChatUseCase.flowsByChatId[navigationChatId] = flowOf(existingMessages)
        chatUseCases.getChatUseCase.flowsByChatId[staleSessionChatId] = flowOf(emptyList())

        val settingsUseCases = FakeChatSettingsUseCases()

        harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = FakeInferenceLockManager(),
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                errorHandler = FakeViewModelErrorHandler(),
                loggingPort = FakeLoggingPort(),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
        viewModel = harness.viewModel
        seedOrchestratorCurrentChatId(staleSessionChatId)
    }

    @Test
    @DisplayName("navigation chatId in SavedStateHandle loads messages despite stale orchestrator session")
    fun navigationChatIdLoadsMessagesDespiteStaleOrchestratorSession() =
        runTest {
            viewModel.uiState.test {
                val loaded = awaitItem()
                assertEquals(navigationChatId, loaded.chatId)
                assertEquals(1, loaded.messages.size)
                assertEquals(
                    "Hello from history",
                    loaded.messages
                        .first()
                        .content.text,
                )
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(chatUseCases.getChatUseCase.calls.count { it == navigationChatId } >= 1)
        }

    @Test
    @DisplayName("simulated ChatRoute LaunchedEffect with null must not wipe navigation chatId")
    fun routeLaunchedEffectWithNullDoesNotWipeNavigationChatId() =
        runTest {
            viewModel.onNavigationChatIdChanged(null)

            assertEquals(navigationChatId.value, savedStateHandle.get<String>("chatId"))

            viewModel.uiState.test {
                val loaded = awaitItem()
                assertEquals(navigationChatId, loaded.chatId)
                assertEquals(1, loaded.messages.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @DisplayName("sync from navigation aligns orchestrator without clearing SavedStateHandle")
    fun syncFromNavigationAlignsOrchestrator() =
        runTest {
            viewModel.onNavigationChatIdChanged(navigationChatId)

            assertEquals(navigationChatId, harness.inferenceOrchestrator.currentChatId.value)
            assertEquals(navigationChatId.value, savedStateHandle.get<String>("chatId"))
        }

    private fun seedOrchestratorCurrentChatId(chatId: ChatId) {
        val field =
            com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator::class.java
                .getDeclaredField("_currentChatId")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(harness.inferenceOrchestrator) as MutableStateFlow<ChatId?>
        flow.value = chatId
    }
}
