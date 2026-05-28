package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaToolExecutorTest {
    private val generateMediaUseCase = mockk<GenerateMediaUseCase>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private val executor =
        MediaToolExecutor(
            generateMediaUseCase = generateMediaUseCase,
            loggingPort = loggingPort,
        )

    @Test
    fun `S-CHAT-01 Image tool success JSON contains success true and success message`() =
        runTest {
            val request = imageToolRequest("""{"prompt":"a sunset","count":1}""")
            coEvery { generateMediaUseCase(any()) } returns
                Result.success(
                    listOf(
                        GeneratedMediaAsset(
                            id = "chat_media_abc",
                            localUri = "file:///x",
                            prompt = "a sunset",
                            mediaType = "IMAGE",
                            createdAt = 0,
                        ),
                    ),
                )

            val result = executor.execute(request)

            assertTrue(result.resultJson.contains("\"success\":true"))
            assertTrue(result.resultJson.contains("Media generated successfully and available in Studio."))
            assertTrue(result.resultJson.contains("file:///x"))
        }

    @Test
    fun `S-CHAT-02 Image tool failure JSON contains success false and provider error text`() =
        runTest {
            val request = imageToolRequest("""{"prompt":"a sunset","count":1}""")
            coEvery { generateMediaUseCase(any()) } returns
                Result.failure(IllegalStateException("No image generation provider assigned"))

            val result = executor.execute(request)

            assertTrue(result.resultJson.contains("\"success\":false"))
            assertTrue(result.resultJson.contains("No image generation provider assigned"))
        }

    @Test
    fun `S-CHAT-03 Success JSON includes asset_ids value with prefix chat_media_`() =
        runTest {
            val request = imageToolRequest("""{"prompt":"a sunset","count":1}""")
            coEvery { generateMediaUseCase(any()) } returns
                Result.success(
                    listOf(
                        GeneratedMediaAsset(
                            id = "chat_media_abc",
                            localUri = "file:///x",
                            prompt = "a sunset",
                            mediaType = "IMAGE",
                            createdAt = 0,
                        ),
                    ),
                )

            val result = executor.execute(request)

            assertTrue(result.resultJson.contains("chat_media_abc"))
        }

    @Test
    fun `S-CHAT-04 Executor never calls GenerateImageUseCase directly`() {
        val constructor = MediaToolExecutor::class.java.constructors.single()
        val parameterTypes = constructor.parameterTypes.map { it.name }

        assertFalse(parameterTypes.any { it.contains("GenerateImageUseCase") })
        assertFalse(MediaToolExecutor::class.java.declaredFields.any { it.type == GenerateImageUseCase::class.java })
    }

    @Test
    fun `E-CHAT-01 Unsupported tool throws IllegalArgumentException`() =
        runTest {
            val request =
                ToolCallRequest(
                    toolName = "unknown_tool",
                    argumentsJson = "{}",
                    provider = "replicate",
                    modelType = ModelType.FAST,
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                )

            val thrown =
                assertFailsWith<IllegalArgumentException> {
                    executor.execute(request)
                }

            assertEquals("Unsupported media tool: unknown_tool", thrown.message)
        }

    @Test
    fun `Image tool IOException failure JSON contains success false and Network error prefix`() =
        runTest {
            val request = imageToolRequest("""{"prompt":"a sunset","count":1}""")
            coEvery { generateMediaUseCase(any()) } returns
                Result.failure(java.io.IOException("Service Unavailable"))

            val result = executor.execute(request)

            assertTrue(result.resultJson.contains("\"success\":false"))
            assertTrue(result.resultJson.contains("Network error: Service Unavailable"))
        }

    @Test
    fun `Image tool direct IOException throwing failure JSON contains success false and Network error prefix`() =
        runTest {
            val request = imageToolRequest("""{"prompt":"a sunset","count":1}""")
            coEvery { generateMediaUseCase(any()) } throws java.io.IOException("Gateway timeout")

            val result = executor.execute(request)

            assertTrue(result.resultJson.contains("\"success\":false"))
            assertTrue(result.resultJson.contains("Network error: Gateway timeout"))
        }

    private fun imageToolRequest(argumentsJson: String): ToolCallRequest =
        ToolCallRequest(
            toolName = ToolDefinition.GENERATE_IMAGE.name,
            argumentsJson = argumentsJson,
            provider = "replicate",
            modelType = ModelType.FAST,
            chatId = ChatId("chat-1"),
            userMessageId = MessageId("msg-1"),
        )
}
