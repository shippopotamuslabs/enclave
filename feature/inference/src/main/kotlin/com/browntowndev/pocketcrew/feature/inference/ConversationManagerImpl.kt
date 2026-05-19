package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.AgentPolicyPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.feature.inference.conversation.ConversationEngineCreatorHolder
import com.browntowndev.pocketcrew.feature.inference.conversation.ConversationCancellationGate
import com.browntowndev.pocketcrew.feature.inference.conversation.ConversationHistoryHandler
import com.browntowndev.pocketcrew.feature.inference.conversation.ConversationSessionCoordinator
import com.browntowndev.pocketcrew.feature.inference.conversation.ConversationSignatureCache
import com.browntowndev.pocketcrew.feature.inference.conversation.LiteRtConversationFactory
import com.browntowndev.pocketcrew.feature.inference.conversation.LiteRtToolCallLoop
import com.browntowndev.pocketcrew.feature.inference.conversation.NoopAgentPolicyPort
import com.browntowndev.pocketcrew.feature.inference.conversation.resolveModelPath
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Thin facade over LiteRT conversation lifecycle. Delegates engine creation, tool execution,
 * signature caching, and history management to focused collaborators in `conversation/`.
 */
class ConversationManagerImpl
    @Inject
    constructor(
        private val context: Context,
        private val localModelRepository: LocalModelRepositoryPort,
        private val activeModelProvider: ActiveModelProviderPort,
        private val loggingPort: LoggingPort,
        private val toolExecutor: ToolExecutorPort,
        private val agentPolicyPort: AgentPolicyPort = NoopAgentPolicyPort,
    ) : ConversationManager {
        private val cache = ConversationSignatureCache()
        private val cancellationGate = ConversationCancellationGate()

        @VisibleForTesting
        internal val engineCreatorHolder = ConversationEngineCreatorHolder()

        private val factory: LiteRtConversationFactory by lazy {
            LiteRtConversationFactory(context, cache) { modelPath, backend, visionBackend, contextWindow, cacheDir ->
                engineCreatorHolder.create(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    contextWindow = contextWindow,
                    cacheDir = cacheDir,
                    defaultCreator = ::createEngine,
                )
            }
        }
        private val toolLoop =
            LiteRtToolCallLoop(
                cache = cache,
                toolExecutor = toolExecutor,
                loggingPort = loggingPort,
                agentPolicyPort = agentPolicyPort,
                cancellationGate = cancellationGate,
            )
        private val sessionCoordinator: ConversationSessionCoordinator by lazy {
            ConversationSessionCoordinator(
                cache = cache,
                factory = factory,
                toolLoop = toolLoop,
                cancellationGate = cancellationGate,
            )
        }
        private val mutex = Mutex()

        override fun cancelCurrentGeneration() {
            Log.d(TAG, "cancelCurrentGeneration called")
            cancellationGate.cancelCurrentGeneration()
        }

        override fun cancelProcess() {
            Log.i(TAG, "cancelProcess requested - resetting manager state")
            cancellationGate.cancelProcess()
            cache.conversationPort?.let { port ->
                Log.d(TAG, "Triggering native cancelProcess on active port")
                port.cancelProcess()
            }
        }

        override suspend fun getConversation(
            modelType: ModelType,
            options: GenerationOptions?,
            onLoadingStarted: suspend () -> Unit,
        ): LiteRtConversation =
            withContext(Dispatchers.IO) {
                cancellationGate.resetForNewRequest()
                mutex.withLock {
                    val activeConfig =
                        activeModelProvider.getActiveConfiguration(modelType)
                            ?: throw IllegalStateException("No active configuration for $modelType")

                    validateModelType(modelType, activeConfig)

                    val configId = activeConfig.id as LocalModelConfigurationId
                    val asset =
                        localModelRepository.getAssetByConfigId(configId)
                            ?: throw IllegalStateException(
                                "No registered asset for config ${activeConfig.id}. Download a model first.",
                            )

                    val modelPath = getModelPath(asset.metadata.localFileName)

                    sessionCoordinator.getConversation(
                        modelType = modelType,
                        options = options,
                        activeConfig = activeConfig,
                        configId = configId,
                        modelPath = modelPath,
                        isMultimodal = asset.metadata.isMultimodal,
                        onLoadingStarted = onLoadingStarted,
                    )
                }
            }

        override suspend fun closeConversation() {
            mutex.withLock {
                factory.closeConversation()
            }
        }

        override suspend fun setHistory(messages: List<ChatMessage>) {
            mutex.withLock {
                ConversationHistoryHandler.applyHistoryUpdate(
                    cache = cache,
                    messages = messages,
                    closeConversation = { factory.closeConversation() },
                )
            }
        }

        override suspend fun closeEngine() {
            mutex.withLock {
                factory.closeEngine()
            }
        }

        @VisibleForTesting
        internal fun getModelPath(filename: String): String = resolveModelPath(context, filename)

        @VisibleForTesting
        internal fun createEngine(
            modelPath: String,
            backend: Backend,
            visionBackend: Backend?,
            contextWindow: Int,
            cacheDir: String?,
        ): Engine =
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    maxNumTokens = contextWindow,
                    cacheDir = cacheDir,
                ),
            )

        @VisibleForTesting
        internal suspend fun executeToolSafelyForTest(request: ToolCallRequest): String = toolLoop.executeToolSafely(request)

        @VisibleForTesting
        internal var conversation
            get() = cache.conversation
            set(value) {
                cache.conversation = value
            }

        @VisibleForTesting
        internal var conversationPort
            get() = cache.conversationPort
            set(value) {
                cache.conversationPort = value
            }

        @VisibleForTesting
        internal var history
            get() = cache.history
            set(value) {
                cache.history = value
            }

        @VisibleForTesting
        internal val isCurrentGenerationCancelled
            get() = cancellationGate.isCurrentGenerationCancelled

        @VisibleForTesting
        internal val cancellationEpoch
            get() = cancellationGate.cancellationEpoch

        private fun validateModelType(
            modelType: ModelType,
            activeConfig: com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration,
        ) {
            if (modelType == ModelType.VISION) {
                throw IllegalStateException("ConversationManager does not run vision models. Vision is API-only.")
            }
            if (!activeConfig.isLocal) {
                throw IllegalStateException(
                    "ConversationManager cannot run API models. ModelType $modelType is mapped to an API configuration.",
                )
            }
        }

        companion object {
            private const val TAG = "ConversationManager"
        }
    }
