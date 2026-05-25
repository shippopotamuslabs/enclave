package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.google.genai.types.Content

/**
 * Estimates token counts for Google [CachedContent] prefixes using the same payload shape
 * as [GoogleGenAiSdkClient.createCachedContent] (system instruction text, mapped contents, tool schemas).
 */
internal object GooglePromptCacheTokenEstimator {
    fun countStablePrefixTokens(
        modelId: String,
        systemPrompt: String,
        contents: List<Content>,
        tools: List<ToolDefinition>,
    ): Int {
        val text =
            buildString {
                if (systemPrompt.isNotBlank()) {
                    append(systemPrompt.trim())
                    append('\n')
                }
                append(contents.joinGeminiContentsForTokenEstimate())
                if (tools.isNotEmpty()) {
                    append('\n')
                    append(tools.joinToolDefinitionsForTokenEstimate())
                }
            }
        return JTokkitTokenCounter.countTokens(text, modelId)
    }

    fun countContentsTokens(
        modelId: String,
        contents: List<Content>,
    ): Int = JTokkitTokenCounter.countTokens(contents.joinGeminiContentsForTokenEstimate(), modelId)

    private fun List<Content>.joinGeminiContentsForTokenEstimate(): String =
        buildString {
            this@joinGeminiContentsForTokenEstimate.forEach { geminiContent ->
                geminiContent.parts().orElse(emptyList()).forEach { part ->
                    part.text().ifPresent {
                        append(it)
                        append('\n')
                    }
                    part.functionCall().ifPresent { call ->
                        append("function_call:")
                        call.name().ifPresent { append(it) }
                        append(' ')
                        call.args().ifPresent { append(it.toString()) }
                        append('\n')
                    }
                    part.functionResponse().ifPresent { response ->
                        append("function_response:")
                        response.name().ifPresent { append(it) }
                        append(' ')
                        response.response().ifPresent { append(it.toString()) }
                        append('\n')
                    }
                }
            }
        }

    private fun List<ToolDefinition>.joinToolDefinitionsForTokenEstimate(): String =
        joinToString(separator = "\n") { tool ->
            "${tool.name}\n${tool.description}\n${tool.parametersJson}"
        }
}
