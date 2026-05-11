package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppendBlocksToolResultParserTest {

    @Test
    fun `parseCompleteArtifact accepts nested fullDocument object`() {
        val raw =
            """
            {"status":"success","isComplete":true,"title":"My Doc","message":"done","totalBlocks":2,
            "fullDocument":{"version":"1.0","metadata":{"title":"My Doc"},"blocks":[{"type":"paragraph","text":"hi"}]}}
            """.trimIndent()

        val parsed = AppendBlocksToolResultParser.parseCompleteArtifact(raw)
        assertNotNull(parsed)
        assertEquals("My Doc", parsed!!.title)
        assertTrue(parsed.documentJson.contains("\"blocks\""))
        assertTrue(parsed.documentJson.contains("hi"))
    }

    @Test
    fun `parseCompleteArtifact accepts fullDocument as JSON string alternate wire shape`() {
        val innerDoc = """{"version":"1.0","metadata":{},"blocks":[]}"""
        val raw =
            """
            {"status":"success","isComplete":true,"title":"FromStringField","message":"done","totalBlocks":0,
            "fullDocument":${Json.encodeToString(JsonPrimitive(innerDoc))}}
            """.trimIndent()

        val parsed = AppendBlocksToolResultParser.parseCompleteArtifact(raw)
        assertNotNull(parsed)
        assertEquals("FromStringField", parsed!!.title)
        assertTrue(parsed.documentJson.contains("\"blocks\""))
    }

    @Test
    fun `parseCompleteArtifact unwraps double encoded root string`() {
        val inner =
            """
            {"status":"success","isComplete":true,"title":"Wrapped","message":"done","totalBlocks":0,
            "fullDocument":{"version":"1.0","metadata":{},"blocks":[]}}
            """.trimIndent()
        val wrapped = Json.encodeToString(JsonPrimitive(inner))

        val parsed = AppendBlocksToolResultParser.parseCompleteArtifact(wrapped)
        assertNotNull(parsed)
        assertEquals("Wrapped", parsed!!.title)
    }

    @Test
    fun `parseCompleteArtifact returns null when not complete`() {
        val raw =
            """
            {"status":"success","isComplete":false,"title":"X","message":"go on","totalBlocks":1}
            """.trimIndent()
        assertNull(AppendBlocksToolResultParser.parseCompleteArtifact(raw))
    }

    @Test
    fun `parseCompleteArtifact returns null on error status`() {
        val raw = """{"status":"error","message":"Artifact not found"}"""
        assertNull(AppendBlocksToolResultParser.parseCompleteArtifact(raw))
    }
}
