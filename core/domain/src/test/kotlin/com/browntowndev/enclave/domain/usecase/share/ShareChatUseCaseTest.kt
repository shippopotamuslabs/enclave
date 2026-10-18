package com.browntowndev.enclave.domain.usecase.share

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeShareRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.share.ShareOption
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.model.share.SharedMessage
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.CreateAssistantPlaceholderUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatResponseUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatTitleUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeDbMessagesWithOptimisticUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ChatUseCases] fake (ENC-28): only [getChat] is functional (backed by [chatFlow]);
 * the use-case properties are unused by ShareChatUseCase and throw if touched.
 */
private class FakeChatUseCases : ChatUseCases {
    var chatFlow: Flow<List<Message>> = flowOf(emptyList())
    val getChatCalls = mutableListOf<ChatId>()

    override suspend fun getChat(chatId: ChatId): Flow<List<Message>> {
        getChatCalls += chatId
        return chatFlow
    }

    private fun unused(): Nothing = throw UnsupportedOperationException("not used in tests")

    override val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase get() = unused()
    override val submitPromptUseCase: SubmitPromptUseCase get() = unused()
    override val mergeDbMessagesWithOptimisticUseCase: MergeDbMessagesWithOptimisticUseCase get() = unused()
    override val generateChatResponseUseCase: GenerateChatResponseUseCase get() = unused()
    override val getChatCapablePresetsUseCase: GetChatCapablePresetsUseCase get() = unused()
    override val generateChatTitleUseCase: GenerateChatTitleUseCase get() = unused()
    override val getChatUseCase: GetChatUseCase get() = unused()
    override val listenToSpeechUseCase: ListenToSpeechUseCase get() = unused()
    override val mergeMessagesUseCase: MergeMessagesUseCase get() = unused()
    override val processFileAttachmentUseCase: ProcessFileAttachmentUseCase get() = unused()
}

class ShareChatUseCaseTest {
    private lateinit var shareRepository: FakeShareRepository
    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var shareChatUseCase: ShareChatUseCase

    @BeforeEach
    fun setUp() {
        shareRepository = FakeShareRepository()
        chatUseCases = FakeChatUseCases()
        loggingPort = FakeLoggingPort()
        shareChatUseCase = ShareChatUseCase(shareRepository, chatUseCases, loggingPort)
    }

    @Test
    fun `invoke with SingleTurn should share specific message pair`() =
        runTest {
            // Given
            val chatId = ChatId("chat-1")
            val assistantMessageId = MessageId("msg-2")
            val option = ShareOption.SingleTurn(chatId, assistantMessageId)

            val userMessage = createMessage("msg-1", chatId, Role.USER, "User message")
            val assistantMessage = createMessage("msg-2", chatId, Role.ASSISTANT, "Assistant response")
            val otherMessage = createMessage("msg-3", chatId, Role.USER, "Other message")

            chatUseCases.chatFlow = flowOf(listOf(userMessage, assistantMessage, otherMessage))

            val sharedMessages = listOf(mapToShared(userMessage), mapToShared(assistantMessage))
            val sharedChat = SharedChat("share-1", sharedMessages, createdAt = 0, expiresAt = 0, shareUrl = "url")
            shareRepository.shareChatResult = sharedChat

            // When
            val result = shareChatUseCase(option)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(sharedChat, result.getOrNull())
            assertEquals(listOf(listOf(userMessage, assistantMessage) to chatId), shareRepository.shareChatCalls)
        }

    @Test
    fun `invoke with FullConversation should share all messages`() =
        runTest {
            // Given
            val chatId = ChatId("chat-1")
            val option = ShareOption.FullConversation(chatId)

            val messages =
                listOf(
                    createMessage("msg-1", chatId, Role.USER, "Msg 1"),
                    createMessage("msg-2", chatId, Role.ASSISTANT, "Msg 2"),
                )

            chatUseCases.chatFlow = flowOf(messages)

            val sharedChat =
                SharedChat(
                    "share-1",
                    messages.map(::mapToShared),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "url",
                )
            shareRepository.shareChatResult = sharedChat

            // When
            val result = shareChatUseCase(option)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(sharedChat, result.getOrNull())
        }

