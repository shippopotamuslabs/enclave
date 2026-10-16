package com.browntowndev.enclave.feature.chat

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.chat.FileAttachment
import com.browntowndev.enclave.domain.model.inference.PipelineStep

/**
 * Presentation layer content model.
 * Represents the text content with its associated pipeline step.
 *
 * @property text The text content
 * @property pipelineStep The pipeline step that generated this content (null for FINAL or user messages)
 */
@Immutable
data class ContentUi(
    val text: String,
    val pipelineStep: PipelineStep? = null,
    val imageUris: List<String> = emptyList(),
    val webSearchSources: List<WebSearchSourceUi> = emptyList(),
    val artifacts: List<ArtifactCardUiModel> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList(),
    val generatedMediaUris: List<String> = emptyList(),
    val generatedMediaAssetIds: List<String> = emptyList(),
    val generatedMediaTypes: List<String> = emptyList(),
    val generatedMediaPrompts: List<String> = emptyList(),
)
