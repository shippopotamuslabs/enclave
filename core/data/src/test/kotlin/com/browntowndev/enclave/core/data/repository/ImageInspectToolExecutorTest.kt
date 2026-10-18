package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.usecase.chat.AnalyzeImageUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.inject.Provider
import kotlin.test.assertFailsWith

class ImageInspectToolExecutorTest {
    private fun analyzeImageUseCase(factory: FakeInferenceFactory) =
        AnalyzeImageUseCase(
            inferenceFactoryProvider = Provider { factory },
            loggingPort = FakeLoggingPort(),
        )

    private fun executor(
        messageRepository: FakeMessageRepository,
        factory: FakeInferenceFactory,
    ) = ImageInspectToolExecutor(
        loggingPort = FakeLoggingPort(),
        messageRepository = messageRepository,
        analyzeImageUseCase = analyzeImageUseCase(factory),
    )

    private fun factoryReturning(analysis: String): FakeInferenceFactory {
        val factory = FakeInferenceFactory()
        factory.llm.eventsToEmit =
            listOf(
                InferenceEvent.PartialResponse(analysis, ModelType.VISION),
                InferenceEvent.Finished(ModelType.VISION),
            )
        return factory
    }

    @Test
    fun `attached_image_inspect validates question`() =
        runTest {
            val executor = executor(FakeMessageRepository(), FakeInferenceFactory())

            assertFailsWith<IllegalArgumentException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = "{}",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }

    @Test
    fun `attached_image_inspect loads resolved URI and calls AnalyzeImageUseCase`() =
        runTest {
            val messageRepository = FakeMessageRepository()
            messageRepository.resolvedImageTarget =
                ResolvedImageTarget(
                    userMessageId = MessageId("msg-1"),
                    imageUris = listOf("file:///photo.jpg"),
                )
            val factory = factoryReturning("A detailed description of the image.")

            val executor = executor(messageRepository, factory)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"What color is the bicycle?"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                    ),
                )

            assertEquals(ToolDefinition.ATTACHED_IMAGE_INSPECT.name, result.toolName)
            val recorded = factory.llm.recordedPrompts.single()
            assertEquals(listOf("file:///photo.jpg"), recorded.options?.imageUris)
            assertTrue(recorded.prompt.endsWith("User request: What color is the bicycle?"))
        }

    @Test
    fun `attached_image_inspect falls back to latest image when user message id is unavailable`() =
        runTest {
            val messageRepository =
                FakeMessageRepository(
                    listOf(
                        Message(
                            id = MessageId("msg-2"),
                            chatId = ChatId("chat-1"),
                            content = Content(text = "What is in this image?", imageUris = listOf("file:///photo.jpg")),
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
                    ),
                )
            val factory = factoryReturning("A detailed description of the image.")

            val executor = executor(messageRepository, factory)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"What is in the image?"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                    ),
                )

            assertEquals(ToolDefinition.ATTACHED_IMAGE_INSPECT.name, result.toolName)
            val recorded = factory.llm.recordedPrompts.single()
            assertEquals(listOf("file:///photo.jpg"), recorded.options?.imageUris)
            assertTrue(recorded.prompt.endsWith("User request: What is in the image?"))
        }

    @Test
    fun `returns expected JSON payload with resolved message metadata and analysis`() =
        runTest {
            val messageRepository = FakeMessageRepository()
            messageRepository.resolvedImageTarget =
                ResolvedImageTarget(
                    userMessageId = MessageId("msg-1"),
                    imageUris = listOf("file:///photo.jpg"),
                )
            val factory = factoryReturning("A red bicycle by a blue wall.")

            val executor = executor(messageRepository, factory)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"Describe the image"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                    ),
                )

            val json = result.resultJson
            assertTrue(json.contains("\"resolved_message_id\":\"msg-1\""))
            assertTrue(json.contains("\"image_uris\":[\"file:///photo.jpg\"]"))
            assertTrue(json.contains("\"question\":\"Describe the image\""))
            assertTrue(json.contains("\"analysis\":\"A red bicycle by a blue wall.\""))
        }

    @Test
    fun `returns controlled error payload when vision analysis throws`() =
        runTest {
            val messageRepository = FakeMessageRepository()
            messageRepository.resolvedImageTarget =
                ResolvedImageTarget(
                    userMessageId = MessageId("msg-1"),
                    imageUris = listOf("file:///photo.jpg"),
                )
            val factory = FakeInferenceFactory()
            factory.invocationFailure = IllegalStateException("Vision API exploded")

            val executor = executor(messageRepository, factory)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"Describe the image"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                    ),
                )

            assertTrue(result.resultJson.contains("\"error\":\"vision_tool_failed\""))
            assertTrue(result.resultJson.contains("Vision API exploded"))
        }

    @Test
    fun `produces controlled failure when no image is resolvable`() =
        runTest {
            val messageRepository = FakeMessageRepository()
            val executor = executor(messageRepository, FakeInferenceFactory())

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"What is in the image?"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                    ),
                )

            val json = result.resultJson
            assertTrue(
                json.contains("error") || json.contains("no image"),
                "Expected controlled error payload but got: $json",
            )
        }

    @Test
    fun `search tool behavior remains unchanged - rejects unsupported tool name`() =
        runTest {
            val executor = executor(FakeMessageRepository(), FakeInferenceFactory())

            assertFailsWith<IllegalArgumentException> {
                executor.execute(
                    ToolCallRequest(
                        toolName = "tavily_web_search",
                        argumentsJson = """{"query":"test"}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                    ),
                )
            }
        }

    @Test
    fun `handles special characters in question and analysis`() =
        runTest {
            val messageRepository = FakeMessageRepository()
            messageRepository.resolvedImageTarget =
                ResolvedImageTarget(
                    userMessageId = MessageId("msg-1"),
                    imageUris = listOf("file:///photo.jpg"),
                )
            val factory = factoryReturning("Analysis with \"quotes\" and\nnewlines.")

            val executor = executor(messageRepository, factory)

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                        argumentsJson = """{"question":"Question with \"quotes\" and\nnewlines."}""",
                        provider = "OPENAI",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                    ),
                )

            val json = result.resultJson
            // Verify it is valid JSON and contains the unescaped characters when read back
            val jsonObject =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(json)
                    .jsonObject
            assertEquals("Question with \"quotes\" and\nnewlines.", jsonObject["question"]!!.jsonPrimitive.content)
            assertEquals("Analysis with \"quotes\" and\nnewlines.", jsonObject["analysis"]!!.jsonPrimitive.content)
        }
}
