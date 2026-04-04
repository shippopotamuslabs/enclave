package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationResponse
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

    companion object {
        private const val TAG = "ConversationImpl"
    }

    override fun sendMessageAsync(message: String, options: GenerationOptions?): Flow<ConversationResponse> {
        val userMessage = Message.user(message)
        
        // Map reasoningBudget to LiteRT extraContext toggle
        // ARCHITECTURE FIX: Pass null to disable thinking instead of "false" string, 
        // to match official Google Edge Gallery behavior and avoid unintended activation.
        val extraContext = if ((options?.reasoningBudget ?: 0) > 0) {
            mapOf("enable_thinking" to "true")
        } else {
            null
        }

        Log.d(
            TAG,
            "sendMessageAsync reasoningBudget=${options?.reasoningBudget ?: 0}, enable_thinking_present=${extraContext != null}"
        )

        return conversation.sendMessageAsync(userMessage, extraContext ?: emptyMap()).map { partialResult ->
            val text = partialResult.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
            
            val thought = partialResult.channels["thought"] ?: ""
            
            ConversationResponse(text = text, thought = thought)
        }
    }
}