    @Test
    fun `invoke should return failure when repository fails`() =
        runTest {
            // Given
            val chatId = ChatId("chat-1")
            val option = ShareOption.FullConversation(chatId)
            val messages = listOf(createMessage("msg-1", chatId, Role.USER, "Msg 1"))

            chatUseCases.chatFlow = flowOf(messages)
            shareRepository.shareChatFailure = Exception("encryptedPayload and iv must be provided.")

            // When
            val result = shareChatUseCase(option)

            // Then
            assertTrue(result.isFailure)
            assertEquals(ShareFailureMapper.DEFAULT_SHARE_MESSAGE, result.exceptionOrNull()?.message)
            assertTrue(
                loggingPort.errorCalls.any {
                    it.tag == "ShareChatUseCase" && it.message == "Failed to share chat" && it.throwable != null
                },
            )
        }

    @Test
    fun `invoke should wait for messages if flow emits empty list initially`() =
        runTest {
            // Given
            val chatId = ChatId("chat-1")
            val option = ShareOption.FullConversation(chatId)
            val messages = listOf(createMessage("msg-1", chatId, Role.USER, "Msg 1"))

            // Flow that emits empty list first, then the populated list
            chatUseCases.chatFlow =
                flow {
                    emit(emptyList())
                    emit(messages)
                }

            val sharedChat =
                SharedChat(
                    "share-1",
                    messages.map(::mapToShared),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "url",
                )
            shareRepository.shareChatResult = sharedChat

            // When
            val result = shareChatUseCase(option)

            // Then
            assertTrue(result.isSuccess, "Should succeed even if first emission was empty")
            assertEquals(sharedChat, result.getOrNull())
        }

    @Test
    fun `invoke with FullConversation should pass chatId to repository`() =
        runTest {
            val chatId = ChatId("chat-1")
            val option = ShareOption.FullConversation(chatId)
            val messages = listOf(createMessage("msg-1", chatId, Role.USER, "Msg 1"))
            chatUseCases.chatFlow = flowOf(messages)
            val sharedChat = SharedChat("share-1", messages.map(::mapToShared), createdAt = 0, expiresAt = 0, shareUrl = "url")
            shareRepository.shareChatResult = sharedChat

            val result = shareChatUseCase(option)

            assertTrue(result.isSuccess)
            assertEquals(listOf(messages to chatId), shareRepository.shareChatCalls)
        }

    @Test
    fun `invoke with SingleTurn should pass chatId to repository`() =
        runTest {
            val chatId = ChatId("chat-1")
            val assistantMessageId = MessageId("msg-2")
            val option = ShareOption.SingleTurn(chatId, assistantMessageId)
            val userMessage = createMessage("msg-1", chatId, Role.USER, "User message")
            val assistantMessage = createMessage("msg-2", chatId, Role.ASSISTANT, "Assistant response")
            chatUseCases.chatFlow = flowOf(listOf(userMessage, assistantMessage))
            val sharedChat = SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "url")
            shareRepository.shareChatResult = sharedChat

            val result = shareChatUseCase(option)

            assertTrue(result.isSuccess)
            assertEquals(listOf(listOf(userMessage, assistantMessage) to chatId), shareRepository.shareChatCalls)
        }

    @Test
    fun `getSharedChat should pass explicit decryption key to repository`() =
        runTest {
            val sharedChat = SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "")
            shareRepository.getSharedChatResult = sharedChat

            val result = shareChatUseCase.getSharedChat("share-1", "one-time-key")

            assertTrue(result.isSuccess)
            assertEquals(sharedChat, result.getOrNull())
            assertEquals(listOf("share-1" to "one-time-key"), shareRepository.getSharedChatCalls)
        }

    @Test
    fun `getSharedChat should parse decryption key from shared url`() =
        runTest {
            val sharedChat = SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "")
            shareRepository.getSharedChatResult = sharedChat

            val result = shareChatUseCase.getSharedChat("https://enclave-app.com/share/share-1?key=abc-123")

            assertTrue(result.isSuccess)
            assertEquals(sharedChat, result.getOrNull())
            assertEquals(listOf("share-1" to "abc-123"), shareRepository.getSharedChatCalls)
        }

    private fun createMessage(
        id: String,
        chatId: ChatId,
        role: Role,
        text: String,
    ): Message =
        Message(
            id = MessageId(id),
            chatId = chatId,
            content = Content(text = text),
            role = role,
        )

    private fun mapToShared(message: Message): SharedMessage =
        SharedMessage(
            role = message.role.name,
            content = message.content.text,
            timestamp = 0L,
        )
}
