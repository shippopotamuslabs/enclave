package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmToolingOrchestratorAutoFinalizeTest {

    private class FakeToolExecutor : ToolExecutorPort {
        val executed = mutableListOf<ToolCallRequest>()

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            executed.add(request)
            return when (request.toolName) {
                ToolDefinition.START_ARTIFACT.name ->
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = """{"status":"success","artifactId":"art-1","message":"ok"}""",
                    )
                ToolDefinition.APPEND_BLOCKS.name ->
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson =
                            """{"status":"success","totalBlocks":0,"isComplete":true,"message":"done"}""",
                    )
                else -> error("unexpected tool ${request.toolName}")
            }
        }
    }

    @Test
    fun `synthesizes final append_blocks when model stops without isComplete`() = runTest {
        val fake = FakeToolExecutor()
        val logging = mockk<LoggingPort>(relaxed = true)
        val orchestrator = LlmToolingOrchestrator(fake, logging)

        var inferencePass = 0
        orchestrator.execute(
            providerName = "test",
            initialParams = Unit,
            tag = "LlmToolingOrchestratorAutoFinalizeTest",
            maxToolCalls = 20,
            onInferencePass = { _, _ ->
                inferencePass++
                Unit
            },
            onToolCallDetected = { _ ->
                when (inferencePass) {
                    1 ->
                        listOf(
                            ToolCallRequest(
                                toolName = ToolDefinition.START_ARTIFACT.name,
                                argumentsJson = """{"title":"T"}""",
                                provider = "test",
                                modelType = ModelType.FAST,
                                chatId = ChatId("c1"),
                                userMessageId = MessageId("u1"),
                                assistantMessageId = MessageId("a1"),
                            ),
                        )
                    else -> emptyList()
                }
            },
            onToolResultsMapped = { params, _, _ -> params },
            onContextExceeded = { _, _ -> ContextExceededResult(false) },
            onToolResult = { _, _ -> },
            onNoToolCallOnFirstPass = {},
            onFinished = { _, _, _ -> },
        )

        assertEquals(2, fake.executed.size)
        val synth = fake.executed[1]
        assertEquals(ToolDefinition.APPEND_BLOCKS.name, synth.toolName)
        assertTrue(synth.argumentsJson.contains("\"isComplete\":true"))
        assertTrue(synth.argumentsJson.contains("art-1"))
    }
}
