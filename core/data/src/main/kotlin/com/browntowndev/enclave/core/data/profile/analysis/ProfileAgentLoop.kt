package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import kotlinx.coroutines.CancellationException
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
    private val loggingPort: LoggingPort,
    private val passName: String,
    private val requireRecordFinding: Boolean = true,
) {
    suspend fun run(messages: MutableList<AgentLoopMessage>) {
        var steps = 0
        var recordFindingCalled = false
        var nudges = 0
        loggingPort.debug(TAG, "[$passName] starting loop")
        while (steps < MAX_STEPS) {
            val turnResult = runCatching {
                inferencePort.completeTurn(
                    configId = configId,
                    messages = messages.toList(),
                    systemPrompt = systemPrompt,
                    tools = tools,
                )
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                loggingPort.error(TAG, "[$passName] completeTurn failed at step $steps — stopping loop. cause=${e.message}", e)
                STOP_RESULT
            }

            val assistantText = turnResult.assistantText ?: ""
            messages.add(
                AgentLoopMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = assistantText,
                ),
            )

            val toolCallNames = turnResult.toolCalls.map { it.name }
            loggingPort.debug(TAG, "[$passName] step=${steps + 1} finishReason=${turnResult.finishReason} tools=$toolCallNames")

            val wantsToStop = turnResult.toolCalls.isEmpty() || turnResult.finishReason is AgentFinishReason.Stop
            val shouldNudge = requireRecordFinding && wantsToStop && !recordFindingCalled && nudges < MAX_NUDGES

            steps++

            when {
                shouldNudge -> {
                    nudges++
                    loggingPort.debug(TAG, "[$passName] nudge $nudges/$MAX_NUDGES — $RECORD_FINDING_TOOL not yet called")
                    messages.add(
                        AgentLoopMessage(
                            id = UUID.randomUUID().toString(),
                            role = Role.USER,
                            content = NUDGE_MESSAGE,
                        ),
                    )
                }
                wantsToStop -> {
                    loggingPort.debug(TAG, "[$passName] loop finished at step=$steps")
                    break
                }
                else -> {
                    for (call in turnResult.toolCalls) {
                        if (call.name == RECORD_FINDING_TOOL) recordFindingCalled = true

                        val request = ToolCallRequest(
                            toolName = call.name,
                            argumentsJson = call.argumentsJson,
                            provider = "profile_analysis",
                            modelType = ModelType.MAIN,
                        )
                        val toolResult = runCatching { toolExecutor.execute(request) }.getOrElse {
                            loggingPort.error(TAG, "[$passName] tool execution failed: ${call.name}", it)
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
                }
            }
        }

        if (steps >= MAX_STEPS) {
            loggingPort.warning(TAG, "[$passName] reached MAX_STEPS=$MAX_STEPS without stop signal — pass may be incomplete")
        }
    }

    companion object {
        private const val TAG = "ProfileAgentLoop"
        const val MAX_STEPS = 50
        private const val MAX_NUDGES = 3
        private const val RECORD_FINDING_TOOL = "record_finding"
        private const val NUDGE_MESSAGE =
            "You have not called $RECORD_FINDING_TOOL yet. " +
                "This is mandatory before finishing this pass. " +
                "Call $RECORD_FINDING_TOOL with your findings now."
        private val STOP_RESULT = AgentModelTurnResult(
            assistantText = null,
            toolCalls = emptyList(),
            finishReason = AgentFinishReason.Stop,
        )
    }
}
