package com.browntowndev.pocketcrew.core.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiModelMapperTest {
    @Test
    fun `empty map serializes to empty JSON object`() {
        val result = ApiModelMapper.serializeCustomHeadersAndParams(emptyMap())
        assertEquals("{}", result)
    }

    @Test
    fun `malformed JSON string deserializes to empty map`() {
        val result = ApiModelMapper.deserializeCustomHeadersAndParams("not_valid_json")
        assertEquals(emptyMap<String, String>(), result)
    }
}