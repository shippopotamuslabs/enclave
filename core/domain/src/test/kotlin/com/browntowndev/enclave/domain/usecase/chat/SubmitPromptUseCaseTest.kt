package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import com.browntowndev.enclave.domain.usecase.FakeMessageRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SubmitPromptUseCaseTest {
    private lateinit var createUserMessageUseCase: FakeCreateUserMessageUseCase
    private lateinit var createAssistantPlaceholderUseCase: FakeCreateAssistantPlaceholderUseCase
    private lateinit var rollbackPromptSubmissionUseCase: FakeRollbackPromptSubmissionUseCase
    private lateinit var cloudSyncPort: FakeCloudSyncPort
    private lateinit var generateChatTitleUseCase: FakeGenerateChatTitleUseCase
    private lateinit var useCase: SubmitPromptUseCase

    @BeforeEach
    fun setUp() {
        createUserMessageUseCase = FakeCreateUserMessageUseCase()
        createAssistantPlaceholderUseCase = FakeCreateAssistantPlaceholderUseCase()
        rollbackPromptSubmissionUseCase = FakeRollbackPromptSubmissionUseCase()
        cloudSyncPort = FakeCloudSyncPort()
        generateChatTitleUseCase = FakeGenerateChatTitleUseCase()
        useCase =
            SubmitPromptUseCase(
                createUserMessageUseCase = createUserMessageUseCase,
                createAssistantPlaceholderUseCase = createAssistantPlaceholderUseCase,
                rollbackPromptSubmissionUseCase = rollbackPromptSubmissionUseCase,
                cloudSyncPort = cloudSyncPort,
                generateChatTitleUseCase = generateChatTitleUseCase,
            )
    }

    private fun request(
        chatId: ChatId? = ChatId("chat-existing-1"),
        prompt: String = "Hello crew",
        assistantMessageId: MessageId = MessageId("asst-gate-1"),
    ) = SubmitPromptRequest(
        message =
            Message(
                id = MessageId("user-gate-1"),
                chatId = chatId,
                content = Content(text = prompt),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
            ),
        assistantMessageId = assistantMessageId,
    )

    @Test
    @DisplayName("SP-02 — Returns UserPromptResult when message creation succeeds")
    fun sp02_returnsResultWhenCreationSucceeds() =
        runTest {
            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-2"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(chatId = ChatId("chat-existing-2")))

            assertEquals(ChatId("chat-existing-2"), result.chatId)
            assertEquals(MessageId("user-1"), result.userMessageId)
            assertTrue(rollbackPromptSubmissionUseCase.calls.isEmpty())
            assertEquals(1, cloudSyncPort.count("triggerSync"))
        }

    @Test
    @DisplayName("SP-04 — Placeholder is created before invoke returns")
    fun sp04_placeholderCreatedBeforeReturn() =
        runTest {
            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request())

            assertEquals(
                listOf(
                    FakeCreateAssistantPlaceholderUseCase.PlaceholderCall(
                        chatId = ChatId("chat-existing-1"),
                        userMessageCreatedAtMillis = 100L,
                        assistantMessageId = MessageId("asst-gate-1"),
                        scheduleCloudSync = false,
                    ),
                ),
                createAssistantPlaceholderUseCase.calls,
            )
        }

    @Test
    @DisplayName("SP-11 — Exception invokes rollback and rethrows")
    fun sp11_exceptionInvokesRollback() =
        runTest {
            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            createAssistantPlaceholderUseCase.failure = RuntimeException("DB failure")

            assertFailsWith<RuntimeException> {
                useCase.invoke(request())
            }

            assertEquals(
                listOf(
                    FakeRollbackPromptSubmissionUseCase.RollbackCall(
                        chatId = ChatId("chat-existing-1"),
                        userMessageId = MessageId("user-1"),
                        assistantMessageId = MessageId("asst-gate-1"),
                        wasNewChat = false,
                    ),
                ),
                rollbackPromptSubmissionUseCase.calls,
            )
            assertEquals(1, cloudSyncPort.count("triggerSync"))
        }

    @Test
    @DisplayName("SP-12 — Title generation is launched for new chat with non-blank prompt")
    fun sp12_titleGeneratedForNewChat() =
        runTest {
            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-new-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request(chatId = null, prompt = "Hello there"))
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(ChatId("chat-new-1") to "Hello there"),
                generateChatTitleUseCase.calls,
            )
        }

    @Test
    @DisplayName("SP-13 — Title generation skipped for existing chat")
    fun sp13_titleSkippedForExistingChat() =
        runTest {
            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request(chatId = ChatId("chat-existing-1")))

            assertTrue(generateChatTitleUseCase.calls.isEmpty())
        }

    @Test
    @DisplayName("SP-M-01 — Use case with real repos creates user + placeholder messages")
    fun spM01_useCaseWithRealReposCreatesMessages() =
        runTest {
            val messageRepository = FakeMessageRepository()
            val chatRepository = FakeChatRepository()
            val transactionProvider = FakeTransactionProvider()
            val realCreateUser =
                CreateUserMessageUseCase(transactionProvider, messageRepository, chatRepository)
            val realPlaceholder = CreateAssistantPlaceholderUseCase(messageRepository, chatRepository)
            val submitUseCase =
                SubmitPromptUseCase(
                    createUserMessageUseCase = realCreateUser,
                    createAssistantPlaceholderUseCase = realPlaceholder,
                    rollbackPromptSubmissionUseCase = FakeRollbackPromptSubmissionUseCase(),
                    cloudSyncPort = FakeCloudSyncPort(),
                    generateChatTitleUseCase = FakeGenerateChatTitleUseCase(),
                )

            val chatId = ChatId("chat-mutation-1")
            val result =
                submitUseCase.invoke(
                    SubmitPromptRequest(
                        message =
                            Message(
                                id = MessageId("user-m1"),
                                chatId = chatId,
                                content = Content(text = "Hello crew"),
                                role = Role.USER,
                                messageState = MessageState.COMPLETE,
                            ),
                        assistantMessageId = MessageId("asst-m1"),
                    ),
                )

            assertInstanceOf(UserPromptResult::class.java, result)
            assertEquals(2, messageRepository.getSavedMessages().size)
        }
}
