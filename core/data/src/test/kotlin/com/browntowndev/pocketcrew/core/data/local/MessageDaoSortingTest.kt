package com.browntowndev.pocketcrew.core.data.local

import com.browntowndev.pocketcrew.core.data.local.dao.message.MessagePersistPayload
import com.browntowndev.pocketcrew.core.data.local.dao.message.MessageWithFiles
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDaoSortingTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: MessageDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PocketCrewDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.messageDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `getMessagesByChatIdFlow sorts by createdAt with nulls at the end`() =
        runTest {
            val chatId = ChatId("chat-1")

            // Insert parent chat to satisfy FK constraint
            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Test Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )

            // 1. In-flight assistant message (null createdAt)
            val msgInFlight =
                MessageEntity(
                    id = MessageId("3"),
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                    content = "Generating...",
                    createdAt = null,
                )

            // 2. Older completed message
            val msgOld =
                MessageEntity(
                    id = MessageId("1"),
                    chatId = chatId,
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "Hello",
                    createdAt = 1000L,
                )

            // 3. Newer completed message
            val msgNew =
                MessageEntity(
                    id = MessageId("2"),
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    content = "Hi there",
                    createdAt = 2000L,
                )

            dao.upsert(msgInFlight)
            dao.upsert(msgOld)
            dao.upsert(msgNew)

            val messages = dao.getMessagesByChatIdFlow(chatId).first()

            assertEquals(3, messages.size)
            assertEquals("1", messages[0].message.id.value) // 1000L
            assertEquals("2", messages[1].message.id.value) // 2000L
            assertEquals("3", messages[2].message.id.value) // NULL
        }

    @Test
    fun `getMessagesByChatId (suspend) sorts by createdAt with nulls at the end`() =
        runTest {
            val chatId = ChatId("chat-1")

            // Insert parent chat
            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Test Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )

            dao.upsert(
                MessageEntity(
                    id = MessageId("3"),
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                    content = "Generating...",
                    createdAt = null,
                ),
            )

            dao.upsert(
                MessageEntity(
                    id = MessageId("1"),
                    chatId = chatId,
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "Hello",
                    createdAt = 1000L,
                ),
            )

            val messages = dao.getMessagesByChatId(chatId)

            assertEquals(2, messages.size)
            assertEquals("1", messages[0].message.id.value) // 1000L
            assertEquals("3", messages[1].message.id.value) // NULL
        }

    @Test
    fun `persistAllMessageData emits completed content and state atomically`() =
        runTest {
            val chatId = ChatId("chat-atomic")
            val assistantMessageId = MessageId("assistant")

            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Test Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            dao.upsert(
                MessageEntity(
                    id = assistantMessageId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                    content = "partial",
                    createdAt = null,
                ),
            )

            val emissions = mutableListOf<List<MessageWithFiles>>()
            val collectJob =
                launch {
                    dao
                        .getMessagesByChatIdFlow(chatId)
                        .take(2)
                        .collect { emissions += it }
                }

            runCurrent()
            dao.persistAllMessageData(
                MessagePersistPayload(
                    messageId = assistantMessageId,
                    modelType = ModelType.FAST,
                    thinkingStartTime = 0L,
                    thinkingEndTime = 0L,
                    thinkingDuration = null,
                    thinkingRaw = null,
                    content = "final answer",
                    messageState = MessageState.COMPLETE,
                    pipelineStep = null,
                ),
            )
            collectJob.join()

            val finalMessage = emissions.last().single { it.message.id == assistantMessageId }.message
            assertEquals("final answer", finalMessage.content)
            assertEquals(MessageState.COMPLETE, finalMessage.messageState)
            assertTrue(finalMessage.createdAt != null && finalMessage.createdAt > 0L)
            assertTrue(
                emissions.none { emission ->
                    val message = emission.single { it.message.id == assistantMessageId }.message
                    val finalContentWithoutCompleteState =
                        message.content == "final answer" && message.messageState != MessageState.COMPLETE
                    val completeStateWithoutFinalContent =
                        message.messageState == MessageState.COMPLETE && message.content != "final answer"
                    finalContentWithoutCompleteState || completeStateWithoutFinalContent
                },
            )
        }

    @Test
    fun `getMessagesForChat filters by chat and preserves createdAt ordering`() =
        runTest {
            val chatId = ChatId("chat-filter")
            val otherChatId = ChatId("chat-other")

            database.chatDao().upsert(
                ChatEntity(
                    id = chatId,
                    name = "Target",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            database.chatDao().upsert(
                ChatEntity(
                    id = otherChatId,
                    name = "Other",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )

            dao.upsert(
                MessageEntity(
                    id = MessageId("a"),
                    chatId = chatId,
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "first",
                    createdAt = 1000L,
                ),
            )
            dao.upsert(
                MessageEntity(
                    id = MessageId("b"),
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    content = "second",
                    createdAt = 2000L,
                ),
            )
            dao.upsert(
                MessageEntity(
                    id = MessageId("other"),
                    chatId = otherChatId,
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "wrong chat",
                    createdAt = 3000L,
                ),
            )

            val filtered =
                dao.getMessagesForChat(
                    chatId = chatId,
                    ids = listOf(MessageId("b"), MessageId("a"), MessageId("other")),
                )

            assertEquals(2, filtered.size)
            assertEquals("a", filtered[0].message.id.value)
            assertEquals("b", filtered[1].message.id.value)
        }
}
