package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MediaResultParser {
    private val json = Json { ignoreUnknownKeys = true }

    data class MediaResult(
        val uris: List<String>,
        val ids: List<String>,
        val types: List<String>,
        val prompts: List<String>,
    )

    /**
     * Parses the JSON result from a media generation tool.
     *
     * @param resultJson The raw JSON string returned by the tool.
     * @return A MediaResult containing URIs and asset IDs.
     */
    fun parse(resultJson: String): MediaResult =
        runCatching {
            val jsonObject = json.parseToJsonElement(resultJson).jsonObject
            val uris = jsonObject["asset_uris"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val ids = jsonObject["asset_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val types =
                jsonObject["asset_types"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: listOfNotNull(jsonObject["media_type"]?.jsonPrimitive?.content)
            val prompts =
                jsonObject["asset_prompts"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: listOfNotNull(jsonObject["prompt"]?.jsonPrimitive?.content)

            MediaResult(uris, ids, types, prompts)
        }.getOrElse {
            MediaResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
}
