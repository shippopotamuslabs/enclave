package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment

/**
 * Presentation layer content model.
 * Represents the text content with its associated pipeline step.
 *
 * @property text The text content
 * @property pipelineStep The pipeline step that generated this content (null for FINAL or user messages)
 */
data class ContentUi(
    val text: String,
    val pipelineStep: PipelineStep? = null,
    val imageUri: String? = null,
    val webSearchSources: List<WebSearchSource> = emptyList(),
    val artifacts: List<ArtifactGenerationRequest> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList(),
    val generatedMediaUris: List<String> = emptyList(),
    val generatedMediaAssetIds: List<String> = emptyList(),
    val generatedMediaTypes: List<String> = emptyList(),
    val generatedMediaPrompts: List<String> = emptyList(),
)
