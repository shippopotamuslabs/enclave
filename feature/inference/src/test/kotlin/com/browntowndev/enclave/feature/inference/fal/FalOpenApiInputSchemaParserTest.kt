package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ImageInputRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FalOpenApiInputSchemaParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_extractsVideoEnumsFromInputSchema() {
        val document =
            buildJsonObject {
                putJsonObject("components") {
                    putJsonObject("schemas") {
                        putJsonObject("Input") {
                            putJsonObject("properties") {
                                putJsonObject("prompt") { put("type", "string") }
                                putJsonObject("duration") {
                                    putJsonArray("enum") {
                                        add(json.parseToJsonElement("\"5s\""))
                                        add(json.parseToJsonElement("\"10s\""))
                                    }
                                }
                                putJsonObject("resolution") {
                                    putJsonArray("enum") {
                                        add(json.parseToJsonElement("\"720p\""))
                                        add(json.parseToJsonElement("\"1080p\""))
                                    }
                                }
                                putJsonObject("aspect_ratio") {
                                    putJsonArray("enum") {
                                        add(json.parseToJsonElement("\"16:9\""))
                                        add(json.parseToJsonElement("\"9:16\""))
                                    }
                                }
                                putJsonObject("image_url") { put("type", "string") }
                            }
                        }
                    }
                }
            }

        val schema = FalOpenApiInputSchemaParser.parse(document)

        assertNotNull(schema)
        assertEquals(listOf("5s", "10s"), schema!!.videoDurationTokens)
        assertEquals(listOf("720p", "1080p"), schema.resolutionOptions)
        assertEquals(listOf("16:9", "9:16"), schema.aspectRatioOptions)
        assertEquals(ImageInputRequirement.OPTIONAL, schema.imageInput)
    }

    @Test
    fun parse_returnsNullWhenNoPromptProperty() {
        val document =
            buildJsonObject {
                putJsonObject("components") {
                    putJsonObject("schemas") {
                        putJsonObject("Output") {
                            putJsonObject("properties") {
                                putJsonObject("video") { put("type", "string") }
                            }
                        }
                    }
                }
            }

        assertEquals(null, FalOpenApiInputSchemaParser.parse(document))
    }
}
