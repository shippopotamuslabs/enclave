package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappersTest {

    @Test
    fun `WebSearchSourceEntity toDomain maps extracted field`() {
        val entity = WebSearchSourceEntity(
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
        val entity = WebSearchSourceEntity(
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
        val domain = WebSearchSource(
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
        val domain = WebSearchSource(
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
        val original = WebSearchSource(
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

        val entity = MessageEntity(
            id = MessageId("msg-1"),
            chatId = ChatId("chat-1"),
            content = "Hello",
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            generatedMediaUrisJson = Json.encodeToString(uris),
            generatedMediaAssetIdsJson = Json.encodeToString(assetIds),
            generatedMediaTypesJson = Json.encodeToString(types),
            generatedMediaPromptsJson = Json.encodeToString(prompts)
        )

        val domain = entity.toDomain()

        assertEquals(uris, domain.content.generatedMediaUris)
        assertEquals(assetIds, domain.content.generatedMediaAssetIds)
        assertEquals(types, domain.content.generatedMediaTypes)
        assertEquals(prompts, domain.content.generatedMediaPrompts)
    }

    @Test
    fun `MessageEntity toDomain handles null media fields with empty lists`() {
        val entity = MessageEntity(
            id = MessageId("msg-1"),
            chatId = ChatId("chat-1"),
            content = "Hello",
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            generatedMediaUrisJson = null,
            generatedMediaAssetIdsJson = null,
            generatedMediaTypesJson = null,
            generatedMediaPromptsJson = null
        )

        val domain = entity.toDomain()

        assertTrue(domain.content.generatedMediaUris.isEmpty())
        assertTrue(domain.content.generatedMediaAssetIds.isEmpty())
        assertTrue(domain.content.generatedMediaTypes.isEmpty())
        assertTrue(domain.content.generatedMediaPrompts.isEmpty())
    }

    @Test
    fun `Message toEntity correctly encodes media metadata lists`() {
        val uris = listOf("uri1")
        val assetIds = listOf("id1")
        val types = listOf("IMAGE")
        val prompts = listOf("prompt1")

        val domain = Message(
            id = MessageId("msg-1"),
            chatId = ChatId("chat-1"),
            content = Content(
                text = "Hello",
                generatedMediaUris = uris,
                generatedMediaAssetIds = assetIds,
                generatedMediaTypes = types,
                generatedMediaPrompts = prompts
            ),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            modelType = ModelType.FAST
        )

        val entity = domain.toEntity()

        assertEquals(Json.encodeToString(uris), entity.generatedMediaUrisJson)
        assertEquals(Json.encodeToString(assetIds), entity.generatedMediaAssetIdsJson)
        assertEquals(Json.encodeToString(types), entity.generatedMediaTypesJson)
        assertEquals(Json.encodeToString(prompts), entity.generatedMediaPromptsJson)
    }

    @Test
    fun `Message mapping round-trip preserves media metadata`() {
        val original = Message(
            id = MessageId("msg-1"),
            chatId = ChatId("chat-1"),
            content = Content(
                text = "Hello",
                generatedMediaUris = listOf("uri1", "uri2"),
                generatedMediaAssetIds = listOf("id1", "id2"),
                generatedMediaTypes = listOf("IMAGE", "IMAGE"),
                generatedMediaPrompts = listOf("p1", "p2")
            ),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            modelType = ModelType.FAST
        )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(original.content.generatedMediaUris, roundTripped.content.generatedMediaUris)
        assertEquals(original.content.generatedMediaAssetIds, roundTripped.content.generatedMediaAssetIds)
        assertEquals(original.content.generatedMediaTypes, roundTripped.content.generatedMediaTypes)
        assertEquals(original.content.generatedMediaPrompts, roundTripped.content.generatedMediaPrompts)
    }
}
