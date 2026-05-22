package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.artifact.MimeType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GenerateArtifactParamsTest {
    @Test
    fun `default values`() {
        val params = GenerateArtifactParams(title = "Title", content = "Content")
        assertEquals(MimeType.PDF, params.targetFormat)
        assertEquals("Title", params.title)
        assertEquals("Content", params.content)
    }

    @Test
    fun `toRequest maps title and content and leaves targetFormat unset for user preference`() {
        val params =
            GenerateArtifactParams(
                targetFormat = MimeType.PDF,
                title = "Title",
                content = "Content",
            )
        val request = params.toRequest()
        assertNull(request.targetFormat)
        assertEquals("legacy_${params.title.hashCode()}", request.artifactId)
        assertEquals("Title", request.title)
        assertEquals("Content", request.content)
    }

    @Test
    fun `toRequest ignores model Word hint for persisted user preference`() {
        val params =
            GenerateArtifactParams(
                targetFormat = MimeType.WORD,
                title = "Report",
                content = "Content",
            )
        val request = params.toRequest()
        assertNull(request.targetFormat)
        assertEquals("legacy_${params.title.hashCode()}", request.artifactId)
        assertEquals("Report", request.title)
        assertEquals("Content", request.content)
    }

    // Regression: emitToolSideEffects reads "artifactId" (camelCase) from append_blocks argumentsJson.
    // If AppendBlocksParams ever gains a @SerialName("artifact_id"), the artifact event breaks silently.
    @Test
    fun cl_tr_08_append_blocks_params_serializes_camel_case_keys() {
        val params =
            AppendBlocksParams(
                artifactId = "test-uuid-123",
                blocks = emptyList(),
                isComplete = true,
            )
        val json = Json.encodeToString(AppendBlocksParams.serializer(), params)
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("test-uuid-123", obj["artifactId"]?.jsonPrimitive?.content, "CL-TR-08 artifactId")
        assertNull(obj["artifact_id"], "CL-TR-08 no artifact_id key")
    }

    @Test
    fun `AppendBlocksParams serializes artifactId as camelCase key not snake_case`() {
        val params =
            AppendBlocksParams(
                artifactId = "test-uuid-123",
                blocks = emptyList(),
                isComplete = true,
            )
        val json = Json.encodeToString(AppendBlocksParams.serializer(), params)
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(
            "test-uuid-123",
            obj["artifactId"]?.jsonPrimitive?.content,
            "artifactId must serialize as camelCase so emitToolSideEffects can read it",
        )
        assertNull(obj["artifact_id"], "snake_case key must not exist")
    }
}
