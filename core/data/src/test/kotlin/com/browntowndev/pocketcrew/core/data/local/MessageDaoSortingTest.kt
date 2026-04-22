package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDaoSortingTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `getMessagesByChatIdFlow sorts by createdAt with nulls at the end`() = runTest {
        val chatId = ChatId("chat-1")
        
        // Insert parent chat to satisfy FK constraint
        database.chatDao().insert(ChatEntity(
            id = chatId,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        ))
        
        // 1. In-flight assistant message (null createdAt)
        val msgInFlight = MessageEntity(
            id = MessageId("3"),
            chatId = chatId,
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            content = "Generating...",
            createdAt = null
        )
        
        // 2. Older completed message
        val msgOld = MessageEntity(
            id = MessageId("1"),
            chatId = chatId,
            role = Role.USER,
            messageState = MessageState.COMPLETE,
            content = "Hello",
            createdAt = 1000L
        )
        
        // 3. Newer completed message
        val msgNew = MessageEntity(
            id = MessageId("2"),
            chatId = chatId,
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            content = "Hi there",
            createdAt = 2000L
        )

        dao.insert(msgInFlight)
        dao.insert(msgOld)
        dao.insert(msgNew)

        val messages = dao.getMessagesByChatIdFlow(chatId).first()

        assertEquals(3, messages.size)
        assertEquals("1", messages[0].id.value) // 1000L
        assertEquals("2", messages[1].id.value) // 2000L
        assertEquals("3", messages[2].id.value) // NULL
    }

    @Test
    fun `getMessagesByChatId (suspend) sorts by createdAt with nulls at the end`() = runTest {
        val chatId = ChatId("chat-1")
        
        // Insert parent chat
        database.chatDao().insert(ChatEntity(
            id = chatId,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        ))
        
        dao.insert(MessageEntity(
            id = MessageId("3"),
            chatId = chatId,
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            content = "Generating...",
            createdAt = null
        ))
        
        dao.insert(MessageEntity(
            id = MessageId("1"),
            chatId = chatId,
            role = Role.USER,
            messageState = MessageState.COMPLETE,
            content = "Hello",
            createdAt = 1000L
        ))

        val messages = dao.getMessagesByChatId(chatId)

        assertEquals(2, messages.size)
        assertEquals("1", messages[0].id.value) // 1000L
        assertEquals("3", messages[1].id.value) // NULL
    }

    @Test
    fun `persistAllMessageData emits completed content and state atomically`() = runTest {
        val chatId = ChatId("chat-atomic")
        val assistantMessageId = MessageId("assistant")

        database.chatDao().insert(ChatEntity(
            id = chatId,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        ))
        dao.insert(MessageEntity(
            id = assistantMessageId,
            chatId = chatId,
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            content = "partial",
            createdAt = null,
        ))

        val emissions = mutableListOf<List<MessageEntity>>()
        val collectJob = launch {
            dao.getMessagesByChatIdFlow(chatId)
                .take(2)
                .collect { emissions += it }
        }

        runCurrent()
        dao.persistAllMessageData(
            messageId = assistantMessageId,
            modelType = ModelType.FAST,
            thinkingStartTime = 0L,
            thinkingEndTime = 0L,
            thinkingDuration = null,
            thinkingRaw = null,
            content = "final answer",
            messageState = MessageState.COMPLETE,
            pipelineStep = null,
        )
        collectJob.join()

        val finalMessage = emissions.last().single { it.id == assistantMessageId }
        assertEquals("final answer", finalMessage.content)
        assertEquals(MessageState.COMPLETE, finalMessage.messageState)
        assertTrue(finalMessage.createdAt != null && finalMessage.createdAt > 0L)
        assertTrue(
            emissions.none { emission ->
                val message = emission.single { it.id == assistantMessageId }
                val finalContentWithoutCompleteState =
                    message.content == "final answer" && message.messageState != MessageState.COMPLETE
                val completeStateWithoutFinalContent =
                    message.messageState == MessageState.COMPLETE && message.content != "final answer"
                finalContentWithoutCompleteState || completeStateWithoutFinalContent
            }
        )
    }
}
