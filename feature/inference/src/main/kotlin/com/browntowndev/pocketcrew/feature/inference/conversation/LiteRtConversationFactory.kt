package com.browntowndev.pocketcrew.feature.inference.conversation

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.CancellationException as JavaCancellationException

internal typealias LiteRtEngineCreator = (
    modelPath: String,
    backend: Backend,
    visionBackend: Backend?,
    contextWindow: Int,
    cacheDir: String?,
) -> Engine

internal class LiteRtConversationFactory(
    internal val context: Context,
    private val cache: ConversationSignatureCache,
    private val engineCreator: LiteRtEngineCreator,
) {
    fun ensureEngineInitialized(
        modelPath: String,
        contextWindow: Int,
        isMultimodal: Boolean,
    ) {
        if (cache.engine != null) return
        val cacheDir = cacheDirFor(modelPath)
        initializeEngine(modelPath, contextWindow, cacheDir, isMultimodal)
    }

    @OptIn(ExperimentalApi::class)
    fun initializeEngine(
        modelPath: String,
        contextWindow: Int,
        cacheDir: String?,
        isMultimodal: Boolean,
    ) {
        val backend = Backend.GPU()
        val visionBackend = if (isMultimodal) Backend.GPU() else null
        var candidate: Engine? = null

        try {
            val speculativeDecodingEnabled = supportsSpeculativeDecoding(modelPath)
            ExperimentalFlags.enableSpeculativeDecoding = speculativeDecodingEnabled
            Log.d(
                TAG,
                "Initializing LiteRT text engine with GPU backend, contextWindow=$contextWindow " +
                    "speculativeDecodingEnabled=$speculativeDecodingEnabled memoryBeforeInit=${MemoryDiagnostics.snapshot()}",
            )

            candidate =
                engineCreator(
                    modelPath,
                    backend,
                    visionBackend,
                    contextWindow,
                    cacheDir,
                )

            candidate.initialize()

            cache.engine = candidate
            cache.activeBackendIsNpu = false

            Log.d(
                TAG,
                "LiteRT text engine initialized successfully with GPU backend " +
                    "(isNpu=${cache.activeBackendIsNpu}, speculativeDecodingEnabled=$speculativeDecodingEnabled) " +
                    "memoryAfterInit=${MemoryDiagnostics.snapshot()}",
            )
        } catch (e: CancellationException) {
            abortEngineInitialization(candidate, e)
        } catch (e: JavaCancellationException) {
            abortEngineInitialization(candidate, e)
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT GPU backend failed during initialize() memoryAfterFailure=${MemoryDiagnostics.snapshot()}", t)
            closeCandidate(candidate, "error cleanup")
            throw IllegalStateException("Failed to initialize LiteRT text engine with GPU backend", t)
        } finally {
            ExperimentalFlags.enableSpeculativeDecoding = false
        }
    }

    private fun abortEngineInitialization(
        candidate: Engine?,
        cause: Throwable,
    ): Nothing {
        Log.i(TAG, "LiteRT GPU engine initialization cancelled by user")
        closeCandidate(candidate, "cancellation cleanup")
        throw cause
    }

    private fun closeCandidate(
        candidate: Engine?,
        reason: String,
    ) {
        try {
            candidate?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close engine during $reason", t)
        }
    }

    fun supportsSpeculativeDecoding(modelPath: String): Boolean {
        var capabilities: Capabilities? = null
        return try {
            capabilities = Capabilities(modelPath)
            capabilities.hasSpeculativeDecodingSupport()
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to read LiteRT speculative decoding capabilities for $modelPath", t)
            false
        } finally {
            try {
                capabilities?.close()
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to close LiteRT capabilities for $modelPath", t)
            }
        }
    }

    fun closeConversation() {
        Log.d(TAG, "Closing LiteRT conversation memoryBeforeClose=${MemoryDiagnostics.snapshot()}")
        cache.conversation?.close()
        cache.conversation = null
        cache.conversationPort = null
        cache.currentSignature = null
        cache.clearTransientToolResults()
        Log.d(TAG, "Closed LiteRT conversation memoryAfterClose=${MemoryDiagnostics.snapshot()}")
    }

    fun closeEngine() {
        Log.d(TAG, "Closing LiteRT engine memoryBeforeClose=${MemoryDiagnostics.snapshot()}")
        closeConversation()
        cache.engine?.close()
        cache.engine = null
        cache.resetAfterEngineClose()
        Log.d(TAG, "Closed LiteRT engine memoryAfterClose=${MemoryDiagnostics.snapshot()}")
    }

    private fun cacheDirFor(modelPath: String): String? =
        if (modelPath.startsWith("/data/local/tmp")) {
            context.getExternalFilesDir(null)?.absolutePath
        } else {
            null
        }

    companion object {
        private const val TAG = "ConversationManager"
    }
}

internal fun resolveModelPath(
    context: Context,
    filename: String,
): String {
    val modelsDir = File(context.getExternalFilesDir(null), "models")
    return File(modelsDir, filename).absolutePath
}
