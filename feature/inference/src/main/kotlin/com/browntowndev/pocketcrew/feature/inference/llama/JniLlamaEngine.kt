package com.browntowndev.pocketcrew.feature.inference.llama

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage

/**
 * JNI-backed implementation of LlamaEnginePort.
 * Uses JNI to interact with native llama.cpp library.
 *
 * IMPORTANT: llama.cpp is NOT thread-safe. All llama operations must run on a single thread.
 * We use a dedicated single-thread executor to ensure thread safety.
 */
@Singleton
class JniLlamaEngine
    @Inject
    constructor(
        private val gpuProfiler: GpuProfiler,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : LlamaEnginePort {
        companion object {
            private const val TAG = "JniLlamaEngine"
            private const val UNLOAD_TIMEOUT_SECONDS = 30L

            @JvmStatic
            private external fun detectSveBits(): Int

            init {
                LlamaNativeLibraryLoader.loadOptimalLibrary { detectSveBits() }
            }
        }

        private val llamaExecutor =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "llama-thread").apply { isDaemon = true }
            }

        internal val modelLifecycle =
            LlamaModelLifecycle(
                getContextSize = { getContextSizeForCompression() },
                getContextUsage = { getContextUsageForCompression() },
                compressContext = { seqId, factor -> nativeCompressContext(seqId, factor) },
                getState = { nativeGetState() },
                setState = { nativeSetState(it) },
            )

        private val generating = AtomicBoolean(false)
        private val history = mutableListOf<ChatMessage>()

        override suspend fun initialize(config: LlamaModelConfig) =
            withContext(ioDispatcher) {
                Log.i(TAG, "Initializing llama model: ${config.modelPath}")
                @Suppress("MaxLineLength")
                Log.i(
                    TAG,
                    "  contextWindow=${config.sampling.contextWindow}, maxTokens=${config.sampling.maxTokens}, batchSize=${config.sampling.batchSize}, gpuLayers=${config.sampling.gpuLayers}",
                )

                if (modelLifecycle.loaded.get()) {
                    Log.i(TAG, "Unloading previous model")
                    unloadInternal()
                }

                val startTime = System.currentTimeMillis()
                val detectedBackend = gpuProfiler.detectOptimalBackend()
                val backendDescription = gpuProfiler.getBackendDescription()
                val gpuName = gpuProfiler.detectGpuName()
                Log.i(TAG, "GPU Backend: $backendDescription")
                Log.i(TAG, "GPU detected: $gpuName")

                if (detectedBackend == LlamaBackend.CPU) {
                    Log.w(TAG, "=== WARNING: Running on CPU only! GPU acceleration is DISABLED ===")
                    Log.w(TAG, "=== To enable GPU: change GpuProfiler to return Vulkan backend ===")
                } else {
                    Log.i(TAG, "=== GPU acceleration ENABLED ===")
                }

                val future =
                    llamaExecutor.submit<Boolean> {
                        nativeLoadModel(
                            modelPath = config.modelPath,
                            mmprojPath = config.mmprojPath,
                            contextSize = config.sampling.contextWindow,
                            batchSize = config.sampling.batchSize,
                            gpuLayers = config.sampling.gpuLayers,
                            backendType = detectedBackend.value,
                        )
                    }
                val ok: Boolean = future.get()
                val loadTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Model loaded in ${loadTime}ms, success=$ok")
                check(ok) { "Failed to load GGUF model: ${config.modelPath}" }

                modelLifecycle.currentConfig = config
                modelLifecycle.loaded.set(true)
                history.clear()
                history += ChatMessage(ChatRole.SYSTEM, config.systemPrompt)
            }

        override suspend fun startConversation(systemPrompt: String?) =
            withContext(ioDispatcher) {
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                history.clear()

                val prompt =
                    systemPrompt ?: modelLifecycle.currentConfig?.systemPrompt
                        ?: "You are a helpful assistant."

                history += ChatMessage(ChatRole.SYSTEM, prompt)
            }

        override suspend fun appendMessage(message: DomainChatMessage) =
            withContext(ioDispatcher) {
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                history +=
                    ChatMessage(
                        role = ChatRole.fromDomainRole(message.role),
                        content = message.content,
                    )
            }

        override suspend fun setHistory(messages: List<DomainChatMessage>) =
            withContext(ioDispatcher) {
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                history.clear()
                val systemPrompt =
                    modelLifecycle.currentConfig?.systemPrompt ?: "You are a helpful assistant."
                history += ChatMessage(ChatRole.SYSTEM, systemPrompt)
                messages.forEach { domainMsg ->
                    history +=
                        ChatMessage(
                            role = ChatRole.fromDomainRole(domainMsg.role),
                            content = domainMsg.content,
                        )
                }
            }

        override fun generate(): Flow<GenerationEvent> =
            callbackFlow {
                Log.i(TAG, "Starting generation...")
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                check(generating.compareAndSet(false, true)) { "Generation already in progress" }

                val cfg = requireNotNull(modelLifecycle.currentConfig) { "Missing config" }
                val params = LlamaGenerationOptionsMapper.resolveFromSampling(cfg.sampling)
                val (roles, contents) = LlamaGenerationSession.buildNativeMessages(history)
                Log.i(
                    TAG,
                    @Suppress("MaxLineLength")
                    "Generation config: temp=${params.temperature}, topK=${params.topK}, topP=${params.topP}, maxTokens=${params.maxTokens}",
                )
                Log.i(TAG, "Starting completion with reasoning_budget=${params.reasoningBudget}")
                Log.i(TAG, "Penalties: freq=${params.penaltyFreq}, present=${params.penaltyPresent}")
                Log.i(TAG, "Generation timeout: ${params.timeoutSeconds} seconds")

                val callback =
                    LlamaGenerationSession.createTokenCallback(
                        emit = ::trySend,
                        onGenerationComplete = { promptTokens, generatedTokens, fullText ->
                            modelLifecycle.recordGenerationComplete(promptTokens, generatedTokens)
                            modelLifecycle.checkAndCompressContext()
                            history += ChatMessage(ChatRole.ASSISTANT, fullText)
                            generating.set(false)
                        },
                        onTerminal = {
                            generating.set(false)
                            close()
                        },
                    )

                try {
                    val future =
                        llamaExecutor.submit<Unit> {
                            startCompletion(
                                roles = roles,
                                contents = contents,
                                imagePaths = emptyArray(),
                                temperature = params.temperature,
                                topK = params.topK,
                                topP = params.topP,
                                minP = params.minP,
                                maxTokens = params.maxTokens,
                                repeatPenalty = cfg.sampling.repeatPenalty,
                                penaltyFreq = params.penaltyFreq,
                                penaltyPresent = params.penaltyPresent,
                                reasoningBudget = params.reasoningBudget,
                                callback = callback,
                            )
                        }
                    future.get(params.timeoutSeconds, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during generation", e)
                    trySend(GenerationEvent.Error(e))
                    generating.set(false)
                    close(e)
                }

                awaitClose {
                    llamaExecutor.submit { nativeStopCompletion() }
                    generating.set(false)
                }
            }.flowOn(Dispatchers.IO)

        override fun generateWithOptions(options: GenerationOptions): Flow<GenerationEvent> =
            callbackFlow {
                Log.i(TAG, "Starting generation with options: reasoningBudget=${options.reasoningBudget}")
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                check(generating.compareAndSet(false, true)) { "Generation already in progress" }

                val cfg = requireNotNull(modelLifecycle.currentConfig) { "Missing config" }
                val params = LlamaGenerationOptionsMapper.resolveFromOptions(options, cfg)
                val (roles, contents) =
                    LlamaGenerationSession.buildNativeMessages(
                        history = history,
                        systemPromptOverride = options.systemPrompt,
                        mediaMarkerCount = options.imageUris.size,
                    )

                @Suppress("MaxLineLength")
                Log.i(
                    TAG,
                    "Generation with options: reasoningBudget=${params.reasoningBudget}, penaltyFreq=${params.penaltyFreq}, penaltyPresent=${params.penaltyPresent}, timeout=${params.timeoutSeconds}, temp=${params.temperature}",
                )

                val callback =
                    LlamaGenerationSession.createTokenCallback(
                        emit = ::trySend,
                        onGenerationComplete = { promptTokens, generatedTokens, fullText ->
                            modelLifecycle.recordGenerationComplete(promptTokens, generatedTokens)
                            modelLifecycle.checkAndCompressContext()
                            history += ChatMessage(ChatRole.ASSISTANT, fullText)
                            generating.set(false)
                        },
                        onTerminal = {
                            generating.set(false)
                            close()
                        },
                    )

                try {
                    val future =
                        llamaExecutor.submit<Unit> {
                            startCompletion(
                                roles = roles,
                                contents = contents,
                                imagePaths = options.imageUris.toTypedArray(),
                                temperature = params.temperature,
                                topK = params.topK,
                                topP = params.topP,
                                minP = params.minP,
                                maxTokens = params.maxTokens,
                                repeatPenalty = cfg.sampling.repeatPenalty,
                                penaltyFreq = params.penaltyFreq,
                                penaltyPresent = params.penaltyPresent,
                                reasoningBudget = params.reasoningBudget,
                                callback = callback,
                            )
                        }
                    future.get(params.timeoutSeconds, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during generation", e)
                    trySend(GenerationEvent.Error(e))
                    generating.set(false)
                    close(e)
                }

                awaitClose {
                    llamaExecutor.submit { nativeStopCompletion() }
                    generating.set(false)
                }
            }.flowOn(Dispatchers.IO)

        override suspend fun stopGeneration() =
            withContext(ioDispatcher) {
                if (generating.compareAndSet(true, false)) {
                    llamaExecutor.submit { nativeStopCompletion() }
                }
            }

        override suspend fun resetConversation(systemPrompt: String?) =
            withContext(ioDispatcher) {
                check(modelLifecycle.loaded.get()) { "Engine not initialized" }
                history.clear()
                history +=
                    ChatMessage(
                        ChatRole.SYSTEM,
                        systemPrompt ?: modelLifecycle.currentConfig?.systemPrompt ?: "You are a helpful assistant.",
                    )
                llamaExecutor
                    .submit<Unit> {
                        nativeClearKvCache()
                    }.get(10, TimeUnit.SECONDS)
            }

        override suspend fun unload(): Unit =
            withContext(ioDispatcher) {
                llamaExecutor
                    .submit {
                        nativeStopCompletion()
                        Unit
                    }.get(10, TimeUnit.SECONDS)

                delay(500)

                llamaExecutor
                    .submit {
                        unloadInternal()
                        Unit
                    }.get(UNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }

        override fun isLoaded(): Boolean = modelLifecycle.loaded.get()

        private fun unloadInternal() {
            try {
                nativeStopCompletion()
            } catch (_: Throwable) {
                Log.w(TAG, "Ignoring stop error during unload")
            }
            nativeUnloadModel()
            history.clear()
            modelLifecycle.clearAfterUnload()
            generating.set(false)
        }

        internal fun getContextSizeForCompression(): Int = nativeGetContextSize()

        internal fun getContextUsageForCompression(): Int = nativeGetContextUsage()

        internal fun applyCompressionForContext(factor: Int): Boolean = modelLifecycle.applyCompression(factor)

        private external fun nativeLoadModel(
            modelPath: String,
            mmprojPath: String?,
            contextSize: Int,
            batchSize: Int,
            gpuLayers: Int,
            backendType: Int,
        ): Boolean

        private external fun nativeUnloadModel()

        private external fun nativeClearKvCache()

        internal fun startCompletion(
            roles: Array<String>,
            contents: Array<String>,
            imagePaths: Array<String>,
            temperature: Float,
            topK: Int,
            topP: Float,
            minP: Float,
            maxTokens: Int,
            repeatPenalty: Float,
            penaltyFreq: Float,
            penaltyPresent: Float,
            reasoningBudget: Int,
            callback: NativeTokenCallback,
        ) {
            nativeStartCompletion(
                roles,
                contents,
                imagePaths,
                temperature,
                topK,
                topP,
                minP,
                maxTokens,
                repeatPenalty,
                penaltyFreq,
                penaltyPresent,
                reasoningBudget,
                callback,
            )
        }

        private external fun nativeStartCompletion(
            roles: Array<String>,
            contents: Array<String>,
            imagePaths: Array<String>,
            temperature: Float,
            topK: Int,
            topP: Float,
            minP: Float,
            maxTokens: Int,
            repeatPenalty: Float,
            penaltyFreq: Float,
            penaltyPresent: Float,
            reasoningBudget: Int,
            callback: NativeTokenCallback,
        )

        private external fun nativeStopCompletion()

        private external fun nativeGetContextSize(): Int

        private external fun nativeGetContextUsage(): Int

        private external fun nativeCompressContext(
            seqId: Int,
            factor: Int,
        ): Boolean

        @Suppress("UnusedPrivateMember")
        private external fun nativeGetStateSize(): Int

        private external fun nativeGetState(): ByteArray?

        private external fun nativeSetState(state: ByteArray): Boolean
    }

/**
 * Callback interface for native token streaming.
 */
interface NativeTokenCallback {
    fun onToken(token: String)

    fun onComplete(
        promptTokens: Int,
        generatedTokens: Int,
    )

    fun onError(message: String)
}
