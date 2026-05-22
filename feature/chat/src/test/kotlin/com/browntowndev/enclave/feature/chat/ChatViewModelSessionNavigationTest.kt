package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        savedStateHandle =
            SavedStateHandle(
                mapOf("chatId" to navigationChatId.value),
            )
        chatUseCases = mockk(relaxed = true)
        coEvery { chatUseCases.getChat(navigationChatId) } returns flowOf(existingMessages)
        coEvery { chatUseCases.getChat(staleSessionChatId) } returns flowOf(emptyList())

        val settingsUseCases = mockk<SettingsUseCases>()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())

        val inferenceLockManager = mockk<InferenceLockManager>()
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)

        val toolExecutionEventPort = mockk<ToolExecutionEventPort>()
        every { toolExecutionEventPort.events } returns MutableSharedFlow()

        harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = mockk(relaxed = true),
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = mockk(relaxed = true),
                activeModelProvider = mockk(relaxed = true),
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = mockk<ViewModelErrorHandler>(relaxed = true),
                loggingPort = mockk<LoggingPort>(relaxed = true),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                playbackController = mockk(relaxed = true),
                studioRepository = mockk(relaxed = true),
                shareChatUseCase = mockk(relaxed = true),
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = mockk(relaxed = true),
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
                assertEquals("Hello from history", loaded.messages.first().content.text)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(atLeast = 1) { chatUseCases.getChat(navigationChatId) }
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
        val field = com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator::class.java
            .getDeclaredField("_currentChatId")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(harness.inferenceOrchestrator) as MutableStateFlow<ChatId?>
        flow.value = chatId
    }
}
