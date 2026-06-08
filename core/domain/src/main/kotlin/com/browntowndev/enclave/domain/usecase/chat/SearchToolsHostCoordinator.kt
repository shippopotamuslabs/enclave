package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.inference.SearchToolsCallArgs
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolSearchIndexSnapshot
import com.browntowndev.enclave.domain.model.inference.ToolSearchResult
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.NoopToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort

data class SearchToolsHostInput(
    val query: String,
    val limit: Int,
    val toolCatalog: List<ToolCatalogEntry>,
    val activationRegistry: ToolSessionActivationRegistry,
    val providedIndex: ToolSearchIndexSnapshot?,
    val strategy: ToolCallStrategy,
    val chatId: ChatId?,
)

/**
 * Shared `search_tools` host path for API (RustLoop) and LiteRT.
 * Parses args, resolves hybrid search, updates [ToolSessionActivationRegistry].
 */
class SearchToolsHostCoordinator(
    private val embeddingEngine: EmbeddingEnginePort,
    private val embeddingCache: ToolSearchEmbeddingCachePort = NoopToolSearchEmbeddingCachePort,
) {
    suspend fun executeFromArguments(
        argumentsJson: String,
        toolCatalog: List<ToolCatalogEntry>,
        activationRegistry: ToolSessionActivationRegistry,
        providedIndex: ToolSearchIndexSnapshot?,
        strategy: ToolCallStrategy,
        chatId: ChatId?,
    ): ToolSearchResult = execute(inputFrom(argumentsJson, toolCatalog, activationRegistry, providedIndex, strategy, chatId))

    suspend fun execute(input: SearchToolsHostInput): ToolSearchResult {
        val effectiveLimit = input.limit.coerceIn(1, ToolSearchThresholds.MAX_SEARCH_LIMIT)
        val searchIndex =
            input.providedIndex
                ?: ToolSearchCatalogBuilder.build(
                    catalog = input.toolCatalog,
                    embeddingEngine = embeddingEngine,
                    embeddingCache = embeddingCache,
                )
        val activeTools =
            input.activationRegistry
                .orderedActiveNames()
                .mapNotNull(ToolDefinition::fromName)
        val resolution =
            ToolSearchResolver.resolve(
                query = input.query,
                limit = effectiveLimit,
                toolCatalog = input.toolCatalog,
                activeTools = activeTools,
                searchIndex = searchIndex,
                embeddingEngine = embeddingEngine,
                strategy = input.strategy,
            )
        input.activationRegistry.applySearchActivations(
            activatedNames = resolution.activatedToolNames,
            chatId = input.chatId,
        )
        return resolution
    }

    companion object {
        fun inputFrom(
            argumentsJson: String,
            toolCatalog: List<ToolCatalogEntry>,
            activationRegistry: ToolSessionActivationRegistry,
            providedIndex: ToolSearchIndexSnapshot?,
            strategy: ToolCallStrategy,
            chatId: ChatId?,
        ): SearchToolsHostInput {
            val args = SearchToolsCallArgs.parse(argumentsJson)
            return SearchToolsHostInput(
                query = args.query,
                limit = args.limit,
                toolCatalog = toolCatalog,
                activationRegistry = activationRegistry,
                providedIndex = providedIndex,
                strategy = strategy,
                chatId = chatId,
            )
        }
    }
}
