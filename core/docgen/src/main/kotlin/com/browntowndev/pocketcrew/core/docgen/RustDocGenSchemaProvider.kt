package com.browntowndev.pocketcrew.core.docgen

import com.browntowndev.pocketcrew.domain.model.inference.DynamicToolSchemaProvider
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

class RustDocGenSchemaProvider : DynamicToolSchemaProvider {
    override fun getSchema(toolName: String): String? =
        when (toolName) {
            ToolDefinition.START_ARTIFACT.name -> getStartArtifactSchema()
            ToolDefinition.APPEND_BLOCKS.name -> getAppendBlocksSchema()
            else -> null
        }
}
