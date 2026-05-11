package com.browntowndev.pocketcrew.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StartArtifactParamsArgumentNormalizerTest {

    @Test
    fun `decodeArguments lifts title and author from metadata`() {
        val raw =
            """
            {"blocks":[],"metadata":{"title":"The Iron Covenant","author":"ChatGPT","format":"Word"},"version":"1.0"}
            """.trimIndent()
        val decoded = ToolDefinition.START_ARTIFACT.decodeArguments<StartArtifactParams>(raw)
        assertEquals("The Iron Covenant", decoded.title)
        assertEquals("ChatGPT", decoded.author)
    }

    @Test
    fun `decodeArguments accepts flat schema`() {
        val raw = """{"title":"T","author":"A"}"""
        val decoded = ToolDefinition.START_ARTIFACT.decodeArguments<StartArtifactParams>(raw)
        assertEquals("T", decoded.title)
        assertEquals("A", decoded.author)
    }

    @Test
    fun `decodeArguments ignores legacy targetFormat in payload`() {
        val raw = """{"title":"T","targetFormat":"WORD"}"""
        val decoded = ToolDefinition.START_ARTIFACT.decodeArguments<StartArtifactParams>(raw)
        assertEquals("T", decoded.title)
        assertNull(decoded.author)
    }

    @Test
    fun `root title wins over metadata title`() {
        val raw = """{"title":"Root","metadata":{"title":"Meta"}}"""
        val decoded = ToolDefinition.START_ARTIFACT.decodeArguments<StartArtifactParams>(raw)
        assertEquals("Root", decoded.title)
    }

    @Test
    fun `metadata only title`() {
        val raw = """{"metadata":{"title":"X","format":"Word Document"}}"""
        val decoded = ToolDefinition.START_ARTIFACT.decodeArguments<StartArtifactParams>(raw)
        assertEquals("X", decoded.title)
    }
}
