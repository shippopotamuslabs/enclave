package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.GenerateImageToolParams
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateVideoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaToolExecutorTest {

    private val generateImageUseCase = mockk<GenerateImageUseCase>()
    private val generateVideoUseCase = mockk<GenerateVideoUseCase>()
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>()
    private val studioRepository = mockk<StudioRepositoryPort>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private val executor = MediaToolExecutor(
        generateImageUseCase,
        generateVideoUseCase,
        generateMusicUseCase,
        studioRepository,
        loggingPort
    )

    @Test
    fun `execute image generation success`() = runTest {
        val request = ToolCallRequest(
            toolName = ToolDefinition.GENERATE_IMAGE.name,
            argumentsJson = "{\"prompt\":\"a sunset\",\"count\":1}",
            provider = "replicate",
            modelType = ModelType.FAST,
            chatId = ChatId("chat-123"),
            userMessageId = MessageId("msg-123")
        )
        val imageBytes = byteArrayOf(1, 2, 3)
        val asset = StudioMediaAsset(
            id = "1",
            localUri = "file:///temp/1.jpg",
            prompt = "a sunset",
            mediaType = "IMAGE",
            createdAt = 0
        )

        coEvery { generateImageUseCase(any(), any()) } returns Result.success(listOf(imageBytes))
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///temp/1.jpg"
        coEvery { studioRepository.addEphemeralMedia(any()) } returns Unit
        
        val result = executor.execute(request)

        assertTrue(result.resultJson.contains("\"success\":true"))
        assertTrue(result.resultJson.contains("file:///temp/1.jpg"))
        coVerify { studioRepository.cacheEphemeralMedia(imageBytes, "IMAGE") }
        coVerify { studioRepository.addEphemeralMedia(any()) }
    }
}
