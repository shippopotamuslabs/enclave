package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import com.browntowndev.enclave.domain.usecase.FakeMessageRepository
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ProcessPromptWithSafetyGateUseCaseTest {
    private lateinit var createUserMessageUseCase: CreateUserMessageUseCase
    private lateinit var createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase
    private lateinit var rollbackPromptSubmissionUseCase: RollbackPromptSubmissionUseCase
    private lateinit var cloudSyncPort: CloudSyncPort
    private lateinit var generateChatTitleUseCase: GenerateChatTitleUseCase
    private lateinit var useCase: ProcessPromptWithSafetyGateUseCase

    @BeforeEach
    fun setUp() {
        createUserMessageUseCase = mockk()
        createAssistantPlaceholderUseCase = mockk(relaxed = true)
        rollbackPromptSubmissionUseCase = mockk(relaxed = true)
        cloudSyncPort = mockk(relaxed = true)
        generateChatTitleUseCase = mockk(relaxed = true)
    }

    private fun buildUseCase(moderatePromptUseCase: ModeratePromptUseCase): ProcessPromptWithSafetyGateUseCase =
        ProcessPromptWithSafetyGateUseCase(
            createUserMessageUseCase = createUserMessageUseCase,
            createAssistantPlaceholderUseCase = createAssistantPlaceholderUseCase,
            moderatePromptUseCase = moderatePromptUseCase,
            rollbackPromptSubmissionUseCase = rollbackPromptSubmissionUseCase,
            cloudSyncPort = cloudSyncPort,
            generateChatTitleUseCase = generateChatTitleUseCase,
        )

    private fun request(
        chatId: ChatId? = ChatId("chat-existing-1"),
        prompt: String = "blocked input",
        shouldModerate: Boolean = true,
        assistantMessageId: MessageId = MessageId("asst-gate-1"),
    ) = PromptSafetyGateRequest(
        message =
            Message(
                id = MessageId("user-gate-1"),
                chatId = chatId,
                content = Content(text = prompt),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
            ),
        promptTextForModeration = prompt,
        shouldModerate = shouldModerate,
        assistantMessageId = assistantMessageId,
    )

    @Test
    @DisplayName("DOPG-D-01 — placeholder exists before moderation completes")
    fun dopgD01_placeholderBeforeModerationCompletes() =
        runTest {
            val moderationGate = CompletableDeferred<Unit>()
            val delayingPort =
                object : PromptModerationPort {
                    override suspend fun classify(prompt: String): PromptModerationScores {
                        moderationGate.await()
                        return PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
                    }
                }
            useCase = buildUseCase(ModeratePromptUseCase(delayingPort))
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            val invokeJob = async { useCase.invoke(request()) }
            runCurrent()

            coVerify(exactly = 1) { createAssistantPlaceholderUseCase(any(), any(), any(), any()) }
            assertFalse(moderationGate.isCompleted)

            moderationGate.complete(Unit)
            val result = invokeJob.await()
            assertInstanceOf(PromptSafetyGateResult.Allowed::class.java, result)
        }

    @Test
    @DisplayName("DOPG-D-02 — Gate returns Allowed when moderation passes")
    fun dopgD02_allowedWhenModerationPasses() =
        runTest {
            useCase = buildUseCase(ModeratePromptUseCase(AllowPromptModerationPort))
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-2"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(chatId = ChatId("chat-existing-2"), prompt = "Hello crew"))

            assertInstanceOf(PromptSafetyGateResult.Allowed::class.java, result)
            val allowed = result as PromptSafetyGateResult.Allowed
            assertEquals(ChatId("chat-existing-2"), allowed.userResult.chatId)
            assertEquals(MessageId("asst-gate-1"), allowed.assistantMessageId)
            coVerify(exactly = 0) { rollbackPromptSubmissionUseCase(any(), any(), any(), any()) }
            verify(exactly = 1) { cloudSyncPort.triggerSync() }
        }

    @Test
    @DisplayName("DOPG-D-11 — moderation throws invokes rollback and rethrows")
    fun dopgD11_moderationThrowsInvokesRollback() =
        runTest {
            val throwingPort =
                object : PromptModerationPort {
                    override suspend fun classify(prompt: String): PromptModerationScores = throw RuntimeException("ONNX failure")
                }
            useCase = buildUseCase(ModeratePromptUseCase(throwingPort))
            coEvery { createUserMessageUseCase(any(), scheduleCloudSync = false) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

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
    @DisplayName("DOPG-D-03 — Gate runs moderation after placeholder persist")
    fun dopgD03_moderationAfterPlaceholder() =
        runTest {
            val callOrder = mutableListOf<String>()
            val recordingPort =
                object : PromptModerationPort {
                    override suspend fun classify(prompt: String): PromptModerationScores {
                        callOrder.add("moderate")
                        return PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
                    }
                }
            useCase = buildUseCase(ModeratePromptUseCase(recordingPort))
            coEvery { createUserMessageUseCase(any(), any()) } coAnswers {
                callOrder.add("createUser")
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }
            coEvery {
                createAssistantPlaceholderUseCase(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                    scheduleCloudSync = any(),
                )
            } coAnswers {
                callOrder.add("createPlaceholder")
                AssistantPlaceholderResult(assistantMessageId = MessageId("asst-gate-1"))
            }

            useCase.invoke(request())

            assertEquals(listOf("createUser", "createPlaceholder", "moderate"), callOrder)
        }

    @Test
    @DisplayName("DOPG-D-04 — Gate skips ONNX when shouldModerate is false")
    fun dopgD04_skipsModerationWhenBypassed() =
        runTest {
            val blockingPort =
                object : PromptModerationPort {
                    override suspend fun classify(prompt: String): PromptModerationScores =
                        PromptModerationScores(
                            scoresByLabel = mapOf("S3" to 0.66),
                            labelAliases = mapOf("sexual/minors" to listOf("S3")),
                            thresholds = mapOf("sexual/minors" to 0.65),
                        )
                }
            useCase = buildUseCase(ModeratePromptUseCase(blockingPort))
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(shouldModerate = false))

            assertInstanceOf(PromptSafetyGateResult.Allowed::class.java, result)
        }

    @Test
    @DisplayName("DOPG-D-05 — Gate returns Blocked and invokes rollback when ONNX blocks")
    fun dopgD05_blockedInvokesRollback() =
        runTest {
            useCase =
                buildUseCase(
                    ModeratePromptUseCase(
                        object : PromptModerationPort {
                            override suspend fun classify(prompt: String): PromptModerationScores =
                                PromptModerationScores(
                                    scoresByLabel = mapOf("S3" to 0.66, "OK" to 0.34),
                                    labelAliases = mapOf("sexual/minors" to listOf("S3")),
                                    thresholds = mapOf("sexual/minors" to 0.65),
                                )
                        },
                    ),
                )
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-3"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(chatId = ChatId("chat-existing-3")))

            assertInstanceOf(PromptSafetyGateResult.Blocked::class.java, result)
            val blocked = result as PromptSafetyGateResult.Blocked
            assertTrue("sexual/minors" in blocked.moderationResult.triggeredLabels)
            coVerify(exactly = 1) {
                rollbackPromptSubmissionUseCase(
                    chatId = ChatId("chat-existing-3"),
                    userMessageId = MessageId("user-1"),
                    assistantMessageId = MessageId("asst-gate-1"),
                    wasNewChat = false,
                )
            }
        }

    @Test
    @DisplayName("DOPG-M-01 — blocked path without rollback leaves persisted rows")
    fun dopgM01_blockedWithoutRollbackLeavesRows() =
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
            val noopRollback = mockk<RollbackPromptSubmissionUseCase>()
            coEvery { noopRollback(any(), any(), any(), any()) } returns Unit
            val generateTitle = mockk<GenerateChatTitleUseCase>(relaxed = true)
            val gate =
                ProcessPromptWithSafetyGateUseCase(
                    createUserMessageUseCase = realCreateUser,
                    createAssistantPlaceholderUseCase = realPlaceholder,
                    moderatePromptUseCase =
                        ModeratePromptUseCase(
                            object : PromptModerationPort {
                                override suspend fun classify(prompt: String): PromptModerationScores =
                                    PromptModerationScores(
                                        scoresByLabel = mapOf("S3" to 0.66),
                                        labelAliases = mapOf("sexual/minors" to listOf("S3")),
                                        thresholds = mapOf("sexual/minors" to 0.65),
                                    )
                            },
                        ),
                    rollbackPromptSubmissionUseCase = noopRollback,
                    cloudSyncPort = mockk(relaxed = true),
                    generateChatTitleUseCase = generateTitle,
                )

            val chatId = ChatId("chat-mutation-1")
            val result =
                gate.invoke(
                    PromptSafetyGateRequest(
                        message =
                            Message(
                                id = MessageId("user-m1"),
                                chatId = chatId,
                                content = Content(text = "blocked input"),
                                role = Role.USER,
                                messageState = MessageState.COMPLETE,
                            ),
                        promptTextForModeration = "blocked input",
                        shouldModerate = true,
                        assistantMessageId = MessageId("asst-m1"),
                    ),
                )

            assertInstanceOf(PromptSafetyGateResult.Blocked::class.java, result)
            assertEquals(2, messageRepository.getSavedMessages().size)
            coVerify(exactly = 1) {
                noopRollback(
                    chatId = chatId,
                    userMessageId = any(),
                    assistantMessageId = MessageId("asst-m1"),
                    wasNewChat = false,
                )
            }
        }

    @Test
    @DisplayName("DOPG-D-10 — Blank promptText returns Allowed without blocking")
    fun dopgD10_blankPromptAllowed() =
        runTest {
            useCase = buildUseCase(ModeratePromptUseCase(AllowPromptModerationPort))
            coEvery { createUserMessageUseCase(any(), any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )

            val result = useCase.invoke(request(prompt = "", shouldModerate = true))

            assertInstanceOf(PromptSafetyGateResult.Allowed::class.java, result)
        }

    private object AllowPromptModerationPort : PromptModerationPort {
        override suspend fun classify(prompt: String): PromptModerationScores = PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
    }
}
