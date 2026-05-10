package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * Represents content with its associated pipeline step.
 * Used to track which step in Crew mode generated this content.
 *
 * @property text The text content
 * @property pipelineStep The pipeline step that generated this content (null for user messages or FINAL)
 */
data class Content(
    val text: String,
    val pipelineStep: PipelineStep? = null,
    val imageUris: List<String> = emptyList(),
    val webSearchSources: List<WebSearchSource> = emptyList(),
    val artifacts: List<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList(),
    val generatedMediaUris: List<String> = emptyList(),
    val generatedMediaAssetIds: List<String> = emptyList(),
    val generatedMediaTypes: List<String> = emptyList(),
    val generatedMediaPrompts: List<String> = emptyList(),
)
