package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.inference.RoomCommittedInferencePromptStore
import com.browntowndev.enclave.core.data.repository.createMessageRepositoryImpl
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.chat.ChatHistoryRehydrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatHistoryRehydratorIntegrationTest {
    private lateinit var database: EnclaveDatabase
    private val logger = FakeLoggingPort()

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun scnPersist005_chatHistoryRehydrationIntegrationWithDatabaseReload() =
        runTest {
            // Given an in-memory EnclaveDatabase containing:
            // A chat session with ID "chat-1"
            val chatId = ChatId("chat-1")
            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Test Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )

            // A historical user message "msg-user" with content "What is the weather?"
            val userMsgId = MessageId("msg-user")
            database.messageCommandDao().upsert(
                MessageEntity(
                    id = userMsgId,
                    chatId = chatId,
                    role = Role.USER,
                    content = "What is the weather?",
                    createdAt = 1000L,
                ),
            )

            // An assistant response message "msg-assistant" with content "It is sunny."
            val assistantMsgId = MessageId("msg-assistant")
            database.messageCommandDao().upsert(
                MessageEntity(
                    id = assistantMsgId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = "It is sunny.",
                    createdAt = 2000L,
                ),
            )

            // A current user message "msg-current"
            val currentMsgId = MessageId("msg-current")
            database.messageCommandDao().upsert(
                MessageEntity(
                    id = currentMsgId,
                    chatId = chatId,
                    role = Role.USER,
                    content = "follow up",
                    createdAt = 3000L,
                ),
            )

            // An active assistant message "msg-active-assistant"
            val activeAssistantMsgId = MessageId("msg-active-assistant")
            database.messageCommandDao().upsert(
                MessageEntity(
                    id = activeAssistantMsgId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = "",
                    createdAt = 4000L,
                ),
            )

            // A committed prompt in message_committed_inference_prompt for "msg-user" with content "System: Weather info is sunny. User: What is the weather?"
            database.messageCommittedInferencePromptDao().insertOrUpdate(
                MessageCommittedInferencePromptEntity(
                    messageId = userMsgId,
                    inferenceContent = "System: Weather info is sunny. User: What is the weather?",
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            // And a new RoomCommittedInferencePromptStore (empty cache) and ChatHistoryRehydrator are configured
            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            val repository =
                createMessageRepositoryImpl(
                    messageDao = database.messageDao(),
                    messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
                    chatSummaryDao = database.chatSummaryDao(),
                    embeddingDao = database.embeddingDao(),
                )

            val rehydrator =
                ChatHistoryRehydrator(
                    messageRepository = repository,
                    loggingPort = logger,
                    committedInferencePrompts = store,
                )

            val capturedHistory = mutableListOf<ChatMessage>()
            val mockInferenceService =
                object : LlmInferencePort {
                    override fun sendPrompt(
                        prompt: String,
                        closeConversation: Boolean,
                    ): Flow<InferenceEvent> = emptyFlow()

                    override fun sendPrompt(
                        prompt: String,
                        options: GenerationOptions,
                        closeConversation: Boolean,
                    ): Flow<InferenceEvent> = emptyFlow()

                    override suspend fun setHistory(messages: List<ChatMessage>) {
                        capturedHistory.addAll(messages)
                    }

                    override suspend fun closeSession() {}
                }

            // When rehydrator is invoked to rehydrate the history of "chat-1" for a new turn
            rehydrator(
                chatId = chatId,
                userMessageId = currentMsgId,
                assistantMessageId = activeAssistantMsgId,
                service = mockInferenceService,
            )

            // Then the history payload set on the mock LlmInferencePort must contain a ChatMessage with role = Role.USER and content containing "System: Weather info is sunny. User: What is the weather?"
            val userChatMessage =
                capturedHistory.find {
                    it.role == Role.USER &&
                        it.content.contains("System: Weather info is sunny. User: What is the weather?")
                }
            assertTrue(userChatMessage != null, "Captured history should contain the committed prompt user message")

            // And it must not contain a message with content containing "What is the weather?" without the system prepended context
            val rawUserChatMessage = capturedHistory.find { it.role == Role.USER && it.content == "What is the weather?" }
            assertTrue(rawUserChatMessage == null, "Captured history should not contain the raw database message content")
        }
}
