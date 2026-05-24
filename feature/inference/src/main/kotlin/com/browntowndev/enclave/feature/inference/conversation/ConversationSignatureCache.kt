package com.browntowndev.enclave.feature.inference.conversation

import android.os.Debug
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SamplerConfig
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy
import com.browntowndev.enclave.feature.inference.ConversationImpl
import com.browntowndev.enclave.feature.inference.LiteRtConversation
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage

internal class ConversationSignatureCache {
    var currentModelPath: String? = null
    var currentModelType: ModelType? = null
    var currentConfigId: LocalModelConfigurationId? = null
    var currentThinkingEnabled: Boolean? = null
    var currentSystemPrompt: String = SystemPromptTemplates.DEFAULT
    var currentContextWindow: Int = DEFAULT_CONTEXT_WINDOW
    var currentSamplerConfig: SamplerConfig? = null
    var currentSignature: ConversationSignature? = null

    @Volatile
    var conversation: Conversation? = null

    @Volatile
    var conversationPort: LiteRtConversation? = null

    @Volatile
    var history: List<DomainChatMessage> = emptyList()

    @Volatile
    var engine: Engine? = null

    @Volatile
    var activeBackendIsNpu: Boolean = false

    @Volatile
    var hasTransientToolResults: Boolean = false

    @Volatile
    var transientToolResultTokens: Int = 0

    @Volatile
    var latestAssistantMessageId: MessageId? = null

    @Volatile
    var activeToolNames: List<String> = emptyList()

    @Volatile
    var toolCatalog: List<ToolCatalogEntry> = emptyList()

    @Volatile
    var toolCallStrategy: ToolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE

    fun clearTransientToolResults() {
        hasTransientToolResults = false
        transientToolResultTokens = 0
    }

    fun resetAfterEngineClose() {
        currentModelPath = null
        currentModelType = null
        currentConfigId = null
        currentThinkingEnabled = null
        currentSystemPrompt = SystemPromptTemplates.DEFAULT
        currentSamplerConfig = null
        currentSignature = null
        activeBackendIsNpu = false
        activeToolNames = emptyList()
        toolCatalog = emptyList()
        toolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE
    }

    fun wrapConversation(
        context: android.content.Context,
        liteRtConversation: Conversation,
    ): LiteRtConversation {
        if (conversationPort == null) {
            conversationPort = ConversationImpl(context, liteRtConversation)
        }
        return conversationPort!!
    }

    companion object {
        const val DEFAULT_CONTEXT_WINDOW = 2048
        const val LOG_TRUNCATE_LENGTH = 120
        const val TOOL_RESULT_TRUNCATE_LENGTH = 2000
        const val MAX_SOFT_FAILURE_STREAK = 3u
        const val CONTEXT_FULL_RESULT_BUDGET_BYTES = 256UL
    }
}

internal object MemoryDiagnostics {
    private const val BYTES_PER_MEGABYTE = 1024 * 1024

    fun snapshot(): String {
        val runtime = Runtime.getRuntime()
        val usedJvmMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE
        val totalJvmMb = runtime.totalMemory() / BYTES_PER_MEGABYTE
        val maxJvmMb = runtime.maxMemory() / BYTES_PER_MEGABYTE
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MEGABYTE
        return "jvmUsedMb=$usedJvmMb jvmTotalMb=$totalJvmMb jvmMaxMb=$maxJvmMb nativeHeapMb=$nativeHeapMb"
    }
}
