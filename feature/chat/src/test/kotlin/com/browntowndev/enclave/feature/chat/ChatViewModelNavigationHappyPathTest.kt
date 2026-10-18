package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setUp() {
        savedStateHandle = SavedStateHandle(mapOf("chatId" to chatA.value))
        chatUseCases = FakeChatUseCases()
        chatUseCases.getChatUseCase.flowsByChatId[chatA] = flowOf(messagesA)
        chatUseCases.getChatUseCase.flowsByChatId[chatB] = flowOf(messagesB)

        val settingsUseCases = FakeChatSettingsUseCases()
        val inferenceLockManager = FakeInferenceLockManager()
        val toolExecutionEventPort = FakeToolExecutionEventPort()

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = FakeViewModelErrorHandler(),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
    }

    @Test
    @DisplayName("switching from chat A to chat B loads B messages in uiState")
    fun switchingChatsLoadsTargetMessages() =
        runTest {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(chatA, initial.chatId)
                assertEquals(
                    "Alpha thread",
                    initial.messages
                        .single()
                        .content.text,
                )

                savedStateHandle["chatId"] = chatB.value
                viewModel.onNavigationChatIdChanged(chatB)

                var switched: ChatUiState? = null
                while (switched == null) {
                    val next = awaitItem()
                    if (next.chatId == chatB &&
                        next.messages
                            .singleOrNull()
                            ?.content
                            ?.text == "Beta thread"
                    ) {
                        switched = next
                    }
                }
                assertEquals(chatB, switched.chatId)
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(chatUseCases.getChatUseCase.calls.count { it == chatB } >= 1)
        }
}
