package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `ChatDao updatePinStatus sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val chat = ChatEntity(
            id = chatId,
            name = "Test Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false,
            pendingSync = 0
        )
        chatDao.upsert(chat)
        
        // Initial state
        var savedChat = chatDao.getChatById(chatId)!!
        assertEquals(0, savedChat.pendingSync)

        // Action
        chatDao.updatePinStatus(chatId)

        // Verify
        savedChat = chatDao.getChatById(chatId)!!
        assertEquals(1, savedChat.pendingSync)
        assertTrue(savedChat.pinned)
    }

    @Test
    fun `ChatDao updateName sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val chat = ChatEntity(
            id = chatId,
            name = "Old Name",
            created = Date(),
            lastModified = Date(),
            pinned = false,
            pendingSync = 0
        )
        chatDao.upsert(chat)

        // Action
        chatDao.updateName(chatId, "New Name")

        // Verify
        val savedChat = chatDao.getChatById(chatId)!!
        assertEquals(1, savedChat.pendingSync)
        assertEquals("New Name", savedChat.name)
    }

    @Test
    fun `MessageDao updateMessageContent sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val messageId = MessageId(UUID.randomUUID().toString())
        
        // Need a chat first due to foreign key
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "Old Content",
            role = Role.USER,
            pendingSync = 0
        )
        messageDao.upsert(message)

        // Action
        messageDao.updateMessageContent(messageId, "New Content", 10, "thinking")

        // Verify
        val savedMessage = messageDao.getMessageById(messageId)!!.message
        assertEquals(1, savedMessage.pendingSync)
        assertEquals("New Content", savedMessage.content)
    }

    @Test
    fun `MessageDao updateMessageState sets pending_sync to true`() = runTest {
        val chatId = ChatId(UUID.randomUUID().toString())
        val messageId = MessageId(UUID.randomUUID().toString())
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        messageDao.upsert(MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "Content",
            role = Role.USER,
            pendingSync = 0
        ))

        // Action
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)

        // Verify
        val savedMessage = messageDao.getMessageById(messageId)!!.message
        assertEquals(1, savedMessage.pendingSync)
        assertEquals(MessageState.COMPLETE, savedMessage.messageState)
    }

    @Test
    fun `ChatDao getClaimedForSync returns only chats with pending_sync claimed`() = runTest {
        chatDao.upsert(ChatEntity(ChatId("1"), "Chat 1", Date(), Date(), false, pendingSync = 1))
        chatDao.upsert(ChatEntity(ChatId("2"), "Chat 2", Date(), Date(), false, pendingSync = 0))

        chatDao.claimPending()
        val pending = chatDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].id.value)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `MessageDao getClaimedForSync returns only messages with pending_sync claimed`() = runTest {
        val chatId = ChatId("chat")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        
        messageDao.upsert(MessageEntity(
            id = MessageId("1"),
            chatId = chatId,
            content = "C1",
            role = Role.USER,
            pendingSync = 1
        ))
        messageDao.upsert(MessageEntity(
            id = MessageId("2"),
            chatId = chatId,
            content = "C2",
            role = Role.USER,
            pendingSync = 0
        ))

        messageDao.claimPending()
        val pending = messageDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].message.id.value)
        assertEquals(2, pending[0].message.pendingSync)
    }

    @Test
    fun `Chat mapping to entity sets pendingSync to true`() {
        val chat = Chat(
            id = ChatId("1"),
            name = "Test",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        val entity = chat.toEntity()
        assertEquals("Entity should have pendingSync = 1 when mapped from domain", 1, entity.pendingSync)
    }

    @Test
    fun `Message mapping to entity sets pendingSync to true`() {
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("chat"),
            content = com.browntowndev.pocketcrew.domain.model.chat.Content(text = "Hello"),
            role = Role.USER,
            createdAt = 1000L
        )
        val entity = message.toEntity()
        assertEquals("Entity should have pendingSync = 1 when mapped from domain", 1, entity.pendingSync)
    }

    @Test
    fun `Chat markSynced resets pending_sync`() = runTest {
        val id = ChatId("1")
        chatDao.upsert(ChatEntity(id, "Chat 1", Date(), Date(), false, pendingSync = 1))

        chatDao.claimPending()
        chatDao.markSynced(listOf(id))

        val chat = chatDao.getChatById(id)!!
        assertEquals(0, chat.pendingSync)
    }

    @Test
    fun `MessageDao markSynced resets pending_sync`() = runTest {
        val chatId = ChatId("chat")
        val messageId = MessageId("1")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        messageDao.upsert(MessageEntity(
            id = messageId,
            chatId = chatId,
            content = "C1",
            role = Role.USER,
            pendingSync = 1
        ))

        messageDao.claimPending()
        messageDao.markSynced(listOf(messageId))

        val message = messageDao.getMessageById(messageId)!!.message
        assertEquals(0, message.pendingSync)
    }

    // ========== Phase 1: TtsProviderDao ==========

    @Test
    fun `TtsProviderDao getClaimedForSync returns only claimed rows`() = runTest {
        val ttsDao = database.ttsProviderDao()
        ttsDao.upsertTtsProvider(TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, null, "alias-1", pendingSync = 1))
        ttsDao.upsertTtsProvider(TtsProviderEntity("2", "xAI TTS", ApiProvider.XAI, "eve", null, null, "alias-2", pendingSync = 0))

        ttsDao.claimPending()
        val pending = ttsDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `TtsProviderDao markSynced resets pending_sync`() = runTest {
        val ttsDao = database.ttsProviderDao()
        ttsDao.upsertTtsProvider(TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, null, "alias-1", pendingSync = 1))

        ttsDao.claimPending()
        ttsDao.markSynced(listOf("1"))

        val provider = ttsDao.getTtsProvider("1")!!
        assertEquals(0, provider.pendingSync)
    }

    @Test
    fun `TtsProviderDao markSynced only resets listed ids`() = runTest {
        val ttsDao = database.ttsProviderDao()
        ttsDao.upsertTtsProvider(TtsProviderEntity("1", "P1", ApiProvider.OPENAI, "alloy", null, null, "a1", pendingSync = 1))
        ttsDao.upsertTtsProvider(TtsProviderEntity("2", "P2", ApiProvider.XAI, "eve", null, null, "a2", pendingSync = 1))

        ttsDao.claimPending()
        ttsDao.markSynced(listOf("1"))

        assertEquals(0, ttsDao.getTtsProvider("1")!!.pendingSync)
        assertEquals(2, ttsDao.getTtsProvider("2")!!.pendingSync)
    }

    // ========== Phase 1: MediaProviderDao ==========

    @Test
    fun `MediaProviderDao getClaimedForSync returns only claimed rows`() = runTest {
        val mediaDao = database.mediaProviderDao()
        mediaDao.upsert(MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "g-key", pendingSync = 1))
        mediaDao.upsert(MediaProviderEntity("2", "Kling", ApiProvider.OPENAI, MediaCapability.VIDEO, null, null, "k-key", pendingSync = 0))

        mediaDao.claimPending()
        val pending = mediaDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `MediaProviderDao markSynced resets pending_sync`() = runTest {
        val mediaDao = database.mediaProviderDao()
        mediaDao.upsert(MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "g-key", pendingSync = 1))

        mediaDao.claimPending()
        mediaDao.markSynced(listOf("1"))

        assertEquals(0, mediaDao.getMediaProvider("1")!!.pendingSync)
    }

    // ========== Phase 1: ApiModelConfigurationsDao ==========

    @Test
    fun `ApiModelConfigurationsDao getClaimedForSync returns only claimed rows`() = runTest {
        val credDao = database.apiCredentialsDao()
        val configDao = database.apiModelConfigurationsDao()
        val credId = ApiCredentialsId(UUID.randomUUID().toString())
        credDao.upsert(ApiCredentialsEntity(id = credId, provider = ApiProvider.OPENAI, modelId = "gpt-4o", credentialAlias = "k1", displayName = "GPT-4o"))

        val id1 = ApiModelConfigurationId("cfg-1")
        val id2 = ApiModelConfigurationId("cfg-2")
        configDao.upsert(ApiModelConfigurationEntity(id1, credId, "Fast", pendingSync = 1))
        configDao.upsert(ApiModelConfigurationEntity(id2, credId, "Slow", pendingSync = 0))

        configDao.claimPending()
        val pending = configDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals(id1, pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `ApiModelConfigurationsDao markSynced resets pending_sync`() = runTest {
        val credDao = database.apiCredentialsDao()
        val configDao = database.apiModelConfigurationsDao()
        val credId = ApiCredentialsId(UUID.randomUUID().toString())
        credDao.upsert(ApiCredentialsEntity(id = credId, provider = ApiProvider.OPENAI, modelId = "gpt-4o", credentialAlias = "k2", displayName = "GPT-4o"))

        val id1 = ApiModelConfigurationId("cfg-mark-1")
        configDao.upsert(ApiModelConfigurationEntity(id1, credId, "Fast", pendingSync = 1))

        configDao.claimPending()
        configDao.markSynced(listOf(id1))

        assertEquals(0, configDao.getById(id1)!!.pendingSync)
    }

    // ========== Phase 1: LocalModelsDao ==========

    @Test
    fun `LocalModelsDao getClaimedForSync returns only claimed rows`() = runTest {
        val modelsDao = database.localModelsDao()
        val id1 = LocalModelId("lm-1")
        val id2 = LocalModelId("lm-2")
        modelsDao.upsert(LocalModelEntity(id1, ModelFileFormat.GGUF, "model-a", "a.gguf", "a.gguf", "sha-a", 1000L, pendingSync = 1))
        modelsDao.upsert(LocalModelEntity(id2, ModelFileFormat.GGUF, "model-b", "b.gguf", "b.gguf", "sha-b", 2000L, pendingSync = 0))

        modelsDao.claimPending()
        val pending = modelsDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals(id1, pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `LocalModelsDao markSynced resets pending_sync`() = runTest {
        val modelsDao = database.localModelsDao()
        val id1 = LocalModelId("lm-mark-1")
        modelsDao.upsert(LocalModelEntity(id1, ModelFileFormat.GGUF, "model-a", "a.gguf", "a.gguf", "sha-mark-a", 1000L, pendingSync = 1))

        modelsDao.claimPending()
        modelsDao.markSynced(listOf(id1))

        assertEquals(0, modelsDao.getById(id1)!!.pendingSync)
    }

    // ========== Phase 2: MemoriesDao ==========

    @Test
    fun `MemoriesDao getClaimedForSync returns only claimed rows`() = runTest {
        val memoriesDao = database.memoriesDao()
        memoriesDao.insertMemory(MemoriesEntity(id = "m1", category = MemoryCategory.FACTS, content = "Fact", pendingSync = 1))
        memoriesDao.insertMemory(MemoriesEntity(id = "m2", category = MemoryCategory.PREFERENCES, content = "Pref", pendingSync = 0))

        memoriesDao.claimPending()
        val pending = memoriesDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("m1", pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `MemoriesDao markSynced resets pending_sync`() = runTest {
        val memoriesDao = database.memoriesDao()
        memoriesDao.insertMemory(MemoriesEntity(id = "m-sync-1", category = MemoryCategory.FACTS, content = "Fact", pendingSync = 1))

        memoriesDao.claimPending()
        memoriesDao.markSynced(listOf("m-sync-1"))

        assertEquals(0, memoriesDao.getMemoryById("m-sync-1")!!.pendingSync)
    }

    @Test
    fun `MemoriesDao markSynced only resets listed ids`() = runTest {
        val memoriesDao = database.memoriesDao()
        memoriesDao.insertMemory(MemoriesEntity(id = "m-a", category = MemoryCategory.FACTS, content = "A", pendingSync = 1))
        memoriesDao.insertMemory(MemoriesEntity(id = "m-b", category = MemoryCategory.FACTS, content = "B", pendingSync = 1))

        memoriesDao.claimPending()
        memoriesDao.markSynced(listOf("m-a"))

        assertEquals(0, memoriesDao.getMemoryById("m-a")!!.pendingSync)
        assertEquals(2, memoriesDao.getMemoryById("m-b")!!.pendingSync)
    }

    // ========== Phase 2: WebSearchSourceDao ==========

    @Test
    fun `WebSearchSourceDao getClaimedForSync returns only claimed rows`() = runTest {
        val chatId = ChatId("chat-ws")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        val msgId = MessageId("msg-ws-1")
        messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
        val wsDao = database.webSearchSourceDao()
        wsDao.insertAll(listOf(
            WebSearchSourceEntity("ws-1", msgId, "Title A", "https://a.com", "Content A", 0.9, false, pendingSync = 1),
            WebSearchSourceEntity("ws-2", msgId, "Title B", "https://b.com", "Content B", 0.8, false, pendingSync = 0),
        ))

        wsDao.claimPending()
        val pending = wsDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("ws-1", pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `WebSearchSourceDao markSynced resets pending_sync`() = runTest {
        val chatId = ChatId("chat-ws-sync")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        val msgId = MessageId("msg-ws-sync-1")
        messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
        val wsDao = database.webSearchSourceDao()
        wsDao.insertAll(listOf(
            WebSearchSourceEntity("ws-sync-1", msgId, "Title", "https://c.com", "Content", 0.9, false, pendingSync = 1),
        ))

        wsDao.claimPending()
        wsDao.markSynced(listOf("ws-sync-1"))

        val sources = wsDao.getByMessageId(msgId)
        assertEquals(0, sources.first().pendingSync)
    }

    // ========== Phase 2: MessageFileDao ==========

    @Test
    fun `MessageFileDao getClaimedForSync returns only claimed rows`() = runTest {
        val chatId = ChatId("chat-f")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        val msgId = MessageId("msg-f-1")
        messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
        val fileDao = database.messageFileDao()
        fileDao.insert(MessageFileEntity("f-1", msgId.value, "file1.txt", "content1", pendingSync = 1))
        fileDao.insert(MessageFileEntity("f-2", msgId.value, "file2.txt", "content2", pendingSync = 0))

        fileDao.claimPending()
        val pending = fileDao.getClaimedForSync()
        assertEquals(1, pending.size)
        assertEquals("f-1", pending[0].id)
        assertEquals(2, pending[0].pendingSync)
    }

    @Test
    fun `MessageFileDao markSynced resets pending_sync`() = runTest {
        val chatId = ChatId("chat-f-sync")
        chatDao.upsert(ChatEntity(chatId, "Chat", Date(), Date(), false))
        val msgId = MessageId("msg-f-sync-1")
        messageDao.upsert(MessageEntity(id = msgId, chatId = chatId, content = "C", role = Role.USER))
        val fileDao = database.messageFileDao()
        fileDao.insert(MessageFileEntity("f-sync-1", msgId.value, "file.txt", "content", pendingSync = 1))

        fileDao.claimPending()
        fileDao.markSynced(listOf("f-sync-1"))

        val file = fileDao.getById("f-sync-1")!!
        assertEquals(0, file.pendingSync)
    }
}
