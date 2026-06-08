package com.browntowndev.enclave.domain.model.inference

object ToolCategoryTags {
    val byToolName: Map<String, List<String>> =
        mapOf(
            "search_tools" to listOf("discovery"),
            "web_search" to listOf("web"),
            "web_extract" to listOf("web"),
            "search_chat_history" to listOf("memory"),
            "search_chat" to listOf("memory"),
            "get_message_context" to listOf("memory"),
            "manage_memories" to listOf("memory"),
            "read_context_reference" to listOf("context"),
            "start_artifact" to listOf("artifact"),
            "append_blocks" to listOf("artifact"),
            "search_current_artifact" to listOf("artifact"),
            "search_x_recent" to listOf("x"),
            "x_get_thread" to listOf("x"),
            "x_trending" to listOf("x"),
            "x_user_tweets" to listOf("x"),
            "x_mention_search" to listOf("x"),
            "attached_image_inspect" to listOf("vision"),
            "generate_image" to listOf("media"),
            "generate_video" to listOf("media"),
            "generate_music" to listOf("media"),
        )

    fun forTool(name: String): List<String> = byToolName[name].orEmpty()
}
