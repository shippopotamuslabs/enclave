package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ImageInspectToolExecutorTest {

    @Test
    fun `attached_image_inspect validates question`() = runTest {
        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = mockk(relaxed = true),
            analyzeImageUseCase = mockk(),
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                    argumentsJson = "{}",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `attached_image_inspect loads resolved URI and calls AnalyzeImageUseCase`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.resolveLatestImageBearingUserMessage(any(), any())
        } returns ResolvedImageTarget(
            userMessageId = MessageId("msg-1"),
            imageUri = "file:///photo.jpg",
        )

        val uriSlot = io.mockk.slot<String>()
        val promptSlot = io.mockk.slot<String>()
        val analyzeImageUseCase = mockk<com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase>()
        coEvery { analyzeImageUseCase(capture(uriSlot), capture(promptSlot)) } answers {
            "A detailed description of the image."
        }

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = analyzeImageUseCase,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"What color is the bicycle?"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        assertEquals(ToolDefinition.ATTACHED_IMAGE_INSPECT.name, result.toolName)
        assertEquals("file:///photo.jpg", uriSlot.captured)
        assertEquals("What color is the bicycle?", promptSlot.captured)
    }

    @Test
    fun `attached_image_inspect falls back to latest image when user message id is unavailable`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.getMessagesForChat(any())
        } returns listOf(
            Message(
                id = MessageId("msg-2"),
                chatId = ChatId("chat-1"),
                content = Content(text = "What is in this image?", imageUri = "file:///photo.jpg"),
                role = Role.USER,
                createdAt = 2L,
            ),
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = Content(text = "Earlier text only"),
                role = Role.USER,
                createdAt = 1L,
            ),
        )

        val uriSlot = io.mockk.slot<String>()
        val promptSlot = io.mockk.slot<String>()
        val analyzeImageUseCase = mockk<com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase>()
        coEvery { analyzeImageUseCase(capture(uriSlot), capture(promptSlot)) } returns "A detailed description of the image."

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = analyzeImageUseCase,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"What is in the image?"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
            )
        )

        assertEquals(ToolDefinition.ATTACHED_IMAGE_INSPECT.name, result.toolName)
        assertEquals("file:///photo.jpg", uriSlot.captured)
        assertEquals("What is in the image?", promptSlot.captured)
    }

    @Test
    fun `returns expected JSON payload with resolved message metadata and analysis`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.resolveLatestImageBearingUserMessage(any(), any())
        } returns ResolvedImageTarget(
            userMessageId = MessageId("msg-1"),
            imageUri = "file:///photo.jpg",
        )

        val analyzeImageUseCase = mockk<com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase>()
        coEvery { analyzeImageUseCase(any(), any()) } returns "A red bicycle by a blue wall."

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = analyzeImageUseCase,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"Describe the image"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        val json = result.resultJson
        assertTrue(json.contains("\"resolved_message_id\":\"msg-1\""))
        assertTrue(json.contains("\"image_uri\":\"file:///photo.jpg\""))
        assertTrue(json.contains("\"question\":\"Describe the image\""))
        assertTrue(json.contains("\"analysis\":\"A red bicycle by a blue wall.\""))
    }

    @Test
    fun `returns controlled error payload when vision analysis throws`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.resolveLatestImageBearingUserMessage(any(), any())
        } returns ResolvedImageTarget(
            userMessageId = MessageId("msg-1"),
            imageUri = "file:///photo.jpg",
        )

        val analyzeImageUseCase = mockk<com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase>()
        coEvery { analyzeImageUseCase(any(), any()) } throws IllegalStateException("Vision API exploded")

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = analyzeImageUseCase,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"Describe the image"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        assertTrue(result.resultJson.contains("\"error\":\"vision_tool_failed\""))
        assertTrue(result.resultJson.contains("Vision API exploded"))
    }

    @Test
    fun `produces controlled failure when no image is resolvable`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.getMessagesForChat(any())
        } returns emptyList()
        coEvery {
            messageRepository.resolveLatestImageBearingUserMessage(any(), any())
        } returns null

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = mockk(),
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"What is in the image?"}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        val json = result.resultJson
        assertTrue(json.contains("error") || json.contains("no image"),
            "Expected controlled error payload but got: $json")
    }

    @Test
    fun `search tool behavior remains unchanged - rejects unsupported tool name`() = runTest {
        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = mockk(relaxed = true),
            analyzeImageUseCase = mockk(),
        )

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                ToolCallRequest(
                    toolName = "tavily_web_search",
                    argumentsJson = """{"query":"test"}""",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            )
        }
    }

    @Test
    fun `handles special characters in question and analysis`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery {
            messageRepository.resolveLatestImageBearingUserMessage(any(), any())
        } returns ResolvedImageTarget(
            userMessageId = MessageId("msg-1"),
            imageUri = "file:///photo.jpg",
        )

        val analyzeImageUseCase = mockk<com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase>()
        coEvery { analyzeImageUseCase(any(), any()) } returns "Analysis with \"quotes\" and\nnewlines."

        val executor = ImageInspectToolExecutor(
            loggingPort = mockk(relaxed = true),
            messageRepository = messageRepository,
            analyzeImageUseCase = analyzeImageUseCase,
        )

        val result = executor.execute(
            ToolCallRequest(
                toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                argumentsJson = """{"question":"Question with \"quotes\" and\nnewlines."}""",
                provider = "OPENAI",
                modelType = ModelType.FAST,
                chatId = ChatId("chat-1"),
                userMessageId = MessageId("msg-1"),
            )
        )

        val json = result.resultJson
        // Verify it is valid JSON and contains the unescaped characters when read back
        val jsonObject = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        assertEquals("Question with \"quotes\" and\nnewlines.", jsonObject["question"]!!.jsonPrimitive.content)
        assertEquals("Analysis with \"quotes\" and\nnewlines.", jsonObject["analysis"]!!.jsonPrimitive.content)
    }
}
