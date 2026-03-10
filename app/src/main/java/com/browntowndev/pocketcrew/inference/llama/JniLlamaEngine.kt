package com.browntowndev.pocketcrew.inference.llama

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI-backed implementation of LlamaEnginePort.
 * Uses JNI to interact with native llama.cpp library.
 *
 * IMPORTANT: llama.cpp is NOT thread-safe. All llama operations must run on a single thread.
 * We use a dedicated single-thread executor to ensure thread safety.
 */
@Singleton
class JniLlamaEngine @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LlamaEnginePort {

    companion object {
        private const val TAG = "JniLlamaEngine"

        // Timeout for generation - thinking models may need significantly longer
        // 15 minutes for thinking models, 15 minutes for regular (Qwen is slow at ~6 tokens/sec)
        private const val GENERATION_TIMEOUT_SECONDS_THINKING = 900L
        private const val GENERATION_TIMEOUT_SECONDS_REGULAR = 900L

        // Timeout for unload - need longer to let ongoing generation finish
        private const val UNLOAD_TIMEOUT_SECONDS = 30L

        init {
            System.loadLibrary("llama-jni")
        }
    }

    // Dedicated single-thread executor for llama operations to ensure thread safety
    // llama.cpp is NOT thread-safe and requires all operations on the same thread
    private val llamaExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama-thread").apply { isDaemon = true }
    }

    private var currentConfig: LlamaModelConfig? = null
    private val loaded = AtomicBoolean(false)
    private val generating = AtomicBoolean(false)
    private val history = mutableListOf<ChatMessage>()

    override suspend fun initialize(config: LlamaModelConfig) = withContext(ioDispatcher) {
        Log.i(TAG, "Initializing llama model: ${config.modelPath}")
        Log.i(TAG, "  contextWindow=${config.sampling.contextWindow}, maxTokens=${config.sampling.maxTokens}, threads=${config.sampling.threads}, batchSize=${config.sampling.batchSize}, gpuLayers=${config.sampling.gpuLayers}")

        if (loaded.get()) {
            Log.i(TAG, "Unloading previous model")
            unloadInternal()
        }

        // Run on llama thread for thread safety
        val startTime = System.currentTimeMillis()
        val future = llamaExecutor.submit<Boolean> {
            nativeLoadModel(
                modelPath = config.modelPath,
                contextSize = config.sampling.contextWindow,
                threads = config.sampling.threads,
                batchSize = config.sampling.batchSize,
                gpuLayers = config.sampling.gpuLayers
            )
        }
        val ok: Boolean = future.get()
        val loadTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "Model loaded in ${loadTime}ms, success=$ok")
        check(ok) { "Failed to load GGUF model: ${config.modelPath}" }

        currentConfig = config
        loaded.set(true)
        history.clear()
        history += ChatMessage(ChatRole.SYSTEM, config.systemPrompt)
    }

    override suspend fun startConversation(systemPrompt: String?) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        history.clear()

        val prompt = systemPrompt ?: currentConfig?.systemPrompt
            ?: "You are a helpful assistant."

        history += ChatMessage(ChatRole.SYSTEM, prompt)
    }

    override suspend fun appendMessage(message: ChatMessage) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        history += message
    }

    override fun generate(): Flow<GenerationEvent> = callbackFlow {
        Log.i(TAG, "Starting generation...")
        check(loaded.get()) { "Engine not initialized" }
        check(generating.compareAndSet(false, true)) { "Generation already in progress" }

        val cfg = requireNotNull(currentConfig) { "Missing config" }
        // Pass chat messages to native - let llama.cpp handle chat template formatting
        val roles = history.map { it.toNativeMessage().first }.toTypedArray()
        val contents = history.map { it.toNativeMessage().second }.toTypedArray()
        Log.i(
            TAG,
            "Generation config: temp=${cfg.sampling.temperature}, topK=${cfg.sampling.topK}, topP=${cfg.sampling.topP}, maxTokens=${cfg.sampling.maxTokens}"
        )

        // Thread-safe callback that proxies to the callbackFlow on the proper thread
        val callback = object : NativeTokenCallback {
            private val sb = StringBuilder()
            private var isClosed = false

            override fun onToken(token: String) {
                if (isClosed) return
                sb.append(token)
                trySend(GenerationEvent.Token(token))
            }

            override fun onComplete(promptTokens: Int, generatedTokens: Int) {
                if (isClosed) return
                isClosed = true
                Log.i(TAG, "Generation complete: $generatedTokens tokens generated")
                trySend(
                    GenerationEvent.Completed(
                        fullText = sb.toString(),
                        promptTokens = promptTokens,
                        generatedTokens = generatedTokens
                    )
                )
                history += ChatMessage(ChatRole.ASSISTANT, sb.toString())
                generating.set(false)
                close()
            }

            override fun onError(message: String) {
                if (isClosed) return
                isClosed = true
                trySend(GenerationEvent.Error(IllegalStateException(message)))
                generating.set(false)
                close()
            }
        }

        // Run native llama operation on dedicated single thread to ensure thread safety
        // llama.cpp is NOT thread-safe and requires all operations on the same thread
        try {
            // Convert thinkingEnabled to llama.cpp reasoning_budget:
            // 0 = thinking enabled, -1 = unlimited
            val reasoningBudget = if (cfg.sampling.thinkingEnabled) 0 else 0
            Log.i(TAG, "Starting completion with reasoning_budget=$reasoningBudget")

            // Use longer timeout for thinking models
            val timeoutSeconds = if (cfg.sampling.thinkingEnabled) {
                GENERATION_TIMEOUT_SECONDS_THINKING
            } else {
                GENERATION_TIMEOUT_SECONDS_REGULAR
            }
            Log.i(TAG, "Generation timeout: $timeoutSeconds seconds")

            // Submit to llama executor thread to ensure all llama operations run on the same thread
            val future = llamaExecutor.submit<Unit> {
                nativeStartCompletion(
                    roles = roles,
                    contents = contents,
                    temperature = cfg.sampling.temperature,
                    topK = cfg.sampling.topK,
                    topP = cfg.sampling.topP,
                    maxTokens = cfg.sampling.maxTokens,
                    repeatPenalty = cfg.sampling.repeatPenalty,
                    reasoningBudget = reasoningBudget,
                    callback = callback
                )
            }
            // Wait for completion - blocking here is OK since we're in a flow and the callback
            // will stream tokens back via trySend()
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            trySend(GenerationEvent.Error(e))
            generating.set(false)
            close(e)
        }

        awaitClose {
            // Always try to stop - it's safe to call even if generation already completed
            // This ensures we don't have a race between completion and cleanup
            llamaExecutor.submit {
                nativeStopCompletion()
            }
            generating.set(false)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun stopGeneration() = withContext(ioDispatcher) {
        if (generating.compareAndSet(true, false)) {
            // Must call on llama thread for thread safety
            llamaExecutor.submit {
                nativeStopCompletion()
            }
        }
    }

    override suspend fun resetConversation(systemPrompt: String?) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        history.clear()
        history += ChatMessage(
            ChatRole.SYSTEM,
            systemPrompt ?: currentConfig?.systemPrompt ?: "You are a helpful assistant."
        )
        // Must call on llama thread for thread safety
        llamaExecutor.submit<Unit> {
            nativeClearKvCache()
        }.get(10, TimeUnit.SECONDS)
    }

    override suspend fun unload(): Unit = withContext(ioDispatcher) {
        // First, stop any ongoing generation to unblock the llama thread
        // This sets g_generating = false in native code, allowing the loop to exit
        llamaExecutor.submit {
            nativeStopCompletion()
            Unit
        }.get(10, TimeUnit.SECONDS)

        // Give the native thread a moment to exit the generation loop
        kotlinx.coroutines.delay(500)

        // Now unload the model with longer timeout
        llamaExecutor.submit {
            unloadInternal()
            Unit
        }.get(UNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun isLoaded(): Boolean = loaded.get()

    private fun unloadInternal() {
        try {
            nativeStopCompletion()
        } catch (_: Throwable) {
            Log.w(TAG, "Ignoring stop error during unload")
        }
        nativeUnloadModel()
        history.clear()
        currentConfig = null
        loaded.set(false)
        generating.set(false)
    }

    private fun buildChatPrompt(messages: List<ChatMessage>): String {
        // Prompt format with newlines preserved - important for model structure
        return buildString {
            for (message in messages) {
                // Preserve the content as-is, just clean up excess whitespace
                val content = message.content
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .trim()
                when (message.role) {
                    ChatRole.SYSTEM -> append("SYSTEM: $content\n")
                    ChatRole.USER -> append("USER: $content\n")
                    ChatRole.ASSISTANT -> append("ASSISTANT: $content\n")
                }
            }
            append("ASSISTANT:")
        }
    }

    // Native method declarations
    private external fun nativeLoadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        batchSize: Int,
        gpuLayers: Int
    ): Boolean

    private external fun nativeUnloadModel()

    private external fun nativeClearKvCache()

    private external fun nativeStartCompletion(
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int,
        repeatPenalty: Float,
        reasoningBudget: Int,  // -1 = unlimited, 0 = disabled
        callback: NativeTokenCallback
    )

    private fun ChatMessage.toNativeMessage(): Pair<String, String> {
        val roleStr = when (role) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
        }
        return roleStr to content
    }

    private external fun nativeStopCompletion()
}

/**
 * Callback interface for native token streaming.
 */
interface NativeTokenCallback {
    fun onToken(token: String)
    fun onComplete(promptTokens: Int, generatedTokens: Int)
    fun onError(message: String)
}
