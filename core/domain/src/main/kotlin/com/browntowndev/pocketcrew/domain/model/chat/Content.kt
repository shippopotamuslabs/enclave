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
    val imageUri: String? = null,
    val tavilySources: List<TavilySource> = emptyList(),
)
