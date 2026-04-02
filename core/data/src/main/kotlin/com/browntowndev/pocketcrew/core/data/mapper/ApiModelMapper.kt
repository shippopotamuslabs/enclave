package com.browntowndev.pocketcrew.core.data.mapper

import org.json.JSONObject
import org.json.JSONException

object ApiModelMapper {
    fun serializeCustomHeadersAndParams(map: Map<String, String>): String {
        val jsonObject = JSONObject()
        for ((key, value) in map) {
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    fun deserializeCustomHeadersAndParams(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            val jsonObject = JSONObject(json)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getString(key)
            }
        } catch (e: JSONException) {
            // Return empty map if parsing fails
            return emptyMap()
        }
        return map
    }
}