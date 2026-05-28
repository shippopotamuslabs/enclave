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
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
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

@ExtendWith(MainDispatcherRule::class)
@DisplayName("ChatViewModel navigation happy path")
class ChatViewModelNavigationHappyPathTest {
    private val chatA = ChatId("chat-a")
    private val chatB = ChatId("chat-b")
    private val messagesA =
        listOf(
            Message(
                id = MessageId("msg-a-1"),
                chatId = chatA,
                content = Content("Alpha thread"),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                createdAt = 1L,
            ),
        )
    private val messagesB =
        listOf(
            Message(
                id = MessageId("msg-b-1"),
                chatId = chatB,
                content = Content("Beta thread"),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                createdAt = 2L,
            ),
        )

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setUp() {
        savedStateHandle = SavedStateHandle(mapOf("chatId" to chatA.value))
        chatUseCases = mockk(relaxed = true)
        coEvery { chatUseCases.getChat(chatA) } returns flowOf(messagesA)
        coEvery { chatUseCases.getChat(chatB) } returns flowOf(messagesB)

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

        viewModel =
            ChatViewModelTestSupport.createViewModel(
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
    }

    @Test
    @DisplayName("switching from chat A to chat B loads B messages in uiState")
    fun switchingChatsLoadsTargetMessages() =
        runTest {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(chatA, initial.chatId)
                assertEquals("Alpha thread", initial.messages.single().content.text)

                savedStateHandle["chatId"] = chatB.value
                viewModel.onNavigationChatIdChanged(chatB)

                var switched: ChatUiState? = null
                while (switched == null) {
                    val next = awaitItem()
                    if (next.chatId == chatB &&
                        next.messages.singleOrNull()?.content?.text == "Beta thread"
                    ) {
                        switched = next
                    }
                }
                assertEquals(chatB, switched.chatId)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(atLeast = 1) { chatUseCases.getChat(chatB) }
        }
}
