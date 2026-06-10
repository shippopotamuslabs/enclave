package com.browntowndev.enclave.feature.inference.conversation

import android.os.Debug
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SamplerConfig
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolSearchIndexSnapshot
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy
import com.browntowndev.enclave.domain.usecase.chat.ToolSessionActivationRegistry
import com.browntowndev.enclave.feature.inference.ConversationImpl
import com.browntowndev.enclave.feature.inference.LiteRtConversation
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage
import java.util.concurrent.atomic.AtomicInteger

internal class ConversationSignatureCache {
    @Volatile
    var currentModelPath: String? = null

    @Volatile
    var currentModelType: ModelType? = null

    @Volatile
    var currentConfigId: LocalModelConfigurationId? = null

    @Volatile
    var currentThinkingEnabled: Boolean? = null

    @Volatile
    var currentSystemPrompt: String = SystemPromptTemplates.DEFAULT

    @Volatile
    var currentContextWindow: Int = DEFAULT_CONTEXT_WINDOW

    @Volatile
    var currentSamplerConfig: SamplerConfig? = null

    @Volatile
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

    val transientToolResultTokens = AtomicInteger(0)

    @Volatile
    var latestAssistantMessageId: MessageId? = null

    @Volatile
    var activeToolNames: List<String> = emptyList()

    lateinit var toolActivationRegistry: ToolSessionActivationRegistry

    @Volatile
    var toolCatalog: List<ToolCatalogEntry> = emptyList()

    @Volatile
    var toolSearchIndex: ToolSearchIndexSnapshot? = null

    @Volatile
    var toolCallStrategy: ToolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE

    fun clearTransientToolResults() {
        hasTransientToolResults = false
        transientToolResultTokens.set(0)
    }

    fun markConversationDirtyForDanglingToolCall() {
        hasTransientToolResults = true
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
        if (::toolActivationRegistry.isInitialized) {
            toolActivationRegistry.seedTurn(emptyList())
        }
        toolCatalog = emptyList()
        toolSearchIndex = null
        toolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE
    }

    fun wrapConversation(
        context: android.content.Context,
        liteRtConversation: Conversation,
    ): LiteRtConversation {
        val existing = conversationPort
        if (existing != null && conversation === liteRtConversation) {
            return existing
        }
        conversationPort = ConversationImpl(context, liteRtConversation)
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
