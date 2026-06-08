package com.browntowndev.enclave.domain.model.inference

/** Tool names with on-device LiteRT native providers. */
object LiteRtNativeToolNames {
    val CALLABLE: Set<String> =
        setOf(
            ToolDefinition.SEARCH_TOOLS.name,
            ToolDefinition.WEB_SEARCH.name,
            ToolDefinition.WEB_EXTRACT.name,
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
            ToolDefinition.SEARCH_CHAT_HISTORY.name,
            ToolDefinition.SEARCH_CHAT.name,
            ToolDefinition.GET_MESSAGE_CONTEXT.name,
            ToolDefinition.START_ARTIFACT.name,
            ToolDefinition.APPEND_BLOCKS.name,
            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
        )

    fun isCallable(toolName: String): Boolean = toolName in CALLABLE
}
