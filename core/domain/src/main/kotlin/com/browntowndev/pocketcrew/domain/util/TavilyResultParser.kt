package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object TavilyResultParser {
    /**
     * Parses the JSON result from the Tavily search tool into a list of [TavilySource] objects.
     *
     * @param messageId The ID of the assistant message these sources should be associated with.
     * @param resultJson The raw JSON string returned by the Tavily search tool.
     * @return A list of [TavilySource] objects.
     */
    fun parse(messageId: MessageId, resultJson: String): List<TavilySource> {
        val sources = mutableListOf<TavilySource>()
        runCatching {
            val payload = Json.parseToJsonElement(resultJson).jsonObject
            val results = payload["results"]?.jsonArray ?: return@runCatching

            for (itemElement in results) {
                val item = itemElement.jsonObject
                sources.add(
                    TavilySource(
                        messageId = messageId,
                        title = item["title"]?.jsonPrimitive?.content ?: "",
                        url = item["url"]?.jsonPrimitive?.content ?: "",
                        content = item["content"]?.jsonPrimitive?.content ?: "",
                        score = item["score"]?.jsonPrimitive?.double ?: 0.0
                    )
                )
            }
        }
        return sources
    }
}
