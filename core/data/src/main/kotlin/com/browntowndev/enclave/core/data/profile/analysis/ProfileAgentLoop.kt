package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
private data class LoopErrorResult(val status: String = "error", val message: String)

class ProfileAgentLoop(
    private val inferencePort: ProfileAnalysisInferencePort,
    private val configId: ApiModelConfigurationId,
    private val systemPrompt: String,
    private val tools: List<ToolDefinition>,
    private val toolExecutor: ToolExecutorPort,
) {
    suspend fun run(messages: MutableList<AgentLoopMessage>) {
        var steps = 0
        while (steps < MAX_STEPS) {
            val turnResult = inferencePort.completeTurn(
                configId = configId,
                messages = messages.toList(),
                systemPrompt = systemPrompt,
                tools = tools,
            )

            val assistantText = turnResult.assistantText ?: ""
            messages.add(
                AgentLoopMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = assistantText,
                ),
            )

            if (turnResult.toolCalls.isEmpty() || turnResult.finishReason is AgentFinishReason.Stop) break

            for (call in turnResult.toolCalls) {
                val request = ToolCallRequest(
                    toolName = call.name,
                    argumentsJson = call.argumentsJson,
                    provider = "profile_analysis",
                    modelType = ModelType.MAIN,
                )
                val toolResult = runCatching { toolExecutor.execute(request) }.getOrElse {
                    ToolExecutionResult(
                        toolName = call.name,
                        resultJson = Json.encodeToString(LoopErrorResult(message = it.message ?: "Unknown error")),
                    )
                }

                messages.add(
                    AgentLoopMessage(
                        id = UUID.randomUUID().toString(),
                        role = Role.USER,
                        content = "[Tool result: ${call.name}]\n${toolResult.resultJson}",
                    ),
                )
            }

            steps++
        }
    }

    companion object {
        private const val MAX_STEPS = 30
    }
}
