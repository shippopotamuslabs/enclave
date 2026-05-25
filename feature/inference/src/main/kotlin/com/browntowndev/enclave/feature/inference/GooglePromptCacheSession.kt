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
        private const val CACHE_THRESHOLD_TOKENS = 1024
    }

    private var activeCacheName: String? = null
    private val cachedMessages = mutableListOf<AgentLoopMessage>()

    fun clearLocalState() {
        activeCacheName = null
        cachedMessages.clear()
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

        if (stablePrefixTokens <= CACHE_THRESHOLD_TOKENS) {
            activeCacheName?.let { deleteCacheQuietly(it) }
            clearLocalState()
            return null
        }

        var reuseCache = false
        if (activeCacheName != null) {
            val matchesPrefix =
                cachedMessages.size <= stableHistory.size &&
                    cachedMessages.indices.all { i ->
                        cachedMessages[i].id == stableHistory[i].id &&
                            cachedMessages[i].role == stableHistory[i].role &&
                            cachedMessages[i].content == stableHistory[i].content
                    }
            if (matchesPrefix) {
                val delta = stableHistory.subList(cachedMessages.size, stableHistory.size)
                val deltaContents = delta.mapNotNull(mapToContent)
                val deltaTokens =
                    GooglePromptCacheTokenEstimator.countContentsTokens(
                        modelId = modelId,
                        contents = deltaContents,
                    )
                if (deltaTokens < CACHE_THRESHOLD_TOKENS) {
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
                cachedMessages.clear()
                cachedMessages.addAll(stableHistory)
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
            stableHistory.subList(cachedMessages.size, stableHistory.size)
        } else {
            stableHistory
        }

    private suspend fun deleteCacheQuietly(name: String) {
        try {
            sdkClient.deleteCachedContent(name)
        } catch (e: Exception) {
            loggingPort.warning(tag, "Failed to delete cached content: ${e.message}")
        }
    }
}
