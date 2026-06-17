package com.browntowndev.enclave.domain.model.inference

object LiveVoiceToolset {
    val tools: List<ToolDefinition> =
        listOf(
            ToolDefinition.WEB_SEARCH,
            ToolDefinition.WEB_EXTRACT,
            ToolDefinition.SEARCH_CHAT_HISTORY,
            ToolDefinition.SEARCH_CHAT,
            ToolDefinition.GET_MESSAGE_CONTEXT,
            ToolDefinition.MANAGE_MEMORIES,
            ToolDefinition.X_SEARCH,
            ToolDefinition.X_GET_THREAD,
            ToolDefinition.X_TRENDING,
            ToolDefinition.X_USER_TWEETS,
            ToolDefinition.X_MENTION_SEARCH,
        )
}
