package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
import com.openai.client.OpenAIClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * TOOL-PARSE-002 K-05, MD-03, MD-04 — mutation defense (must fail until Phase D migration).
 */
class ToolParse002MutationDefenseTest {

    private val inferenceMainRoot: Path = Path.of(
        "src/main/kotlin/com/browntowndev/pocketcrew/feature/inference",
    )

    @Test
    fun k05SendPromptUsesToolStreamGatePortNotAgentTurnTextGate() = runTest {
        val fakeGate = FakeToolStreamGatePort()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = StreamGateProbeService(
            client = mockk(relaxed = true),
            loggingPort = loggingPort,
            fakeGate = fakeGate,
        )

        service.sendPrompt(
            prompt = "hello",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        assertTrue(
            fakeGate.pushCalls.isNotEmpty(),
            "K-05: sendPrompt must route PartialResponse chunks through ToolStreamGatePort",
        )
    }

    @Test
    fun md03AgentTurnTextGateSourceAbsent() {
        val gateFile = inferenceMainRoot.resolve("AgentTurnTextGate.kt")
        assertFalse(
            Files.exists(gateFile),
            "MD-03: AgentTurnTextGate.kt must be deleted after migration",
        )
        val sources = Files.walk(inferenceMainRoot)
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
            .toList()
        assertEquals(
            0,
            sources.count { it.contains("class AgentTurnTextGate") },
            "MD-03: no AgentTurnTextGate class in feature/inference",
        )
    }

    @Test
    fun md04ToolEnvelopeFailureMapperAbsent() {
        val mapperFile = inferenceMainRoot.resolve("ToolEnvelopeFailureMapper.kt")
        assertFalse(
            Files.exists(mapperFile),
            "MD-04: ToolEnvelopeFailureMapper.kt must be deleted",
        )
        val sources = Files.walk(inferenceMainRoot)
            .filter { it.toString().endsWith(".kt") }
            .toList()
        sources.forEach { path ->
            val text = Files.readString(path)
            assertFalse(
                text.contains("toInferenceException"),
                "MD-04: toInferenceException must not appear in ${path.fileName}",
            )
        }
    }

    private class StreamGateProbeService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        fakeGate: FakeToolStreamGatePort,
    ) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = "openai/gpt-5.2",
        provider = "OPENROUTER",
        modelType = ModelType.FAST,
        baseUrl = null,
        loggingPort = loggingPort,
        toolStreamGatePort = fakeGate,
    ) {
        override val tag: String = "ToolParse002MutationDefenseTest"

        override suspend fun executePrompt(
            prompt: String,
            options: GenerationOptions,
            requestHistory: List<com.browntowndev.pocketcrew.domain.model.chat.ChatMessage>,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ) {
            emitEvent(InferenceEvent.PartialResponse("<|DSML|tool_calls>", ModelType.FAST))
        }
    }
}
