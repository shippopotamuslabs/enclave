package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireMap
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireValue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonSchemaWireSupportTest {
    @Test
    fun `JsonNull maps to kotlin null not string null`() {
        val schema =
            buildJsonObject {
                putJsonObject("nullable_field") {
                    // JsonObject with only null value uses put with null
                }
                put("explicit_null", JsonNull)
            }

        val wire = schema.toWireMap()
        assertNull(wire["explicit_null"])
    }

    @Test
    fun `nested object preserves primitives`() {
        val schema =
            buildJsonObject {
                putJsonObject("count") {
                    put("type", "integer")
                }
                put("enabled", true)
            }

        val wire = schema.toWireMap()
        assertEquals(true, wire["enabled"])
        @Suppress("UNCHECKED_CAST")
        val count = wire["count"] as Map<String, Any?>
        assertEquals("integer", count["type"])
    }
}
