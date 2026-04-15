package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import org.json.JSONObject
import org.json.JSONArray

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
            val payload = JSONObject(resultJson)
            val results = payload.optJSONArray("results") ?: JSONArray()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                sources.add(
                    TavilySource(
                        messageId = messageId,
                        title = item.optString("title", ""),
                        url = item.optString("url", ""),
                        content = item.optString("content", ""),
                        score = item.optDouble("score", 0.0)
                    )
                )
            }
        }
        return sources
    }
}
