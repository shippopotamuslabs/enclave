package com.browntowndev.pocketcrew.domain.model.inference

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
) {
    companion object {
        val TAVILY_WEB_SEARCH = ToolDefinition(
            name = "tavily_web_search",
            description = "Search the web and return ranked results.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )
    }
}
