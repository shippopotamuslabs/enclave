package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistVoiceTurnUseCaseTest {
    private fun useCase(
        createUserMessageUseCase: CreateUserMessageUseCase,
        messageRepository: FakeMessageRepository,
        chatRepository: FakeChatRepository = FakeChatRepository(),
        generateChatTitleUseCase: GenerateChatTitleUseCase = FakeGenerateChatTitleUseCase(),
    ): PersistVoiceTurnUseCase =
        PersistVoiceTurnUseCase(
            createUserMessageUseCase = createUserMessageUseCase,
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            generateChatTitleUseCase = generateChatTitleUseCase,
        )

    private fun FakeMessageRepository.savedAssistant(): Message =
        messages().single { it.role == Role.ASSISTANT }

    @Test
    @DisplayName("LV-PERSIST-001")
    fun lvPersist001_bothTurnsExistingChat() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u1"),
                    chatId = ChatId("c1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000L,
                )

            val result =
                useCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "hello there", "hi back")

            val userMessage = createUserMessageUseCase.calls.single()
            assertEquals(Role.USER, userMessage.role)
            assertEquals("hello there", userMessage.content.text)
            val assistantMessage = messageRepository.savedAssistant()
            assertEquals(Role.ASSISTANT, assistantMessage.role)
            assertEquals("hi back", assistantMessage.content.text)
            assertEquals(ModelType.LIVE_VOICE, assistantMessage.modelType)
            assertEquals(ChatId("c1"), assistantMessage.chatId)
            assertEquals(1_700_000_000_001L, assistantMessage.createdAt)
            assertEquals(ChatId("c1"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-002")
    fun lvPersist002_chatCreatedOnDemand() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u9"),
                    chatId = ChatId("new-chat"),
                    userMessageCreatedAtMillis = 0L,
                )

            val result =
                useCase(createUserMessageUseCase, messageRepository)
                    .invoke(null, "start a session", "ready")

            val assistantMessage = messageRepository.savedAssistant()
            assertEquals(ChatId("new-chat"), assistantMessage.chatId)
            assertEquals(ChatId("new-chat"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-003")
    fun lvPersist003_blankUserSeedsFromAssistant() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u2"),
                    chatId = ChatId("seed-chat"),
                    userMessageCreatedAtMillis = 0L,
                )

            val result =
                useCase(createUserMessageUseCase, messageRepository)
                    .invoke(null, "   ", "the answer is 42")

            val userMessage = createUserMessageUseCase.calls.single()
            assertEquals("the answer is 42", userMessage.content.text)
            val assistantMessage = messageRepository.savedAssistant()
            assertEquals("the answer is 42", assistantMessage.content.text)
            assertEquals(ChatId("seed-chat"), assistantMessage.chatId)
            assertEquals(ChatId("seed-chat"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-005")
    fun lvPersist005_assistantOnlyExistingChatDoesNotCreateUserRow() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()

            val result =
                useCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "", "assistant reply only")

            assertTrue(createUserMessageUseCase.calls.isEmpty())
            val assistantMessage = messageRepository.savedAssistant()
            assertEquals(Role.ASSISTANT, assistantMessage.role)
            assertEquals("assistant reply only", assistantMessage.content.text)
            assertEquals(ChatId("c1"), assistantMessage.chatId)
            assertEquals(ChatId("c1"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-004")
    fun lvPersist004_bothBlankPersistsNothing() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()

            val result =
                useCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "", "   ")

            assertTrue(createUserMessageUseCase.calls.isEmpty())
            assertTrue(messageRepository.messages().isEmpty())
            assertEquals(ChatId("c1"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-006")
    fun lvPersist006_newChatWithUserTextTriggersTitleGen() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()
            val generateChatTitleUseCase = FakeGenerateChatTitleUseCase()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u9"),
                    chatId = ChatId("new-chat"),
                    userMessageCreatedAtMillis = 0L,
                )

            useCase(
                createUserMessageUseCase = createUserMessageUseCase,
                messageRepository = messageRepository,
                generateChatTitleUseCase = generateChatTitleUseCase,
            ).invoke(null, "start a session", "ready")
            advanceUntilIdle()

            assertEquals(
                listOf(ChatId("new-chat") to "start a session"),
                generateChatTitleUseCase.calls,
            )
        }

    @Test
    @DisplayName("LV-PERSIST-007")
    fun lvPersist007_existingChatDoesNotTriggerTitleGen() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()
            val generateChatTitleUseCase = FakeGenerateChatTitleUseCase()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u1"),
                    chatId = ChatId("c1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000L,
                )

            useCase(
                createUserMessageUseCase = createUserMessageUseCase,
                messageRepository = messageRepository,
                generateChatTitleUseCase = generateChatTitleUseCase,
            ).invoke(ChatId("c1"), "hello there", "hi back")
            advanceUntilIdle()

            assertTrue(generateChatTitleUseCase.calls.isEmpty())
        }

    @Test
    @DisplayName("LV-PERSIST-009")
    fun lvPersist009_webSearchSourcesPersistedWithAssistantMessage() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()
            val chatRepository = FakeChatRepository()
            val assistantMessageId = MessageId("assistant-1")
            val sources =
                listOf(
                    WebSearchSource(
                        messageId = assistantMessageId,
                        title = "Example",
                        url = "https://example.com",
                        content = "Example content",
                        score = 0.9,
                    ),
                )

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u1"),
                    chatId = ChatId("c1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000L,
                )

            useCase(createUserMessageUseCase, messageRepository, chatRepository)
                .invoke(
                    chatId = ChatId("c1"),
                    userText = "search the web",
                    assistantText = "here are results",
                    webSearchSources = sources,
                    assistantMessageId = assistantMessageId,
                )

            val assistantMessage = messageRepository.savedAssistant()
            assertEquals(sources, assistantMessage.webSearchSources)
            assertEquals(
                listOf(assistantMessageId to sources),
                chatRepository.persistWebSearchSourcesCalls,
            )
        }

    @Test
    @DisplayName("LV-PERSIST-008")
    fun lvPersist008_assistantOnlyNewChatDoesNotTriggerTitleGen() =
        runTest {
            val createUserMessageUseCase = FakeCreateUserMessageUseCase()
            val messageRepository = FakeMessageRepository()
            val generateChatTitleUseCase = FakeGenerateChatTitleUseCase()

            createUserMessageUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("u2"),
                    chatId = ChatId("seed-chat"),
                    userMessageCreatedAtMillis = 0L,
                )

            useCase(
                createUserMessageUseCase = createUserMessageUseCase,
                messageRepository = messageRepository,
                generateChatTitleUseCase = generateChatTitleUseCase,
            ).invoke(null, "   ", "the answer is 42")
            advanceUntilIdle()

            assertTrue(generateChatTitleUseCase.calls.isEmpty())
        }
}
