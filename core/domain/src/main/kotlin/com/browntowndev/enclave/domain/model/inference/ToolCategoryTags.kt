package com.browntowndev.enclave.domain.model.inference

object ToolCategoryTags {
    val byToolName: Map<String, List<ToolCategory>> =
        mapOf(
            "tool_discovery_tool" to listOf(ToolCategory.DISCOVERY),
            "web_search" to listOf(ToolCategory.WEB),
            "web_extract" to listOf(ToolCategory.WEB),
            "search_chat_history" to listOf(ToolCategory.MEMORY),
            "search_chat" to listOf(ToolCategory.MEMORY),
            "get_message_context" to listOf(ToolCategory.MEMORY),
            "manage_memories" to listOf(ToolCategory.MEMORY),
            "read_context_reference" to listOf(ToolCategory.CONTEXT),
            "start_artifact" to listOf(ToolCategory.ARTIFACT),
            "append_blocks" to listOf(ToolCategory.ARTIFACT),
            "search_current_artifact" to listOf(ToolCategory.ARTIFACT),
            "search_x_recent" to listOf(ToolCategory.X),
            "x_get_thread" to listOf(ToolCategory.X),
            "x_trending" to listOf(ToolCategory.X),
            "x_user_tweets" to listOf(ToolCategory.X),
            "x_mention_search" to listOf(ToolCategory.X),
            "attached_image_inspect" to listOf(ToolCategory.VISION),
            "generate_image" to listOf(ToolCategory.MEDIA),
            "generate_video" to listOf(ToolCategory.MEDIA),
            "generate_music" to listOf(ToolCategory.MEDIA),
        )

    fun forTool(name: String): List<String> = byToolName[name].orEmpty().map { it.tag }
}
