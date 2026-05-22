package com.browntowndev.enclave.feature.inference.llama

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
/**
 * Builds native chat payloads and bridges JNI token callbacks into [GenerationEvent] flows.
 */
internal object LlamaGenerationSession {
    private const val TAG = "LlamaGenerationSession"

    fun buildNativeMessages(
        history: List<ChatMessage>,
        systemPromptOverride: String? = null,
        mediaMarkerCount: Int = 0,
    ): Pair<Array<String>, Array<String>> {
        val effectiveMessages = history.toMutableList()
        val effectiveSystemPrompt = systemPromptOverride?.takeIf(String::isNotBlank)

        if (effectiveSystemPrompt != null) {
            val systemIndex = effectiveMessages.indexOfFirst { it.role == ChatRole.SYSTEM }
            if (systemIndex >= 0) {
                effectiveMessages[systemIndex] = ChatMessage(ChatRole.SYSTEM, effectiveSystemPrompt)
            } else {
                effectiveMessages.add(0, ChatMessage(ChatRole.SYSTEM, effectiveSystemPrompt))
            }
        }

        if (mediaMarkerCount > 0) {
            val lastUserIndex = effectiveMessages.indexOfLast { it.role == ChatRole.USER }
            require(lastUserIndex >= 0) { "Cannot attach image input without a user message in history" }
            val mediaPrefix =
                buildString {
                    repeat(mediaMarkerCount) {
                        append("<__media__>")
                    }
                }
            val userMessage = effectiveMessages[lastUserIndex]
            if (!userMessage.content.contains("<__media__>")) {
                effectiveMessages[lastUserIndex] = userMessage.copy(content = mediaPrefix + userMessage.content)
            }
        }

        val nativeMessages = effectiveMessages.map { it.toNativeMessage() }
        return nativeMessages.map { it.first }.toTypedArray() to nativeMessages.map { it.second }.toTypedArray()
    }

    fun createTokenCallback(
        emit: (GenerationEvent) -> kotlinx.coroutines.channels.ChannelResult<Unit>,
        onGenerationComplete: (promptTokens: Int, generatedTokens: Int, fullText: String) -> Unit,
        onTerminal: () -> Unit,
    ): NativeTokenCallback =
        object : NativeTokenCallback {
            private val sb = StringBuilder()
            private var isClosed = false

            override fun onToken(token: String) {
                if (isClosed) return
                sb.append(token)
                emit(GenerationEvent.Token(token))
            }

            override fun onComplete(
                promptTokens: Int,
                generatedTokens: Int,
            ) {
                if (isClosed) return
                isClosed = true
                Log.i(TAG, "Generation complete: $generatedTokens tokens generated")
                val fullText = sb.toString()
                emit(
                    GenerationEvent.Completed(
                        fullText = fullText,
                        promptTokens = promptTokens,
                        generatedTokens = generatedTokens,
                    ),
                )
                onGenerationComplete(promptTokens, generatedTokens, fullText)
                onTerminal()
            }

            override fun onError(message: String) {
                if (isClosed) return
                isClosed = true
                emit(GenerationEvent.Error(IllegalStateException(message)))
                onTerminal()
            }
        }

    private fun ChatMessage.toNativeMessage(): Pair<String, String> {
        val roleStr =
            when (role) {
                ChatRole.SYSTEM -> "system"
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "assistant"
            }
        return roleStr to content
    }
}
