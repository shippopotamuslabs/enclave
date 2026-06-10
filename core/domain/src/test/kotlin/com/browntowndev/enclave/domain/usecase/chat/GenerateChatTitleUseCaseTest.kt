package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import com.browntowndev.enclave.testing.FakeLoggingPort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

class GenerateChatTitleUseCaseTest {
    private lateinit var chatRepository: FakeChatRepository
    private val chatId = ChatId("chat-1")

    @BeforeEach
    fun setup() {
        chatRepository = FakeChatRepository()
        chatRepository.setChats(
            listOf(
                Chat(id = chatId, name = "Fallback truncated title", created = Date(), lastModified = Date()),
            ),
        )
    }

    private fun useCaseReturning(title: String?): GenerateChatTitleUseCase =
        GenerateChatTitleUseCase(
            titleGenerator =
                object : ChatTitleGeneratorPort {
                    override suspend fun generateTitle(prompt: String): String? = title
                },
            chatRepository = chatRepository,
            loggingPort = FakeLoggingPort(),
        )

    @Test
    fun `applies generated title on success`() =
        runTest {
            useCaseReturning("Weekend trip plan")(chatId, "Help me plan a weekend trip to the coast")

            assertEquals(
                "Weekend trip plan",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `keeps fallback when generator returns null`() =
        runTest {
            useCaseReturning(null)(chatId, "Help me plan a weekend trip")

            assertEquals(
                "Fallback truncated title",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `keeps fallback when generator returns blank`() =
        runTest {
            useCaseReturning("   ")(chatId, "Help me plan a weekend trip")

            assertEquals(
                "Fallback truncated title",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `keeps fallback on blank prompt without invoking generator`() =
        runTest {
            var called = false
            val useCase =
                GenerateChatTitleUseCase(
                    titleGenerator =
                        object : ChatTitleGeneratorPort {
                            override suspend fun generateTitle(prompt: String): String? {
                                called = true
                                return "should not run"
                            }
                        },
                    chatRepository = chatRepository,
                    loggingPort = FakeLoggingPort(),
                )

            useCase(chatId, "   ")

            assertEquals(false, called)
            assertEquals(
                "Fallback truncated title",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `strips special tokens and surrounding quotes`() =
        runTest {
            useCaseReturning("<pad> \"Budget travel tips\" </s>")(chatId, "cheap travel")

            assertEquals(
                "Budget travel tips",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `keeps fallback when generator returns path-like degenerate title`() =
        runTest {
            useCaseReturning("er/State/State/State/State/State/State/State/")(chatId, "path prompt")

            assertEquals(
                "Fallback truncated title",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `keeps fallback on exception`() =
        runTest {
            val useCase =
                GenerateChatTitleUseCase(
                    titleGenerator =
                        object : ChatTitleGeneratorPort {
                            override suspend fun generateTitle(prompt: String): String? = throw RuntimeException("ort boom")
                        },
                    chatRepository = chatRepository,
                    loggingPort = FakeLoggingPort(),
                )

            useCase(chatId, "anything")

            assertEquals(
                "Fallback truncated title",
                chatRepository
                    .getAllChats()
                    .first()
                    .single()
                    .name,
            )
        }
}
