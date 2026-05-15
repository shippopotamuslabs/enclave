package com.browntowndev.pocketcrew.core.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiModelMapperTest {
    @Test
    fun `empty map serializes to empty JSON object`() {
        val result = ApiModelMapper.serializeCustomHeaders(emptyMap())
        assertEquals("{}", result)
    }

    @Test
    fun `malformed JSON string deserializes to empty map`() {
        val result = ApiModelMapper.deserializeCustomHeaders("not_valid_json")
        assertEquals(emptyMap<String, String>(), result)
    }
}
