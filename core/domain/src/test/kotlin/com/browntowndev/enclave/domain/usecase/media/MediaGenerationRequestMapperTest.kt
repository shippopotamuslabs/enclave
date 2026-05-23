package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MediaGenerationRequestMapperTest {
    @Test
    fun `S-MAP-01 Image tool maps prompt aspect ratio quality count`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_IMAGE.name,
                argumentsJson = """{"prompt":"a sunset","aspect_ratio":"16:9","quality":"hd","count":2}""",
                provider = "replicate",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)

        assertEquals(MediaCapability.IMAGE, mapped.capability)
        assertEquals("a sunset", mapped.prompt)
        val settings = mapped.settings as ImageGenerationSettings
        assertEquals(AspectRatio.SIXTEEN_NINE, settings.aspectRatio)
        assertEquals(GenerationQuality.HD, settings.quality)
        assertEquals(2, settings.generationCount)
        assertEquals(MediaGenerationOrigin.CHAT_TOOL, mapped.origin)
    }

    @Test
    fun `S-MAP-02 Image tool unknown aspect ratio defaults to ONE_ONE`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_IMAGE.name,
                argumentsJson = """{"prompt":"x","aspect_ratio":"99:99","count":1}""",
                provider = "replicate",
                modelType = ModelType.FAST,
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)
        val settings = mapped.settings as ImageGenerationSettings

        assertEquals(AspectRatio.ONE_ONE, settings.aspectRatio)
    }

    @Test
    fun `S-MAP-03 Image tool count coerced to 4 when count is 99`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_IMAGE.name,
                argumentsJson = """{"prompt":"x","count":99}""",
                provider = "replicate",
                modelType = ModelType.FAST,
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)
        val settings = mapped.settings as ImageGenerationSettings

        assertEquals(4, settings.generationCount)
    }

    @Test
    fun `S-MAP-04 Video tool maps duration and resolution literally`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_VIDEO.name,
                argumentsJson = """{"prompt":"waves","duration":8,"resolution":"720p"}""",
                provider = "replicate",
                modelType = ModelType.FAST,
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)

        assertEquals(MediaCapability.VIDEO, mapped.capability)
        val settings = mapped.settings as VideoGenerationSettings
        assertEquals(8, settings.videoDuration)
        assertEquals("720p", settings.videoResolution)
    }

    @Test
    fun `S-MAP-05 Music tool maps duration literally`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_MUSIC.name,
                argumentsJson = """{"prompt":"lofi beat","duration":45}""",
                provider = "replicate",
                modelType = ModelType.FAST,
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)

        assertEquals(MediaCapability.MUSIC, mapped.capability)
        val settings = mapped.settings as MusicGenerationSettings
        assertEquals(45, settings.duration)
    }

    @Test
    fun `S-MAP-06 Studio mapping preserves settings and sets STUDIO_FOREGROUND origin`() {
        val settings = VideoGenerationSettings(videoDuration = 10, videoResolution = "1080p")

        val mapped =
            MediaGenerationRequestMapper.fromStudio(
                prompt = "studio prompt",
                capability = MediaCapability.VIDEO,
                settings = settings,
            )

        assertEquals(
            MediaGenerationRequest(
                prompt = "studio prompt",
                capability = MediaCapability.VIDEO,
                settings = settings,
                origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
            ),
            mapped,
        )
    }

    @Test
    fun `E-MAP-01 Unsupported tool name throws IllegalArgumentException`() {
        val request =
            ToolCallRequest(
                toolName = "web_search",
                argumentsJson = "{}",
                provider = "tavily",
                modelType = ModelType.FAST,
            )

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                MediaGenerationRequestMapper.fromToolCall(request)
            }

        assertEquals("Unsupported media tool: web_search", thrown.message)
    }

    @Test
    fun `E-MAP-02 Image tool count coerced to 1 when count is 0`() {
        val request =
            ToolCallRequest(
                toolName = ToolDefinition.GENERATE_IMAGE.name,
                argumentsJson = """{"prompt":"x","count":0}""",
                provider = "replicate",
                modelType = ModelType.FAST,
            )

        val mapped = MediaGenerationRequestMapper.fromToolCall(request)
        val settings = mapped.settings as ImageGenerationSettings

        assertEquals(1, settings.generationCount)
    }
}
