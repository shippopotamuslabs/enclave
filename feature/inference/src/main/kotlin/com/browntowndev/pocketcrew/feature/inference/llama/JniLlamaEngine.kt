package com.browntowndev.pocketcrew.feature.inference.llama

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    private val gpuProfiler: GpuProfiler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LlamaEnginePort {

    companion object {
        private const val TAG = "JniLlamaEngine"

        // Timeout for generation - thinking models may need significantly longer
        // 30 minutes for thinking models, 15 minutes for regular (Qwen is slow at ~6 tokens/sec)
        private const val GENERATION_TIMEOUT_SECONDS_THINKING = 1800L
        private const val GENERATION_TIMEOUT_SECONDS_REGULAR = 900L

        // Timeout for unload - need longer to let ongoing generation finish
        private const val UNLOAD_TIMEOUT_SECONDS = 30L

        // Native library names for dual-library approach
        private const val LIBRARY_CPUDETECT = "cpudetect"
        private const val LIBRARY_SVE = "llama-jni-sve"
        private const val LIBRARY_NEON = "llama-jni-neon"

        // Declared before init runs - cpudetect must be loaded first
        @JvmStatic
        private external fun detectSveBits(): Int

        init {
            loadOptimalLibrary()
        }

        /**
         * Load the optimal native library based on hardware SVE support.
         *
         * Strategy:
         * 1. Load cpudetect.so first (tiny library with no llama.cpp dependency)
         * 2. Query SVE vector length via prctl
         * 3. Load exactly one llama library - whichever matches the hardware
         */
        private fun loadOptimalLibrary() {
            // Step 1: Load cpudetect library first (no symbol conflicts)
            try {
                Log.i(TAG, "Loading CPU detector: $LIBRARY_CPUDETECT")
                System.loadLibrary(LIBRARY_CPUDETECT)
            } catch (e: Throwable) {
                Log.e(TAG, "Critical: cpudetect library failed to load. Are we in a unit test?", e)
                return
            }

            // Step 2: Query SVE via prctl (bypasses SELinux file restrictions)
            val sveBits = try {
                detectSveBits()
            } catch (e: Throwable) {
                Log.w(TAG, "SVE detection failed: ${e.message}")
                0
            }

            // Step 3: Check for blacklisted devices (Tensor G3 has broken 128-bit SVE)
            val isBlacklisted = isKnownSveBlacklisted()

            // Step 4: Load exactly one llama library based on detection
            val libraryToLoad = when {
                isBlacklisted.first -> {
                    Log.w(TAG, "Blacklisted device: ${isBlacklisted.second}. Using NEON.")
                    LIBRARY_NEON
                }
                sveBits >= 256 -> {
                    Log.i(TAG, "SVE $sveBits-bit confirmed. Loading SVE library.")
                    LIBRARY_SVE
                }
                sveBits > 0 -> {
                    Log.i(TAG, "SVE present but narrow (${sveBits}-bit < 256). Using NEON.")
                    LIBRARY_NEON
                }
                else -> {
                    Log.i(TAG, "No SVE support detected. Using NEON.")
                    LIBRARY_NEON
                }
            }

            try {
                Log.i(TAG, "Loading llama library: $libraryToLoad")
                System.loadLibrary(libraryToLoad)
                Log.i(TAG, "Library loaded successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Critical: Failed to load $libraryToLoad", e)
            }
        }

        /**
         * Check if device is known to have problematic (128-bit) SVE implementation.
         */
        private fun isKnownSveBlacklisted(): Pair<Boolean, String> {
            val socModel = android.os.Build.SOC_MODEL.uppercase()
            if (socModel == "GS301") {
                return Pair(true, "Tensor G3 (GS301)")
            }

            val device = android.os.Build.DEVICE.lowercase()
            val pixel8Family = setOf("shiba", "husky", "akita")
            if (device in pixel8Family) {
                return Pair(true, "Pixel 8 family ($device)")
            }

            return Pair(false, "")
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

    // Context management
    private var lastPromptTokens = 0
    private var lastGeneratedTokens = 0


    // Compression thresholds
    // Trigger compression at 80% of context window
    private val COMPRESSION_THRESHOLD_RATIO = 0.8f
    // Compress by factor of 2 (halves the context)
    private val COMPRESSION_FACTOR = 2

    override suspend fun initialize(config: LlamaModelConfig) = withContext(ioDispatcher) {
        Log.i(TAG, "Initializing llama model: ${config.modelPath}")
        Log.i(TAG, "  contextWindow=${config.sampling.contextWindow}, maxTokens=${config.sampling.maxTokens}, batchSize=${config.sampling.batchSize}, gpuLayers=${config.sampling.gpuLayers}")

        if (loaded.get()) {
            Log.i(TAG, "Unloading previous model")
            unloadInternal()
        }

        // Run on llama thread for thread safety
        val startTime = System.currentTimeMillis()

        // Detect optimal backend based on GPU hardware
        val detectedBackend = gpuProfiler.detectOptimalBackend()
        val backendDescription = gpuProfiler.getBackendDescription()
        val gpuName = gpuProfiler.detectGpuName()
        Log.i(TAG, "GPU Backend: $backendDescription")
        Log.i(TAG, "GPU detected: $gpuName")

        // CRITICAL: Log whether GPU is actually being used
        if (detectedBackend == LlamaBackend.CPU) {
            Log.w(TAG, "=== WARNING: Running on CPU only! GPU acceleration is DISABLED ===")
            Log.w(TAG, "=== To enable GPU: change GpuProfiler to return Vulkan backend ===")
        } else {
            Log.i(TAG, "=== GPU acceleration ENABLED ===")
        }

            val future = llamaExecutor.submit<Boolean> {
                nativeLoadModel(
                    modelPath = config.modelPath,
                    mmprojPath = config.mmprojPath,
                    contextSize = config.sampling.contextWindow,
                    batchSize = config.sampling.batchSize,
                    gpuLayers = config.sampling.gpuLayers,
                    backendType = detectedBackend.value
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

    override suspend fun appendMessage(message: DomainChatMessage) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        val llamaMessage = ChatMessage(
            role = ChatRole.fromDomainRole(message.role),
            content = message.content
        )
        history += llamaMessage
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) = withContext(ioDispatcher) {
        check(loaded.get()) { "Engine not initialized" }
        history.clear()
        // Add system prompt first
        val systemPrompt = currentConfig?.systemPrompt ?: "You are a helpful assistant."
        history += ChatMessage(ChatRole.SYSTEM, systemPrompt)
        // Add all the provided messages, converting domain to inference format
        messages.forEach { domainMsg ->
            history += ChatMessage(
                role = ChatRole.fromDomainRole(domainMsg.role),
                content = domainMsg.content
            )
        }
    }

    override fun generate(): Flow<GenerationEvent> = callbackFlow {
        Log.i(TAG, "Starting generation...")
        check(loaded.get()) { "Engine not initialized" }
        check(generating.compareAndSet(false, true)) { "Generation already in progress" }

        val cfg = requireNotNull(currentConfig) { "Missing config" }
        val (roles, contents) = buildNativeMessages()
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

                // Track token counts for context management
                lastPromptTokens = promptTokens
                lastGeneratedTokens = generatedTokens

                // Check if context compression is needed and apply it
                checkAndCompressContext()

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
            // reasoningBudget > 0 = thinking enabled with token budget, 0 = disabled, -1 = unlimited
            val reasoningBudget = if (cfg.sampling.thinkingEnabled) 2048 else 0
            Log.i(TAG, "Starting completion with reasoning_budget=$reasoningBudget")

            // Use 0 penalties for thinking models to avoid interfering with extended reasoning
            val penaltyFreq = if (cfg.sampling.thinkingEnabled) 0.0f else 0.05f
            val penaltyPresent = if (cfg.sampling.thinkingEnabled) 0.0f else 0.05f
            Log.i(TAG, "Penalties: freq=$penaltyFreq, present=$penaltyPresent")

            // Use longer timeout for thinking models
            val timeoutSeconds = if (cfg.sampling.thinkingEnabled) {
                GENERATION_TIMEOUT_SECONDS_THINKING
            } else {
                GENERATION_TIMEOUT_SECONDS_REGULAR
            }
            Log.i(TAG, "Generation timeout: $timeoutSeconds seconds")

            // Submit to llama executor thread to ensure all llama operations run on the same thread
            val future = llamaExecutor.submit<Unit> {
                startCompletion(
                    roles = roles,
                    contents = contents,
                    imagePaths = emptyArray(),
                    temperature = cfg.sampling.temperature,
                    topK = cfg.sampling.topK,
                    topP = cfg.sampling.topP,
                    minP = cfg.sampling.minP,
                    maxTokens = cfg.sampling.maxTokens,
                    repeatPenalty = cfg.sampling.repeatPenalty,
                    penaltyFreq = penaltyFreq,
                    penaltyPresent = penaltyPresent,
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

    override fun generateWithOptions(options: GenerationOptions): Flow<GenerationEvent> = callbackFlow {
        Log.i(TAG, "Starting generation with options: reasoningBudget=${options.reasoningBudget}")
        check(loaded.get()) { "Engine not initialized" }
        check(generating.compareAndSet(false, true)) { "Generation already in progress" }

        val cfg = requireNotNull(currentConfig) { "Missing config" }
        val (roles, contents) = buildNativeMessages(options.systemPrompt, options.imageUris.size)

        // Derive parameters from options instead of config defaults
        val reasoningBudget = options.reasoningBudget
        val penaltyFreq = if (reasoningBudget > 0) 0.0f else 0.05f
        val penaltyPresent = if (reasoningBudget > 0) 0.0f else 0.05f
        val timeoutSeconds = if (reasoningBudget > 0) GENERATION_TIMEOUT_SECONDS_THINKING else GENERATION_TIMEOUT_SECONDS_REGULAR
        val temperature = options.temperature ?: cfg.sampling.temperature

        Log.i(TAG, "Generation with options: reasoningBudget=$reasoningBudget, penaltyFreq=$penaltyFreq, penaltyPresent=$penaltyPresent, timeout=$timeoutSeconds, temp=$temperature")

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
                lastPromptTokens = promptTokens
                lastGeneratedTokens = generatedTokens
                checkAndCompressContext()
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

        try {
            val future = llamaExecutor.submit<Unit> {
                startCompletion(
                    roles = roles,
                    contents = contents,
                    imagePaths = options.imageUris.toTypedArray(),
                    temperature = temperature,
                    topK = options.topK ?: cfg.sampling.topK,
                    topP = options.topP ?: cfg.sampling.topP,
                    minP = options.minP ?: cfg.sampling.minP,
                    maxTokens = options.maxTokens ?: cfg.sampling.maxTokens,
                    repeatPenalty = cfg.sampling.repeatPenalty,
                    penaltyFreq = penaltyFreq,
                    penaltyPresent = penaltyPresent,
                    reasoningBudget = reasoningBudget,
                    callback = callback
                )
            }
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            trySend(GenerationEvent.Error(e))
            generating.set(false)
            close(e)
        }

        awaitClose {
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
        delay(500)

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

    /**
     * Check if context needs compression and apply it if necessary.
     * Uses llama.cpp's position compression to reduce context window usage.
     * Prefer native KV usage, falling back to tracked prompt/generated tokens only if needed.
     */
    private fun checkAndCompressContext() {
        if (!loaded.get()) return

        try {
            val contextSize = getContextSizeForCompression()

            if (contextSize <= 0) {
                Log.w(TAG, "Cannot check context: size=$contextSize")
                return
            }

            val nativeUsage = getContextUsageForCompression()
            val fallbackUsage = lastPromptTokens + lastGeneratedTokens
            val totalTokensUsed = when {
                nativeUsage > 0 -> nativeUsage
                fallbackUsage > 0 -> fallbackUsage
                else -> 0
            }

            if (totalTokensUsed <= 0) {
                Log.w(TAG, "No token usage data available yet")
                return
            }

            val usageRatio = totalTokensUsed.toFloat() / contextSize.toFloat()
            Log.i(TAG, "Context usage: $totalTokensUsed / $contextSize tokens (${(usageRatio * 100).toInt()}%)")

            // Compress if approaching threshold
            if (usageRatio >= COMPRESSION_THRESHOLD_RATIO) {
                Log.i(TAG, "Context at ${(usageRatio * 100).toInt()}%, triggering compression (factor=$COMPRESSION_FACTOR)")

                // Save state before compression (for potential rollback)
                val stateBefore = saveState()

                val success = applyCompressionForContext(COMPRESSION_FACTOR)

                if (success) {
                    Log.i(TAG, "Context compression applied successfully")
                } else {
                    Log.w(TAG, "Compression failed, attempting to restore state")
                    // Try to restore state if compression failed
                    stateBefore?.let { restoreState(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/compressing context", e)
        }
    }

    /**
     * Compress context using llama.cpp position division.
     * This divides all positions by the given factor, effectively halving context usage.
     */
    private fun compressContext(factor: Int): Boolean {
        return try {
            // seqId = 0 means apply to all sequences
            nativeCompressContext(0, factor)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing context", e)
            false
        }
    }

    internal fun getContextSizeForCompression(): Int = nativeGetContextSize()

    internal fun getContextUsageForCompression(): Int = nativeGetContextUsage()

    internal fun applyCompressionForContext(factor: Int): Boolean = compressContext(factor)

    /**
     * Save the current llama state (KV cache + tokens) to a byte array.
     * This allows preserving context across operations.
     */
    fun saveState(): ByteArray? {
        return try {
            nativeGetState()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving state", e)
            null
        }
    }

    /**
     * Restore llama state from a previously saved byte array.
     */
    fun restoreState(state: ByteArray): Boolean {
        return try {
            nativeSetState(state)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring state", e)
            false
        }
    }

    /**
     * Get current context usage for monitoring.
     * Returns the number of tokens currently in the KV cache.
     */
    fun getContextUsage(): Int {
        return try {
            nativeGetContextUsage()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context usage", e)
            0
        }
    }

    /**
     * Get the maximum context size.
     */
    fun getContextSize(): Int {
        return try {
            nativeGetContextSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context size", e)
            0
        }
    }

    // Native method declarations
    private external fun nativeLoadModel(
        modelPath: String,
        mmprojPath: String?,
        contextSize: Int,
        batchSize: Int,
        gpuLayers: Int,
        backendType: Int
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
        callback: NativeTokenCallback
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
            callback
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
        penaltyFreq: Float,  // 0.0f to disable for thinking models
        penaltyPresent: Float,  // 0.0f to disable for thinking models
        reasoningBudget: Int,  // -1 = unlimited, 0 = disabled, >0 = enabled with budget
        callback: NativeTokenCallback
    )

    private fun buildNativeMessages(
        systemPromptOverride: String? = null,
        mediaMarkerCount: Int = 0,
    ): Pair<Array<String>, Array<String>> {
        val effectiveMessages = history.toMutableList()
        val effectiveSystemPrompt = systemPromptOverride?.takeIf(String::isNotBlank)

        if (effectiveSystemPrompt != null) {
            val systemIndex = effectiveMessages.indexOfFirst { it.role == ChatRole.SYSTEM }
            if (systemIndex >= 0) {
                effectiveMessages[systemIndex] = ChatMessage(ChatRole.SYSTEM, effectiveSystemPrompt)
            } else {
                effectiveMessages.add(0, ChatMessage(ChatRole.SYSTEM, effectiveSystemPrompt))
            }
        }

        if (mediaMarkerCount > 0) {
            val lastUserIndex = effectiveMessages.indexOfLast { it.role == ChatRole.USER }
            require(lastUserIndex >= 0) { "Cannot attach image input without a user message in history" }
            val mediaPrefix = buildString {
                repeat(mediaMarkerCount) {
                    append("<__media__>")
                }
            }
            val userMessage = effectiveMessages[lastUserIndex]
            if (!userMessage.content.contains("<__media__>")) {
                effectiveMessages[lastUserIndex] = userMessage.copy(content = mediaPrefix + userMessage.content)
            }
        }

        val nativeMessages = effectiveMessages.map { it.toNativeMessage() }
        return nativeMessages.map { it.first }.toTypedArray() to nativeMessages.map { it.second }.toTypedArray()
    }

    private fun ChatMessage.toNativeMessage(): Pair<String, String> {
        val roleStr = when (role) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
        }
        return roleStr to content
    }

    private external fun nativeStopCompletion()

    // Context management native functions
    private external fun nativeGetContextSize(): Int
    private external fun nativeGetContextUsage(): Int
    private external fun nativeCompressContext(seqId: Int, factor: Int): Boolean

    // State save/load native functions
    private external fun nativeGetStateSize(): Int
    private external fun nativeGetState(): ByteArray?
    private external fun nativeSetState(state: ByteArray): Boolean
}

/**
 * Callback interface for native token streaming.
 */
interface NativeTokenCallback {
    fun onToken(token: String)
    fun onComplete(promptTokens: Int, generatedTokens: Int)
    fun onError(message: String)
}
