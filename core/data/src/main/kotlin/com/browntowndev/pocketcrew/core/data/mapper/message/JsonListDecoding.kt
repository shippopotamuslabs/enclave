package com.browntowndev.pocketcrew.core.data.mapper.message

import kotlinx.serialization.json.Json

internal inline fun <reified T> decodeJsonListOrEmpty(json: String?): List<T> =
    try {
        json?.let { Json.decodeFromString<List<T>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
