package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import com.browntowndev.enclave.domain.model.media.OutputMediaUrlPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalOpenApiOutputSchemaParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse extracts flat video_url from output schema`() {
        val openApi = cosmosVideoOpenApi()
        val schema = FalOpenApiOutputSchemaParser.parse(openApi)
        assertNotNull(schema)
        assertTrue(
            schema!!.mediaUrlPaths.any { path ->
                path.segments == listOf("video_url") && path.kind == OutputMediaKind.VIDEO
            },
        )
    }

    @Test
    fun `parse extracts nested video url from file ref schema`() {
        val openApi = seedanceVideoOpenApi()
        val schema = FalOpenApiOutputSchemaParser.parse(openApi)
        assertNotNull(schema)
        assertTrue(
            schema!!.mediaUrlPaths.any { path ->
                path.segments == listOf("video", "url") && path.kind == OutputMediaKind.VIDEO
            },
        )
    }

    @Test
    fun `parse ignores queue status schemas`() {
        val openApi =
            json.decodeFromString<JsonObject>(
                """
                {
                  "components": {
                    "schemas": {
                      "QueueStatus": {
                        "type": "object",
                        "properties": {
                          "status": { "type": "string" },
                          "request_id": { "type": "string" },
                          "status_url": { "type": "string" },
                          "response_url": { "type": "string" }
                        }
                      },
                      "VideoOutput": {
                        "type": "object",
                        "properties": {
                          "video_url": { "type": "string", "format": "uri" }
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
        val schema = FalOpenApiOutputSchemaParser.parse(openApi)
        assertNotNull(schema)
        assertEquals(listOf("video_url"), schema!!.mediaUrlPaths.single().segments)
    }

    @Test
    fun `parse returns null when no media output schemas`() {
        val openApi =
            json.decodeFromString<JsonObject>(
                """
                {
                  "components": {
                    "schemas": {
                      "Input": {
                        "type": "object",
                        "properties": {
                          "prompt": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
        assertNull(FalOpenApiOutputSchemaParser.parse(openApi))
    }

    @Test
    fun `schema path extraction prefers openapi path over generic walk`() {
        val response =
            json.decodeFromString<JsonObject>(
                """
                {
                  "image_url": "https://cdn.example.com/input-echo.jpg",
                  "video": { "url": "https://v3.fal.media/files/seedance-out.mp4" }
                }
                """.trimIndent(),
            )
        val schema =
            ModelGenerationOutputSchema(
                mediaUrlPaths =
                    listOf(
                        OutputMediaUrlPath(
                            segments = listOf("video", "url"),
                            kind = OutputMediaKind.VIDEO,
                        ),
                    ),
            )
        val urls = FalResponseMediaUrls.primaryVideoUrls(response, schema)
        assertEquals(listOf("https://v3.fal.media/files/seedance-out.mp4"), urls)
    }

    @Test
    fun `generic walk still resolves cosmos flat video_url without schema`() {
        val response =
            json.decodeFromString<JsonObject>(
                """
                { "video_url": "https://v3.fal.media/files/cosmos-out.mp4" }
                """.trimIndent(),
            )
        assertEquals(
            listOf("https://v3.fal.media/files/cosmos-out.mp4"),
            FalResponseMediaUrls.primaryVideoUrls(response, outputSchema = null),
        )
    }

    @Test
    fun `unwraps data wrapper before schema and generic extraction`() {
        val response =
            json.decodeFromString<JsonObject>(
                """
                {
                  "data": {
                    "video": { "url": "https://v3.fal.media/files/wrapped.mp4" }
                  }
                }
                """.trimIndent(),
            )
        val schema =
            ModelGenerationOutputSchema(
                mediaUrlPaths =
                    listOf(
                        OutputMediaUrlPath(
                            segments = listOf("video", "url"),
                            kind = OutputMediaKind.VIDEO,
                        ),
                    ),
            )
        assertEquals(
            listOf("https://v3.fal.media/files/wrapped.mp4"),
            FalResponseMediaUrls.primaryVideoUrls(response, schema),
        )
    }

    private fun cosmosVideoOpenApi(): JsonObject =
        json.decodeFromString(
            """
            {
              "components": {
                "schemas": {
                  "CosmosVideoOutput": {
                    "type": "object",
                    "properties": {
                      "video_url": { "type": "string", "format": "uri" }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

    private fun seedanceVideoOpenApi(): JsonObject =
        json.decodeFromString(
            """
            {
              "components": {
                "schemas": {
                  "SeedanceOutput": {
                    "type": "object",
                    "properties": {
                      "video": { "${'$'}ref": "#/components/schemas/File" }
                    }
                  },
                  "File": {
                    "type": "object",
                    "properties": {
                      "url": { "type": "string" },
                      "content_type": { "type": "string" }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
}