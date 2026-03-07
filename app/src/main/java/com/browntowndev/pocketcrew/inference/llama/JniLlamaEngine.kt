package com.browntowndev.pocketcrew.inference.llama

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI-backed implementation of LlamaEnginePort.
 * Uses JNI to interact with native llama.cpp library.
 */
@Singleton
class JniLlamaEngine @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LlamaEnginePort {

    companion object {
        private const val TAG = "JniLlamaEngine"

        init {
            System.loadLibrary("llama-jni")
        }
    }

    private var currentConfig: LlamaModelConfig? = null
    private val loaded = AtomicBoolean(false)
    private val generating = AtomicBoolean(false)
    private val history = mutableListOf<ChatMessage>()

    override suspend fun initialize(config: LlamaModelConfig) = withContext(ioDispatcher) {
        if (loaded.get()) {
            unloadInternal()
        }

        val ok = nativeLoadModel(
            modelPath = config.modelPath,
            contextSize = config.sampling.contextSize,
            threads = config.sampling.threads,
            batchSize = config.sampling.batchSize,
            gpuLayers = config.sampling.gpuLayers
        )
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
        check(loaded.get()) { "Engine not initialized" }
        check(generating.compareAndSet(false, true)) { "Generation already in progress" }

        val cfg = requireNotNull(currentConfig) { "Missing config" }
        val prompt = buildChatPrompt(history)

        val callback = object : NativeTokenCallback {
            private val sb = StringBuilder()

            override fun onToken(token: String) {
                sb.append(token)
                trySend(GenerationEvent.Token(token))
            }

            override fun onComplete(promptTokens: Int, generatedTokens: Int) {
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
                trySend(GenerationEvent.Error(IllegalStateException(message)))
                generating.set(false)
                close()
            }
        }

        nativeStartCompletion(
            prompt = prompt,
            temperature = cfg.sampling.temperature,
            topK = cfg.sampling.topK,
            topP = cfg.sampling.topP,
            maxTokens = cfg.sampling.maxTokens,
            repeatPenalty = cfg.sampling.repeatPenalty,
            callback = callback
        )

        awaitClose {
            if (generating.get()) {
                nativeStopCompletion()
                generating.set(false)
            }
        }
    }

    override suspend fun stopGeneration() = withContext(ioDispatcher) {
        if (generating.compareAndSet(true, false)) {
            nativeStopCompletion()
        }
    }

    override suspend fun resetConversation(systemPrompt: String?) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        history.clear()
        history += ChatMessage(
            ChatRole.SYSTEM,
            systemPrompt ?: currentConfig?.systemPrompt ?: "You are a helpful assistant."
        )
        nativeClearKvCache()
    }

    override suspend fun unload() = withContext(ioDispatcher) {
        unloadInternal()
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
        // Replace with the exact template required by your model.
        return buildString {
            for (message in messages) {
                when (message.role) {
                    ChatRole.SYSTEM -> append("<|system|>\n${message.content}\n")
                    ChatRole.USER -> append("<|user|>\n${message.content}\n")
                    ChatRole.ASSISTANT -> append("<|assistant|>\n${message.content}\n")
                }
            }
            append("<|assistant|>\n")
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
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int,
        repeatPenalty: Float,
        callback: NativeTokenCallback
    )

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
