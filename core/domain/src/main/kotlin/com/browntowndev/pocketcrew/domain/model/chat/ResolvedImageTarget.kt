package com.browntowndev.pocketcrew.domain.model.chat

/**
 * Represents a resolved image target for the image inspection tool.
 *
 * Contains the minimum data needed by the tool executor to
 * re-read the relevant image through the vision model.
 *
 * @property userMessageId The ID of the user message that carries the image.
 * @property imageUri The URI of the image to inspect.
 */
data class ResolvedImageTarget(
    val userMessageId: MessageId,
    val imageUri: String,
)
