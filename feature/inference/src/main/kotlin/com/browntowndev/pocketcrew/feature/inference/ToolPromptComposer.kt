package com.browntowndev.pocketcrew.feature.inference

/**
 * Composes the local-model tool prompt contract.
 * Supports both web search and image inspection tools.
 */
class ToolPromptComposer {

    fun compose(
        baseSystemPrompt: String?,
        includeImageInspectTool: Boolean = false,
    ): String =
        listOfNotNull(
            baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
            localToolContract(includeImageInspectTool),
        ).joinToString(separator = "\n\n")

    companion object {
        const val SEARCH_TOOL_CONTRACT: String = """
When you need current or external information, respond with exactly one tool envelope and no surrounding prose:
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>

After you receive a <tool_result>...</tool_result> message, continue the answer for the user using that result.
Do not expose tool JSON, tool envelopes, or tool results in the final answer.
"""

        const val IMAGE_INSPECT_CONTRACT: String = """
When you need to inspect a previously attached image, respond with exactly one tool envelope and no surrounding prose:
<tool_call>{"name":"attached_image_inspect","arguments":{"question":"..."}}</tool_call>
"""

        fun localToolContract(includeImageInspectTool: Boolean): String =
            buildString {
                append(SEARCH_TOOL_CONTRACT.trim())
                if (includeImageInspectTool) {
                    append("\n\n")
                    append(IMAGE_INSPECT_CONTRACT.trim())
                }
            }
    }
}
