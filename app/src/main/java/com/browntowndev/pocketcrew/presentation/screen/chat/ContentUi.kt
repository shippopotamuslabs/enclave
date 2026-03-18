package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * Presentation layer content model.
 * Represents the text content with its associated pipeline step.
 *
 * @property text The text content
 * @property pipelineStep The pipeline step that generated this content (null for FINAL or user messages)
 */
data class ContentUi(
    val text: String,
    val pipelineStep: PipelineStep? = null
)
