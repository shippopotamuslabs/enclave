package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.TavilySourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMappersTest {

    @Test
    fun `TavilySourceEntity toDomain maps extracted field`() {
        val entity = TavilySourceEntity(
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
    fun `TavilySourceEntity toDomain defaults extracted to false`() {
        val entity = TavilySourceEntity(
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
    fun `TavilySource toEntity maps extracted field`() {
        val domain = TavilySource(
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
    fun `TavilySource toEntity defaults extracted to false`() {
        val domain = TavilySource(
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
        val original = TavilySource(
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
}
