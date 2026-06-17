package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.FakeMessageRepository
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SubmitPromptUseCaseTest {
    private lateinit var createUserMessageUseCase: CreateUserMessageUseCase
    private lateinit var createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase
    private lateinit var rollbackPromptSubmissionUseCase: RollbackPromptSubmissionUseCase
    private lateinit var cloudSyncPort: CloudSyncPort
    private lateinit var generateChatTitleUseCase: GenerateChatTitleUseCase
    private lateinit var useCase: SubmitPromptUseCase

    @BeforeEach
    fun setUp() {
        createUserMessageUseCase = mockk()
        createAssistantPlaceholderUseCase = mockk(relaxed = true)
        rollbackPromptSubmissionUseCase = mockk(relaxed = true)
        cloudSyncPort = mockk(relaxed = true)
        generateChatTitleUseCase = mockk(relaxed = true)
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
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-2"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(chatId = ChatId("chat-existing-2")))

            assertEquals(ChatId("chat-existing-2"), result.chatId)
            assertEquals(MessageId("user-1"), result.userMessageId)
            coVerify(exactly = 0) { rollbackPromptSubmissionUseCase(any(), any(), any(), any()) }
            verify(exactly = 1) { cloudSyncPort.triggerSync() }
        }

    @Test
    @DisplayName("SP-04 — Placeholder is created before invoke returns")
    fun sp04_placeholderCreatedBeforeReturn() =
        runTest {
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request())

            coVerify(exactly = 1) {
                createAssistantPlaceholderUseCase(
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                    assistantMessageId = MessageId("asst-gate-1"),
                    scheduleCloudSync = false,
                )
            }
        }

    @Test
    @DisplayName("SP-11 — Exception invokes rollback and rethrows")
    fun sp11_exceptionInvokesRollback() =
        runTest {
            coEvery { createUserMessageUseCase(any(), scheduleCloudSync = false) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            coEvery {
                createAssistantPlaceholderUseCase(any(), any(), any(), any())
            } throws RuntimeException("DB failure")

            assertFailsWith<RuntimeException> {
                useCase.invoke(request())
            }

            coVerify(exactly = 1) {
                rollbackPromptSubmissionUseCase(
                    chatId = ChatId("chat-existing-1"),
                    userMessageId = MessageId("user-1"),
                    assistantMessageId = MessageId("asst-gate-1"),
                    wasNewChat = false,
                )
            }
            verify(exactly = 1) { cloudSyncPort.triggerSync() }
        }

    @Test
    @DisplayName("SP-12 — Title generation is launched for new chat with non-blank prompt")
    fun sp12_titleGeneratedForNewChat() =
        runTest {
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-new-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request(chatId = null, prompt = "Hello there"))
            testScheduler.advanceUntilIdle()

            coVerify { generateChatTitleUseCase(ChatId("chat-new-1"), "Hello there") }
        }

    @Test
    @DisplayName("SP-13 — Title generation skipped for existing chat")
    fun sp13_titleSkippedForExistingChat() =
        runTest {
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            useCase.invoke(request(chatId = ChatId("chat-existing-1")))

            coVerify(exactly = 0) { generateChatTitleUseCase(any(), any()) }
        }

    @Test
    @DisplayName("SP-M-01 — Use case with real repos creates user + placeholder messages")
    fun spM01_useCaseWithRealReposCreatesMessages() =
        runTest {
            val messageRepository = FakeMessageRepository()
            val chatRepository = FakeChatRepository()
            val transactionProvider = mockk<com.browntowndev.enclave.domain.port.repository.TransactionProvider>()
            coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
                val block = firstArg<suspend () -> Any>()
                block()
            }
            val realCreateUser =
                CreateUserMessageUseCase(transactionProvider, messageRepository, chatRepository)
            val realPlaceholder = CreateAssistantPlaceholderUseCase(messageRepository, chatRepository)
            val noopRollback = mockk<RollbackPromptSubmissionUseCase>(relaxed = true)
            val generateTitle = mockk<GenerateChatTitleUseCase>(relaxed = true)
            val submitUseCase =
                SubmitPromptUseCase(
                    createUserMessageUseCase = realCreateUser,
                    createAssistantPlaceholderUseCase = realPlaceholder,
                    rollbackPromptSubmissionUseCase = noopRollback,
                    cloudSyncPort = mockk(relaxed = true),
                    generateChatTitleUseCase = generateTitle,
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
