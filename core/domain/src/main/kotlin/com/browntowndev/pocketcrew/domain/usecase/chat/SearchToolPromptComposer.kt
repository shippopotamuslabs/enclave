package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

class SearchToolPromptComposer @Inject constructor() {

    fun compose(baseSystemPrompt: String?): String =
        listOfNotNull(
            baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
            LOCAL_TOOL_CONTRACT,
        ).joinToString(separator = "\n\n")

    companion object {
        const val LOCAL_TOOL_CONTRACT: String = """
When you need current or external information, respond with exactly one tool envelope and no surrounding prose:
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>

After you receive a <tool_result>...</tool_result> message, continue the answer for the user using that result.
Do not expose tool JSON, tool envelopes, or tool results in the final answer.
"""
    }
}
