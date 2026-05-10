package com.browntowndev.pocketcrew.core.docgen

import com.browntowndev.pocketcrew.domain.model.inference.DynamicToolSchemaProvider
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

class RustDocGenSchemaProvider : DynamicToolSchemaProvider {
    override fun getSchema(toolName: String): String? {
        // Rust owns the schema for both START and APPEND (though they are different params, 
        // the core Document schema is what we usually need to validate blocks).
        // For now, we only need to override the blocks schema in APPEND_BLOCKS
        // or just return the base Document schema if requested.
        return if (toolName == ToolDefinition.APPEND_BLOCKS.name || 
                   toolName == ToolDefinition.START_ARTIFACT.name) {
            try {
                com.browntowndev.pocketcrew.core.docgen.getSchema()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
