package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaToolDefinitionFactoryTest {
    private val getProviderCapabilitiesUseCase = mockk<GetProviderCapabilitiesUseCase>()
    private val factory = MediaToolDefinitionFactory(getProviderCapabilitiesUseCase)

    @Test
    fun `buildImageToolDefinition creates restricted schema for OpenAI`() {
        val caps =
            ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE),
                supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsReferenceImage = true,
                supportsVideo = false,
            )
        every { getProviderCapabilitiesUseCase("openai") } returns caps

        val toolDef = factory.buildImageToolDefinition("openai")

        val schema = toolDef.schema
        val properties = schema.properties

        // Check aspect_ratio enum
        val aspectRatioEnum = properties["aspect_ratio"]!!.jsonObject["enum"]!!.jsonArray
        assertEquals(2, aspectRatioEnum.size)
        assertTrue(aspectRatioEnum.any { it.jsonPrimitive.content == "1:1" })
        assertTrue(aspectRatioEnum.any { it.jsonPrimitive.content == "16:9" })

        // Check quality enum
        val qualityEnum = properties["quality"]!!.jsonObject["enum"]!!.jsonArray
        assertEquals(2, qualityEnum.size)
        assertTrue(qualityEnum.any { it.jsonPrimitive.content == "speed" })
        assertTrue(qualityEnum.any { it.jsonPrimitive.content == "hd" })
    }

    @Test
    fun `buildVideoToolDefinition creates restricted schema for Google`() {
        val caps =
            ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.SIXTEEN_NINE),
                supportedImageQualities = emptyList(),
                supportedVideoQualities = listOf(GenerationQuality.QUALITY),
                supportedVideoResolutions = listOf("1080p"),
                supportedVideoDurations = listOf(6, 12),
                supportsReferenceImage = true,
                supportsVideo = true,
            )
        every { getProviderCapabilitiesUseCase("google") } returns caps

        val toolDef = factory.buildVideoToolDefinition("google")

        val schema = toolDef.schema
        val properties = schema.properties

        // Check duration enum
        val durationEnum = properties["duration"]!!.jsonObject["enum"]!!.jsonArray
        assertEquals(2, durationEnum.size)
        assertTrue(durationEnum.any { it.jsonPrimitive.content == "6" })
        assertTrue(durationEnum.any { it.jsonPrimitive.content == "12" })

        // Check resolution enum
        val resolutionEnum = properties["resolution"]!!.jsonObject["enum"]!!.jsonArray
        assertEquals(1, resolutionEnum.size)
        assertEquals("1080p", resolutionEnum[0].jsonPrimitive.content)
    }
}
