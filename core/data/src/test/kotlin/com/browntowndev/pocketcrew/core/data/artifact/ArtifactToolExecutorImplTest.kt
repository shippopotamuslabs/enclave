package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactToolExecutorImplTest {
    private val executor = ArtifactToolExecutor(
        documentDao = mockk(relaxed = true),
        embeddingDao = mockk(relaxed = true),
        embeddingEngine = mockk(relaxed = true),
        externalScope = TestScope()
    )

    @Test
    fun `execute returns success for PDF`() = runTest {
        val request = ToolCallRequest(
            chatId = ChatId("chat-123"),
            userMessageId = MessageId("msg-123"),
            toolName = ToolDefinition.START_ARTIFACT.name,
            argumentsJson = """{"title":"Report","targetFormat":"PDF"}""",
            provider = "test",
            modelType = ModelType.FAST
        )
        val result = executor.execute(request)
        assertTrue(result.resultJson.contains("\"status\":\"success\""))
        assertTrue(result.resultJson.contains("\"artifactId\""))
        assertTrue(result.resultJson.contains("append blocks"))
    }

    @Test
    fun `execute returns success for Word`() = runTest {
        val request = ToolCallRequest(
            chatId = ChatId("chat-123"),
            userMessageId = MessageId("msg-123"),
            toolName = ToolDefinition.START_ARTIFACT.name,
            argumentsJson = """{"title":"Report","targetFormat":"WORD"}""",
            provider = "test",
            modelType = ModelType.FAST
        )
        val result = executor.execute(request)
        assertTrue(result.resultJson.contains("\"status\":\"success\""))
        assertTrue(result.resultJson.contains("\"artifactId\""))
        assertTrue(result.resultJson.contains("append blocks"))
    }
}