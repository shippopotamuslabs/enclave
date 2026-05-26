package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WebSearchResultParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the JSON result from a web search tool into a list of [WebSearchSource] objects.
     *
     * @param messageId The ID of the assistant message these sources should be associated with.
     * @param resultJson The raw JSON string returned by the web search tool.
     * @return A list of [WebSearchSource] objects.
     */
    fun parse(
        messageId: MessageId,
        resultJson: String,
    ): List<WebSearchSource> {
        val sources = mutableListOf<WebSearchSource>()
        runCatching {
            val jsonElement = json.parseToJsonElement(resultJson)

            // Handle X search result (JsonArray of posts)
            if (jsonElement is JsonArray) {
                jsonElement.forEach { post ->
                    val postObj = post.jsonObject
                    val content =
                        postObj["text"]?.jsonPrimitive?.content
                            ?: postObj["content"]?.jsonPrimitive?.content
                    val url = postObj["url"]?.jsonPrimitive?.content
                    val title =
                        postObj["user_name"]?.jsonPrimitive?.content
                            ?: postObj["username"]?.jsonPrimitive?.content
                            ?: "X Post"

                    if (!content.isNullOrBlank() && !url.isNullOrBlank()) {
                        sources.add(
                            WebSearchSource(
                                messageId = messageId,
                                title = title,
                                url = url,
                                content = content,
                                score = postObj["impact_score"]?.jsonPrimitive?.double ?: 1.0,
                            ),
                        )
                    }
                }
            } else if (jsonElement is JsonObject) {
                // Handle standard web search format (Tavily/Exa/Google) or X thread response
                // Some formats might have results in "results" or "data"
                val results = jsonElement["results"]?.jsonArray ?: jsonElement["data"]?.jsonArray

                if (results != null) {
                    results.forEach { itemElement ->
                        val item = itemElement.jsonObject
                        val content =
                            item["text"]?.jsonPrimitive?.content
                                ?: item["content"]?.jsonPrimitive?.content
                        val url = item["url"]?.jsonPrimitive?.content
                        val title =
                            item["user_name"]?.jsonPrimitive?.content
                                ?: item["username"]?.jsonPrimitive?.content
                                ?: item["title"]?.jsonPrimitive?.content
                                ?: "Source"
                        val score =
                            item["impact_score"]?.jsonPrimitive?.double
                                ?: item["score"]?.jsonPrimitive?.double
                                ?: 0.0

                        if (!url.isNullOrBlank() && !content.isNullOrBlank()) {
                            sources.add(
                                WebSearchSource(
                                    messageId = messageId,
                                    title = title,
                                    url = url,
                                    content = content,
                                    score = score,
                                ),
                            )
                        }
                    }
                }
            }
        }.onFailure { e ->
            // Log or ignore parsing errors for robust tool results
        }
        return sources
    }
}
