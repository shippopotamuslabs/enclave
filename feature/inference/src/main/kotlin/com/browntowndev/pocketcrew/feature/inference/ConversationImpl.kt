package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of [ConversationPort] that wraps LiteRT's [Conversation].
 * This adapter handles Message creation and Content parsing, providing a clean
 * domain-focused interface.
 *
 * @param conversation The LiteRT Conversation to wrap.
 */
class ConversationImpl @Inject constructor(
    private val conversation: Conversation,
) : ConversationPort {

    override fun sendMessageAsync(message: String): Flow<String> {
        val userMessage = Message.user(message)
        return conversation.sendMessageAsync(userMessage).map { partialResult ->
            partialResult.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
        }
    }
}
