package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.genai.types.Content
import com.google.genai.types.Part

/**
 * Server-side CachedContent lifecycle for Google GenAI agent turns.
 * One instance per [GoogleInferenceServiceImpl] (one chat session).
 */
internal class GooglePromptCacheSession(
    private val modelId: String,
    private val sdkClient: GoogleGenAiSdkClient,
    private val loggingPort: LoggingPort,
    private val tag: String,
) {
    companion object {
        private const val MIN_CACHE_TTL_SECONDS = 300L
        private const val FLASH_CACHE_THRESHOLD_TOKENS = 1024
        private const val PRO_CACHE_THRESHOLD_TOKENS = 4096
    }

    private var activeCacheName: String? = null
    private var cachedSignature: CacheSignature? = null

    fun clearLocalState() {
        activeCacheName = null
        cachedSignature = null
    }

    suspend fun close() {
        val cacheToDelete = activeCacheName
        if (cacheToDelete != null) {
            try {
                sdkClient.deleteCachedContent(cacheToDelete)
            } catch (e: Exception) {
                loggingPort.warning(tag, "Failed to delete cached content on closeSession: ${e.message}")
            }
        }
        clearLocalState()
    }

    /**
     * Ensures [activeCacheName] reflects the stable prefix; returns the cache resource name or null.
     */
    suspend fun ensureCacheForStablePrefix(
        systemPrompt: String,
        stableHistory: List<AgentLoopMessage>,
        availableTools: List<ToolDefinition>,
        mapToContent: (AgentLoopMessage) -> Content?,
    ): String? {
        val cachedContents = stableHistory.mapNotNull(mapToContent)
        val stablePrefixTokens =
            GooglePromptCacheTokenEstimator.countStablePrefixTokens(
                modelId = modelId,
                systemPrompt = systemPrompt,
                contents = cachedContents,
                tools = availableTools,
            )

        val cacheThresholdTokens = cacheThresholdTokens()
        if (stablePrefixTokens <= cacheThresholdTokens) {
            activeCacheName?.let { deleteCacheQuietly(it) }
            clearLocalState()
            return null
        }

        var reuseCache = false
        val stableSignature =
            CacheSignature.from(
                systemPrompt = systemPrompt,
                stableHistory = stableHistory,
                availableTools = availableTools,
            )
        if (activeCacheName != null) {
            val cached = cachedSignature
            if (cached != null && stableSignature.startsWith(cached)) {
                val delta = stableHistory.subList(cached.messages.size, stableHistory.size)
                val deltaContents = delta.mapNotNull(mapToContent)
                val deltaTokens =
                    GooglePromptCacheTokenEstimator.countContentsTokens(
                        modelId = modelId,
                        contents = deltaContents,
                    )
                if (deltaTokens < cacheThresholdTokens) {
                    reuseCache = true
                }
            }
        }

        if (!reuseCache) {
            activeCacheName?.let { deleteCacheQuietly(it) }
            clearLocalState()
            try {
                val systemInstruction =
                    if (systemPrompt.isNotBlank()) {
                        Content.fromParts(Part.fromText(systemPrompt))
                    } else {
                        null
                    }
                val tools =
                    if (availableTools.isNotEmpty()) {
                        availableTools.map { it.toGoogleTool() }
                    } else {
                        null
                    }
                val cacheName =
                    sdkClient.createCachedContent(
                        modelId = modelId,
                        contents = cachedContents,
                        systemInstruction = systemInstruction,
                        tools = tools,
                        ttlSeconds = MIN_CACHE_TTL_SECONDS,
                    )
                activeCacheName = cacheName
                cachedSignature = stableSignature
            } catch (e: Exception) {
                loggingPort.warning(
                    tag,
                    "Failed to create cached content on server: ${e.message}. " +
                        "Falling back to standard non-cached generation.",
                )
                clearLocalState()
            }
        }

        return activeCacheName
    }

    fun deltaHistory(stableHistory: List<AgentLoopMessage>): List<AgentLoopMessage> =
        if (activeCacheName != null) {
            stableHistory.subList(cachedSignature?.messages?.size ?: 0, stableHistory.size)
        } else {
            stableHistory
        }

    private fun cacheThresholdTokens(): Int {
        val normalizedModelId = modelId.lowercase()
        return if (normalizedModelId.contains("pro")) {
            PRO_CACHE_THRESHOLD_TOKENS
        } else {
            FLASH_CACHE_THRESHOLD_TOKENS
        }
    }

    private suspend fun deleteCacheQuietly(name: String) {
        try {
            sdkClient.deleteCachedContent(name)
        } catch (e: Exception) {
            loggingPort.warning(tag, "Failed to delete cached content: ${e.message}")
        }
    }

    private data class CacheSignature(
        val systemPrompt: String,
        val messages: List<MessageSignature>,
        val toolNames: List<String>,
    ) {
        fun startsWith(cached: CacheSignature): Boolean =
            systemPrompt == cached.systemPrompt &&
                toolNames == cached.toolNames &&
                cached.messages.size <= messages.size &&
                messages.take(cached.messages.size) == cached.messages

        companion object {
            fun from(
                systemPrompt: String,
                stableHistory: List<AgentLoopMessage>,
                availableTools: List<ToolDefinition>,
            ): CacheSignature =
                CacheSignature(
                    systemPrompt = systemPrompt,
                    messages = stableHistory.map(MessageSignature::from),
                    toolNames = availableTools.map { it.name },
                )
        }
    }

    private data class MessageSignature(
        val id: String,
        val role: com.browntowndev.enclave.domain.model.chat.Role,
        val content: String,
        val metadataJson: String,
    ) {
        companion object {
            fun from(message: AgentLoopMessage): MessageSignature =
                MessageSignature(
                    id = message.id,
                    role = message.role,
                    content = message.content,
                    metadataJson = message.metadataJson,
                )
        }
    }
}
