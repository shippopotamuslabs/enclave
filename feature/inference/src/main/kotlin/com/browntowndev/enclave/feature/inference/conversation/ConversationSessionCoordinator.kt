package com.browntowndev.enclave.feature.inference.conversation

import android.util.Log
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SamplerConfig
import com.browntowndev.enclave.feature.inference.LiteRtConversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

internal class ConversationSessionCoordinator(
    private val cache: ConversationSignatureCache,
    private val factory: LiteRtConversationFactory,
    private val toolLoop: LiteRtToolCallLoop,
    private val cancellationGate: ConversationCancellationGate,
) {
    suspend fun getConversation(
        modelType: ModelType,
        options: GenerationOptions?,
        activeConfig: ActiveModelConfiguration,
        configId: LocalModelConfigurationId,
        modelPath: String,
        isMultimodal: Boolean,
        onLoadingStarted: suspend () -> Unit,
    ): LiteRtConversation {
        val requestId = cancellationGate.currentRequestId()
        val requestCancellationEpoch = cancellationGate.cancellationEpoch.get()
        ensureRequestActive(requestId, requestCancellationEpoch, "Request cancelled before initialization")

        val resolvedThinkingEnabled = activeConfig.thinkingEnabled
        val resolvedSystemPrompt = options?.systemPrompt ?: activeConfig.systemPrompt ?: SystemPromptTemplates.DEFAULT
        val targetSamplerConfig = resolveSamplerConfig(options, activeConfig)
        val desiredSignature =
            buildConversationSignature(
                modelType = modelType,
                configId = configId,
                thinkingEnabled = resolvedThinkingEnabled,
                systemPrompt = resolvedSystemPrompt,
                samplerConfig = targetSamplerConfig,
                options = options,
                cachedActiveToolNames = cache.activeToolNames,
            )

        val engineChanged = cache.currentModelPath != modelPath || cache.engine == null
        val conversationChanged =
            cache.currentSignature != desiredSignature ||
                cache.conversation == null ||
                cache.conversation?.isAlive != true ||
                cache.hasTransientToolResults
        val conversationRecreated = engineChanged || conversationChanged

        handleResourceRecreation(
            engineChanged = engineChanged,
            conversationChanged = conversationChanged,
            modelType = modelType,
            configId = configId,
            modelPath = modelPath,
            resolvedThinkingEnabled = resolvedThinkingEnabled,
            onLoadingStarted = onLoadingStarted,
            requestId = requestId,
            requestCancellationEpoch = requestCancellationEpoch,
            activeConfig = activeConfig,
            isMultimodal = isMultimodal,
        )

        val eng = cache.engine ?: throw IllegalStateException("Engine not initialized")
        val resolvedContextWindow = activeConfig.contextWindow ?: ConversationSignatureCache.DEFAULT_CONTEXT_WINDOW
        ensureConversationExists(
            modelType = modelType,
            desiredSignature = desiredSignature,
            resolvedSystemPrompt = resolvedSystemPrompt,
            targetSamplerConfig = targetSamplerConfig,
            options = options,
            modelPath = modelPath,
            contextWindow = resolvedContextWindow,
            requestId = requestId,
            requestCancellationEpoch = requestCancellationEpoch,
            engine = eng,
        )

        ensureRequestActive(requestId, requestCancellationEpoch, "Cancelled immediately after conversation setup")

        val liteRtConversation = cache.conversation ?: throw IllegalStateException("Conversation not initialized")
        val port = cache.wrapConversation(factory.context, liteRtConversation)

        updateCacheAfterSuccess(
            desiredSignature = desiredSignature,
            modelType = modelType,
            configId = configId,
            resolvedThinkingEnabled = resolvedThinkingEnabled,
            resolvedSystemPrompt = resolvedSystemPrompt,
            activeConfig = activeConfig,
            targetSamplerConfig = targetSamplerConfig,
            modelPath = modelPath,
            options = options,
            engineChanged = engineChanged,
            conversationRecreated = conversationRecreated,
        )

        return port
    }

    private suspend fun handleResourceRecreation(
        engineChanged: Boolean,
        conversationChanged: Boolean,
        modelType: ModelType,
        configId: LocalModelConfigurationId,
        modelPath: String,
        resolvedThinkingEnabled: Boolean,
        onLoadingStarted: suspend () -> Unit,
        requestId: Int,
        requestCancellationEpoch: Int,
        activeConfig: ActiveModelConfiguration,
        isMultimodal: Boolean,
    ) {
        when {
            engineChanged -> {
                ensureRequestActive(requestId, requestCancellationEpoch, "Cancelled before engine creation")
                onLoadingStarted()
                Log.d(
                    TAG,
                    "Recreating LiteRT engine for modelType=$modelType, modelPath=$modelPath memory=${MemoryDiagnostics.snapshot()}",
                )
                factory.closeEngine()
                cache.clearTransientToolResults()
            }
            conversationChanged -> {
                Log.d(
                    TAG,
                    "Recreating LiteRT conversation for modelType=$modelType, configId=$configId, " +
                        "thinkingEnabled=$resolvedThinkingEnabled, hasTransientToolResults=${cache.hasTransientToolResults} " +
                        "memory=${MemoryDiagnostics.snapshot()}",
                )
                factory.closeConversation()
                cache.clearTransientToolResults()
            }
            else -> {
                Log.d(
                    TAG,
                    "Reusing LiteRT conversation for modelType=$modelType, configId=$configId, " +
                        "thinkingEnabled=$resolvedThinkingEnabled memory=${MemoryDiagnostics.snapshot()}",
                )
            }
        }

        factory.ensureEngineInitialized(
            modelPath = modelPath,
            contextWindow = activeConfig.contextWindow ?: ConversationSignatureCache.DEFAULT_CONTEXT_WINDOW,
            isMultimodal = isMultimodal,
        )

        if (
            !currentCoroutineContext().isActive ||
            !cancellationGate.isRequestActive(requestId) ||
            cancellationGate.cancellationEpoch.get() != requestCancellationEpoch
        ) {
            Log.w(TAG, "getConversation cancelled after engine initialization - closing engine before returning")
            factory.closeEngine()
            throw CancellationException("Cancelled after engine initialization")
        }
    }

    private suspend fun ensureConversationExists(
        modelType: ModelType,
        desiredSignature: ConversationSignature,
        resolvedSystemPrompt: String,
        targetSamplerConfig: SamplerConfig,
        options: GenerationOptions?,
        modelPath: String,
        contextWindow: Int,
        requestId: Int,
        requestCancellationEpoch: Int,
        engine: com.google.ai.edge.litertlm.Engine,
    ) {
        if (cache.conversation != null && cache.conversation?.isAlive == true) return

        cache.conversation?.close()
        cache.conversation = null
        cache.conversationPort = null

        val turnSnapshot =
            LiteRtTurnSnapshot(
                requestId = requestId,
                systemPrompt = resolvedSystemPrompt,
                contextWindow = contextWindow,
                history = cache.history,
                modelPath = modelPath,
                latestAssistantMessageId = options?.assistantMessageId,
                toolCatalog = options?.toolCatalog.orEmpty(),
                toolSearchIndex = options?.toolSearchIndex,
                toolCallStrategy = options?.toolCallStrategy ?: cache.toolCallStrategy,
                transientToolResultTokens = cache.transientToolResultTokens.get(),
            )
        val toolContext =
            LiteRtToolContext(
                modelType = modelType,
                modelId = options?.modelId ?: modelType.name,
                chatId = options?.chatId,
                userMessageId = options?.userMessageId,
                toolLoop = toolLoop,
                turn = turnSnapshot,
            )
        // Seed the LiteRT-local active-tool set from GenerationOptions on a fresh build, and
        // preserve any tool_discovery_tool activations accumulated against the prior conversation
        // when rebuilding within the same engine session (e.g., signature change after
        // discovery widens the callable set).
        val preloadNames = options?.activeTools?.map { it.name }.orEmpty()
        cache.toolActivationRegistry.seedTurnPreservingDiscovered(preloadNames)
        val seededActive = cache.toolActivationRegistry.orderedActiveNames()
        val conversationConfig =
            buildConversationConfig(
                signature = desiredSignature,
                systemPrompt = resolvedSystemPrompt,
                history = cache.history,
                samplerConfig = targetSamplerConfig,
                activeBackendIsNpu = cache.activeBackendIsNpu,
                toolContext = toolContext,
                activeToolNames = seededActive,
            )

        ensureRequestActive(requestId, requestCancellationEpoch, "Cancelled during conversation setup")

        Log.d(
            TAG,
            "Creating LiteRT conversation for modelType=$modelType historySize=${cache.history.size} " +
                "hasSearchTool=${desiredSignature.hasSearchTool} memoryBeforeCreate=${MemoryDiagnostics.snapshot()}",
        )
        cache.conversation = engine.createConversation(conversationConfig)
        Log.d(
            TAG,
            "LiteRT conversation created for modelType=$modelType memoryAfterCreate=${MemoryDiagnostics.snapshot()}",
        )

        if (
            !currentCoroutineContext().isActive ||
            !cancellationGate.isRequestActive(requestId) ||
            cancellationGate.cancellationEpoch.get() != requestCancellationEpoch
        ) {
            Log.w(TAG, "getConversation cancelled after conversation creation - cleaning up")
            factory.closeConversation()
            throw CancellationException("Cancelled immediately after conversation setup")
        }
    }

    private fun updateCacheAfterSuccess(
        desiredSignature: ConversationSignature,
        modelType: ModelType,
        configId: LocalModelConfigurationId,
        resolvedThinkingEnabled: Boolean,
        resolvedSystemPrompt: String,
        activeConfig: ActiveModelConfiguration,
        targetSamplerConfig: SamplerConfig,
        modelPath: String,
        options: GenerationOptions?,
        engineChanged: Boolean,
        conversationRecreated: Boolean,
    ) {
        cache.currentSignature = desiredSignature
        cache.currentModelType = modelType
        cache.currentConfigId = configId
        cache.currentThinkingEnabled = resolvedThinkingEnabled
        cache.currentSystemPrompt = resolvedSystemPrompt
        cache.currentContextWindow = activeConfig.contextWindow ?: ConversationSignatureCache.DEFAULT_CONTEXT_WINDOW
        cache.currentSamplerConfig = targetSamplerConfig
        cache.currentModelPath = modelPath
        cache.latestAssistantMessageId = options?.assistantMessageId
        cache.toolCatalog = options?.toolCatalog.orEmpty()
        cache.toolSearchIndex = options?.toolSearchIndex
        cache.toolCallStrategy = options?.toolCallStrategy ?: cache.toolCallStrategy

        Log.d(
            TAG,
            "getConversation decision: modelType=$modelType, configId=$configId, thinkingEnabled=$resolvedThinkingEnabled, " +
                "systemPrompt=${resolvedSystemPrompt.take(ConversationSignatureCache.LOG_TRUNCATE_LENGTH)}, " +
                "samplerConfig=$targetSamplerConfig, engineRecreated=$engineChanged, " +
                "conversationRecreated=$conversationRecreated, memory=${MemoryDiagnostics.snapshot()}",
        )
    }

    private suspend fun ensureRequestActive(
        requestId: Int,
        requestCancellationEpoch: Int,
        reason: String,
    ) {
        if (
            !currentCoroutineContext().isActive ||
            !cancellationGate.isRequestActive(requestId) ||
            cancellationGate.cancellationEpoch.get() != requestCancellationEpoch
        ) {
            Log.w(
                TAG,
                "getConversation cancelled reason=$reason isActive=${currentCoroutineContext().isActive} " +
                    "requestId=$requestId currentRequestId=${cancellationGate.currentRequestId()} " +
                    "isCancelled=${cancellationGate.isCurrentGenerationCancelled} requestEpoch=$requestCancellationEpoch " +
                    "currentEpoch=${cancellationGate.cancellationEpoch.get()}",
            )
            throw CancellationException(reason)
        }
    }

    private fun resolveSamplerConfig(
        options: GenerationOptions?,
        activeConfig: ActiveModelConfiguration,
    ): SamplerConfig =
        SamplerConfig(
            temperature = options?.temperature?.toDouble() ?: activeConfig.temperature ?: DEFAULT_TEMPERATURE,
            topP = options?.topP?.toDouble() ?: activeConfig.topP ?: DEFAULT_TOP_P,
            topK = options?.topK ?: activeConfig.topK ?: DEFAULT_TOP_K,
        )

    companion object {
        private const val TAG = "ConversationManager"
        private const val DEFAULT_TEMPERATURE = 0.7
        private const val DEFAULT_TOP_P = 0.95
        private const val DEFAULT_TOP_K = 40
    }
}
