package com.browntowndev.enclave.core.data.mapper

import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.MessageWithFiles
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.mapper.chat.toDomain
import com.browntowndev.enclave.core.data.mapper.message.toDomain
import com.browntowndev.enclave.core.data.mapper.message.toEntity
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class ChatMappersTest {
    // region isSyncing derivation tests

    @Test
    fun `toDomain placeholderRow setsIsSyncingTrue`() {
        val entity =
            ChatEntity(
                id = ChatId("chat-placeholder"),
                name = "Syncing...",
                created = Date(0),
                lastModified = Date(0),
                pinned = false,
                pendingSync = 0,
                updatedAt = 0L,
            )

        val domain = entity.toDomain()

        assertTrue(domain.isSyncing, "Expected isSyncing=true for placeholder row")
    }

    @Test
    fun `toDomain realLocalRow isSyncingFalse`() {
        val entity =
            ChatEntity(
                id = ChatId("chat-local"),
                name = "My Chat",
                created = Date(1_000_000L),
                lastModified = Date(1_000_000L),
                pinned = false,
                pendingSync = 1,
                updatedAt = 1_000_000L,
            )

        val domain = entity.toDomain()

        assertFalse(domain.isSyncing, "Expected isSyncing=false for real local row (pendingSync=1)")
    }

    @Test
    fun `toDomain realSyncedRow isSyncingFalse`() {
        val entity =
            ChatEntity(
                id = ChatId("chat-synced"),
                name = "Synced Chat",
                created = Date(2_000_000L),
                lastModified = Date(2_000_000L),
                pinned = false,
                pendingSync = 0,
                updatedAt = 2_000_000L,
            )

        val domain = entity.toDomain()

        assertFalse(domain.isSyncing, "Expected isSyncing=false for synced row (updatedAt > 0)")
    }

    @Test
    fun `toDomain oldSyncedRowMissingUpdatedAt isSyncingFalse`() {
        // Edge case: old Firestore chat doc missing updated_at field, defaults to 0L
        // But created timestamp is real, so should NOT be marked as syncing
        val entity =
            ChatEntity(
                id = ChatId("chat-old-synced"),
                name = "Old Synced Chat",
                created = Date(1_500_000L),
                lastModified = Date(1_500_000L),
                pinned = false,
                pendingSync = 0,
                updatedAt = 0L, // Missing field, defaulted to 0L
            )

        val domain = entity.toDomain()

        assertFalse(domain.isSyncing, "Expected isSyncing=false for old synced row with real created timestamp (created.time > 0)")
    }

    // endregion

    @Test
    fun `WebSearchSourceEntity toDomain maps extracted field`() {
        val entity =
            WebSearchSourceEntity(
                id = "src-1",
                messageId = MessageId("msg-1"),
                title = "Test",
                url = "https://example.com",
                content = "Content",
                score = 0.9,
                extracted = true,
            )

        val domain = entity.toDomain()

        assertEquals(true, domain.extracted)
        assertEquals("src-1", domain.id)
        assertEquals("Test", domain.title)
    }

    @Test
    fun `WebSearchSourceEntity toDomain defaults extracted to false`() {
        val entity =
            WebSearchSourceEntity(
                id = "src-2",
                messageId = MessageId("msg-1"),
                title = "Test",
                url = "https://example.com",
                content = "Content",
                score = 0.5,
                extracted = false,
            )

        val domain = entity.toDomain()

        assertEquals(false, domain.extracted)
    }

    @Test
    fun `WebSearchSource toEntity maps extracted field`() {
        val domain =
            WebSearchSource(
                id = "src-3",
                messageId = MessageId("msg-1"),
                title = "Test",
                url = "https://example.com",
                content = "Content",
                score = 0.8,
                extracted = true,
            )

        val entity = domain.toEntity()

        assertEquals(true, entity.extracted)
        assertEquals("src-3", entity.id)
        assertEquals("Test", entity.title)
    }

    @Test
    fun `WebSearchSource toEntity defaults extracted to false`() {
        val domain =
            WebSearchSource(
                id = "src-4",
                messageId = MessageId("msg-1"),
                title = "Test",
                url = "https://example.com",
                content = "Content",
                score = 0.3,
            )

        val entity = domain.toEntity()

        assertEquals(false, entity.extracted)
    }

    @Test
    fun `round-trip preserves extracted true`() {
        val original =
            WebSearchSource(
                id = "src-5",
                messageId = MessageId("msg-1"),
                title = "Round Trip",
                url = "https://round.com",
                content = "Round",
                score = 1.0,
                extracted = true,
            )

        val entity = original.toEntity()
        val roundTripped = entity.toDomain()

        assertEquals(original.extracted, roundTripped.extracted)
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.url, roundTripped.url)
    }

    @Test
    fun `MessageEntity toDomain correctly decodes media metadata lists`() {
        val uris = listOf("uri1", "uri2")
        val assetIds = listOf("id1", "id2")
        val types = listOf("IMAGE", "VIDEO")
        val prompts = listOf("prompt1", "prompt2")

        val entity =
            MessageEntity(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = "Hello",
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                generatedMediaUrisJson = Json.encodeToString(uris),
                generatedMediaAssetIdsJson = Json.encodeToString(assetIds),
                generatedMediaTypesJson = Json.encodeToString(types),
                generatedMediaPromptsJson = Json.encodeToString(prompts),
            )

        val domain = entity.toDomain()

        assertEquals(uris, domain.content.generatedMediaUris)
        assertEquals(assetIds, domain.content.generatedMediaAssetIds)
        assertEquals(types, domain.content.generatedMediaTypes)
        assertEquals(prompts, domain.content.generatedMediaPrompts)
    }

    @Test
    fun `MessageEntity toDomain handles null media fields with empty lists`() {
        val entity =
            MessageEntity(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = "Hello",
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                generatedMediaUrisJson = null,
                generatedMediaAssetIdsJson = null,
                generatedMediaTypesJson = null,
                generatedMediaPromptsJson = null,
            )

        val domain = entity.toDomain()

        assertTrue(domain.content.generatedMediaUris.isEmpty())
        assertTrue(domain.content.generatedMediaAssetIds.isEmpty())
        assertTrue(domain.content.generatedMediaTypes.isEmpty())
        assertTrue(domain.content.generatedMediaPrompts.isEmpty())
    }

    @Test
    fun `MessageEntity toDomain exposes updatedAt`() {
        val entity =
            MessageEntity(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = "Hello",
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                updatedAt = 1234L,
            )

        val domain = entity.toDomain()

        assertEquals(1234L, domain.updatedAt)
    }

    @Test
    fun `MessageWithFiles toDomain uses child updatedAt for display aggregate`() {
        val messageId = MessageId("msg-1")
        val entity =
            MessageEntity(
                id = messageId,
                chatId = ChatId("chat-1"),
                content = "Hello",
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                updatedAt = 10L,
            )
        val row =
            MessageWithFiles(
                message = entity,
                files =
                    listOf(
                        MessageFileEntity(
                            id = "file-1",
                            messageId = messageId.value,
                            name = "file.txt",
                            content = "file",
                            mimeType = MimeType.PLAIN_TEXT,
                            updatedAt = 20L,
                        ),
                    ),
            )
        val sources =
            listOf(
                WebSearchSourceEntity(
                    id = "source-1",
                    messageId = messageId,
                    title = "Title",
                    url = "https://example.com",
                    content = "Snippet",
                    score = 1.0,
                    updatedAt = 30L,
                ),
            )
        val documents =
            listOf(
                DocumentEntity(
                    id = "doc-1",
                    messageId = messageId,
                    json = "{}",
                    title = "Doc",
                    updatedAt = 40L,
                ),
            )

        val domain = row.toDomain(webSearchSources = sources, documents = documents)

        assertEquals(40L, domain.updatedAt)
    }

    @Test
    fun `Message toEntity correctly encodes media metadata lists`() {
        val uris = listOf("uri1")
        val assetIds = listOf("id1")
        val types = listOf("IMAGE")
        val prompts = listOf("prompt1")

        val domain =
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content =
                    Content(
                        text = "Hello",
                        generatedMediaUris = uris,
                        generatedMediaAssetIds = assetIds,
                        generatedMediaTypes = types,
                        generatedMediaPrompts = prompts,
                    ),
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                modelType = ModelType.FAST,
            )

        val entity = domain.toEntity()

        assertEquals(Json.encodeToString(uris), entity.generatedMediaUrisJson)
        assertEquals(Json.encodeToString(assetIds), entity.generatedMediaAssetIdsJson)
        assertEquals(Json.encodeToString(types), entity.generatedMediaTypesJson)
        assertEquals(Json.encodeToString(prompts), entity.generatedMediaPromptsJson)
    }

    @Test
    fun `Message toEntity preserves updatedAt when provided`() {
        val domain =
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = Content(text = "Hello"),
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                updatedAt = 4567L,
            )

        val entity = domain.toEntity()

        assertEquals(4567L, entity.updatedAt)
    }

    @Test
    fun `Message mapping round-trip preserves media metadata`() {
        val original =
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content =
                    Content(
                        text = "Hello",
                        generatedMediaUris = listOf("uri1", "uri2"),
                        generatedMediaAssetIds = listOf("id1", "id2"),
                        generatedMediaTypes = listOf("IMAGE", "IMAGE"),
                        generatedMediaPrompts = listOf("p1", "p2"),
                    ),
                role = Role.ASSISTANT,
                messageState = MessageState.COMPLETE,
                modelType = ModelType.FAST,
            )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(original.content.generatedMediaUris, roundTripped.content.generatedMediaUris)
        assertEquals(original.content.generatedMediaAssetIds, roundTripped.content.generatedMediaAssetIds)
        assertEquals(original.content.generatedMediaTypes, roundTripped.content.generatedMediaTypes)
        assertEquals(original.content.generatedMediaPrompts, roundTripped.content.generatedMediaPrompts)
    }
}
