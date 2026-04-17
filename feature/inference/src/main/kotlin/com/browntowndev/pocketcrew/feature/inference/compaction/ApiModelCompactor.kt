package com.browntowndev.pocketcrew.feature.inference.compaction

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.CompactionProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.flow.toList

/**
 * Implementation of [CompactionProvider] using a standard API model.
 * Performs summarization or verbatim compaction depending on the prompt instructions.
 */
class ApiModelCompactor(
    private val modelId: String,
    private val inferenceFactory: InferenceFactoryPort,
    private val loggingPort: LoggingPort,
) : CompactionProvider {
    override val id: String = "api_model_$modelId"
    override val name: String = "API Model ($modelId)"

    override suspend fun compact(history: List<ChatMessage>): String {
        return inferenceFactory.withInferenceServiceByConfigId(ApiModelConfigurationId(modelId)) { service ->
            val prompt = """
                Compaction Request:
                Please provide a verbatim but extremely concise compaction of the following chat history. 
                Retain all key facts, entities, and the current state of the conversation. 
                Remove filler words and redundant pleasantries.
                
                History:
                ${history.joinToString("\n") { "${it.role.name}: ${it.content}" }}
                
                Compacted History:
            """.trimIndent()

            try {
                val responseFlow = service.sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, maxTokens = 4096))
                val result = responseFlow.toList().joinToString("") {
                    when (it) {
                        is com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent.PartialResponse -> it.chunk
                        else -> ""
                    }
                }
                result.trim()
            } catch (e: Exception) {
                loggingPort.error("ApiModelCompactor", "Compaction failed for model $modelId: ${e.message}")
                throw e
            }
        }
    }
}
