package com.browntowndev.pocketcrew.domain.model.chat

import java.util.UUID

/**
 * Domain model representing a search source from a web search provider.
 *
 * @property id Unique identifier for the source record
 * @property messageId The ID of the message this source is associated with
 * @property title The title of the search result
 * @property url The URL of the search result
 * @property content A snippet or content from the search result
 * @property score The relevance score assigned by the provider
 */
data class WebSearchSource(
    val id: String = UUID.randomUUID().toString(),
    val messageId: MessageId,
    val title: String,
    val url: String,
    val content: String,
    val score: Double = 0.0,
    val extracted: Boolean = false,
)
