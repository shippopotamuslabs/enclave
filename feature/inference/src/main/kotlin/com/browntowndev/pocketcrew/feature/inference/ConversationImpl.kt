package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.net.Uri
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of [LiteRtConversation] that wraps LiteRT's [Conversation].
 * This adapter handles Message creation and Content parsing, providing a clean
 * domain-focused interface.
 *
 * @param conversation The LiteRT Conversation to wrap.
 */
class ConversationImpl @Inject constructor(
    private val context: Context,
    private val conversation: Conversation,
) : LiteRtConversation {

    companion object {
        private const val TAG = "ConversationImpl"
    }

    override fun cancelProcess() {
        Log.d(TAG, "Cancelling LiteRT conversation process")
        conversation.cancelProcess()
    }

    override fun sendMessageAsync(message: String, options: GenerationOptions?): Flow<ConversationResponse> = flow {
        val userMessage = buildUserMessage(message, options)
        
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

        conversation.sendMessageAsync(userMessage, extraContext ?: emptyMap()).collect { partialResult ->
            val text = partialResult.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
            
            val thought = partialResult.channels["thought"] ?: ""
            
            emit(ConversationResponse(text = text, thought = thought))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun buildUserMessage(message: String, options: GenerationOptions?): Message {
        val imageUris = options?.imageUris.orEmpty()
        if (imageUris.isEmpty()) {
            return Message.user(message)
        }

        val contents = mutableListOf<Content>()
        
        // Add images first
        imageUris.forEach { imageUri ->
            contents += toImageContent(imageUri)
        }
        
        // Add text LAST for the accurate last token
        if (message.trim().isNotEmpty()) {
            contents += Content.Text(message)
        }
        
        return Message.user(Contents.of(contents))
    }

    private suspend fun toImageContent(imageUri: String): Content = withContext(Dispatchers.IO) {
        val pngBytes = ImageDownscaler.downscaleToPngBytes(context, imageUri)
        Content.ImageBytes(pngBytes)
    }
}
