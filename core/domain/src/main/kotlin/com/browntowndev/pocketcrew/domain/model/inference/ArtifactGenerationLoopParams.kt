package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StartArtifactParams(
    @property:ToolParam(description = "The document title as a top-level string (not nested under metadata).")
    val title: String,

    @property:ToolParam(description = "The author of the document.", required = false)
    val author: String? = null,
) : ToolParameters

@Serializable
data class AppendBlocksParams(
    @property:ToolParam(description = "The ID of the artifact to append blocks to.")
    val artifactId: String,
    
    @property:ToolParam(description = "A list of document blocks to append. Each block must follow the Document JSON Schema.")
    val blocks: List<JsonElement>,
    
    @property:ToolParam(description = "Set to true if this is the final set of blocks for the document.")
    val isComplete: Boolean = false
) : ToolParameters

@Serializable
data class SearchArtifactParams(
    @property:ToolParam(description = "The ID of the artifact to search within.")
    val artifactId: String,
    
    @property:ToolParam(description = "The semantic search query.")
    val query: String
) : ToolParameters
