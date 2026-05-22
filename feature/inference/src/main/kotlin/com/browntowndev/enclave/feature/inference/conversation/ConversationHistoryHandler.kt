package com.browntowndev.enclave.feature.inference.conversation

import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig as LiteRtSamplerConfig
import com.google.ai.edge.litertlm.tool
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage
import com.google.ai.edge.litertlm.Message as LiteRtMessage

internal object ConversationHistoryHandler {
    private const val TAG = "ConversationManager"

    fun applyHistoryUpdate(
        cache: ConversationSignatureCache,
        messages: List<ChatMessage>,
        closeConversation: () -> Unit,
    ) {
        val isContinuation =
            messages == cache.history ||
                (
                    messages.size >= cache.history.size &&
                        messages.take(cache.history.size) == cache.history
                )
        val resetsNativeContextToEmpty = messages.isEmpty() && cache.history.isEmpty() && cache.conversation != null

        when {
            resetsNativeContextToEmpty -> {
                Log.d(
                    TAG,
                    "Empty rehydrated history requires native conversation reset. " +
                        "(oldSize=${cache.history.size}, newSize=${messages.size})",
                )
                closeConversation()
            }
            !isContinuation && cache.conversation != null -> {
                Log.d(
                    TAG,
                    "History discontinuity detected. Recreating conversation. (oldSize=${cache.history.size}, newSize=${messages.size})",
                )
                closeConversation()
            }
            isContinuation -> {
                Log.d(
                    TAG,
                    "History is a continuation. Reusing conversation. (oldSize=${cache.history.size}, newSize=${messages.size})",
                )
            }
        }

        cache.history = messages.toList()
    }

    fun toInitialMessages(history: List<DomainChatMessage>): List<LiteRtMessage> =
        history.map { domainMsg ->
            when (domainMsg.role) {
                Role.USER -> LiteRtMessage.user(domainMsg.content)
                Role.ASSISTANT -> LiteRtMessage.model(domainMsg.content)
                Role.SYSTEM -> LiteRtMessage.system(domainMsg.content)
            }
        }
}

internal fun buildConversationConfig(
    signature: ConversationSignature,
    systemPrompt: String,
    history: List<DomainChatMessage>,
    samplerConfig: com.browntowndev.enclave.domain.model.inference.SamplerConfig,
    activeBackendIsNpu: Boolean,
    toolContext: LiteRtToolContext,
): ConversationConfig =
    ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        initialMessages = ConversationHistoryHandler.toInitialMessages(history),
        samplerConfig =
            if (activeBackendIsNpu) {
                null
            } else {
                LiteRtSamplerConfig(
                    temperature = samplerConfig.temperature,
                    topP = samplerConfig.topP,
                    topK = samplerConfig.topK,
                )
            },
        automaticToolCalling = true,
        tools = LiteRtNativeToolsets.buildToolList(signature, toolContext),
    )

internal data class LiteRtToolContext(
    val modelType: com.browntowndev.enclave.domain.model.inference.ModelType,
    val chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
    val userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
    val toolLoop: LiteRtToolCallLoop,
)
