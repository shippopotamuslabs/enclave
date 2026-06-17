package com.browntowndev.enclave.feature.inference

import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Tool
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toAnthropicJsonValue
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireMap
import kotlinx.serialization.json.JsonObject

internal fun ToolDefinition.toAnthropicTool(cacheControl: CacheControlEphemeral? = null): Tool {
    val builder =
        Tool
            .builder()
            .name(name)
            .description(description)
            .inputSchema(
                Tool.InputSchema
                    .builder()
                    .type(com.anthropic.core.JsonValue.from("object"))
                    .properties(anthropicToolProperties())
                    .required(schema.required)
                    .build(),
            ).strict(true)
    if (cacheControl != null) {
        builder.cacheControl(cacheControl)
    }
    return builder.build()
}

private fun ToolDefinition.anthropicToolProperties(): Tool.InputSchema.Properties {
    val builder = Tool.InputSchema.Properties.builder()
    schema.properties.forEach { (key, value) ->
        builder.putAdditionalProperty(key, value.toAnthropicJsonValue())
    }
    schema.fullSchemaOverride?.get("definitions")?.let { defs ->
        if (defs is JsonObject) {
            builder.putAdditionalProperty("definitions", com.anthropic.core.JsonValue.from(defs.toWireMap()))
        }
    }
    schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
        if (defs is JsonObject) {
            builder.putAdditionalProperty("\$defs", com.anthropic.core.JsonValue.from(defs.toWireMap()))
        }
    }
    return builder.build()
}
