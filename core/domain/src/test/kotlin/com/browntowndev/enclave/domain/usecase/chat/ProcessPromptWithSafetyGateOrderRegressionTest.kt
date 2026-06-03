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
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * DOPG-M-02 — documents forbidden moderate-before-persist ordering vs production gate.
 */
class ProcessPromptWithSafetyGateOrderRegressionTest {
    @Test
    @DisplayName("DOPG-M-02 — production gate order differs from moderate-before-persist mutation")
    fun dopgM02_productionOrderDiffersFromMutation() =
        runTest {
            val productionOrder = mutableListOf<String>()
            val createUser = mockk<CreateUserMessageUseCase>()
            val createPlaceholder = mockk<CreateAssistantPlaceholderUseCase>()
            val rollback = mockk<RollbackPromptSubmissionUseCase>(relaxed = true)
            coEvery { createUser(any(), any()) } coAnswers {
                productionOrder.add("createUser")
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-existing-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }
            coEvery { createPlaceholder(any(), any(), any(), any()) } coAnswers {
                productionOrder.add("createPlaceholder")
                AssistantPlaceholderResult(assistantMessageId = MessageId("asst-1"))
            }
            val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
            val generateChatTitle = mockk<GenerateChatTitleUseCase>(relaxed = true)
            val productionGate =
                ProcessPromptWithSafetyGateUseCase(
                    createUserMessageUseCase = createUser,
                    createAssistantPlaceholderUseCase = createPlaceholder,
                    moderatePromptUseCase =
                        ModeratePromptUseCase(
                            object : PromptModerationPort {
                                override suspend fun classify(prompt: String): PromptModerationScores {
                                    productionOrder.add("moderate")
                                    return PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
                                }
                            },
                        ),
                    rollbackPromptSubmissionUseCase = rollback,
                    cloudSyncPort = cloudSyncPort,
                    generateChatTitleUseCase = generateChatTitle,
                )
            productionGate.invoke(
                PromptSafetyGateRequest(
                    message =
                        Message(
                            id = MessageId("user-1"),
                            chatId = ChatId("chat-existing-1"),
                            content = Content(text = "Hello crew"),
                            role = Role.USER,
                            messageState = MessageState.COMPLETE,
                        ),
                    promptTextForModeration = "Hello crew",
                    shouldModerate = true,
                    assistantMessageId = MessageId("asst-1"),
                ),
            )

            val mutationOrder = mutableListOf<String>()
            val mutationModerate =
                ModeratePromptUseCase(
                    object : PromptModerationPort {
                        override suspend fun classify(prompt: String): PromptModerationScores {
                            mutationOrder.add("moderate")
                            return PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
                        }
                    },
                )
            mutationModerate("Hello crew")
            mutationOrder.add("createUser")
            mutationOrder.add("createPlaceholder")

            assertEquals(listOf("createUser", "createPlaceholder", "moderate"), productionOrder)
            assertEquals(listOf("moderate", "createUser", "createPlaceholder"), mutationOrder)
            assertNotEquals(productionOrder, mutationOrder)
        }
}