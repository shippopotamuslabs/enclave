package com.browntowndev.pocketcrew.whisper

import androidx.annotation.Keep
import com.browntowndev.pocketcrew.domain.port.inference.WhisperInferencePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class JniWhisperInference internal constructor(
    private val nativeBridge: WhisperNativeBridge,
    private val processorCountProvider: ProcessorCountProvider,
    private val dispatcher: CoroutineDispatcher,
) : WhisperInferencePort {
    constructor() : this(
        nativeBridge = JniWhisperNativeBridge,
        processorCountProvider = RuntimeProcessorCountProvider,
        dispatcher = Dispatchers.Default,
    )

    private val mutex = Mutex()
    private var contextPtr: Long = 0L

    override suspend fun initialize(modelPath: String) {
        mutex.withLock {
            if (contextPtr != 0L) {
                closeLocked()
            }

            contextPtr = withContext(dispatcher) {
                nativeBridge.initContext(modelPath)
            }
            check(contextPtr != 0L) { "Failed to initialize Whisper model: $modelPath" }
        }
    }

    override suspend fun transcribe(samples: FloatArray): String {
        return mutex.withLock {
            val ptr = contextPtr
            check(ptr != 0L) { "Whisper context is not initialized." }

            withContext(dispatcher) {
                nativeBridge.processAudio(
                    contextPtr = ptr,
                    samples = samples,
                    numThreads = resolveThreadCount(),
                )
            }
        }
    }

    override suspend fun close() {
        mutex.withLock {
            closeLocked()
        }
    }

    private suspend fun closeLocked() {
        val ptr = contextPtr
        if (ptr == 0L) return

        contextPtr = 0L
        withContext(dispatcher) {
            nativeBridge.freeContext(ptr)
        }
    }

    private fun resolveThreadCount(): Int {
        val halfProcessors = processorCountProvider.availableProcessors() / 2
        return halfProcessors.coerceAtLeast(1).coerceAtMost(MAX_THREADS)
    }

    internal companion object {
        const val MAX_THREADS = 4

        @Keep
        @JvmStatic
        external fun initContext(modelPath: String): Long

        @Keep
        @JvmStatic
        external fun processAudio(
            contextPtr: Long,
            samples: FloatArray,
            numThreads: Int,
        ): String

        @Keep
        @JvmStatic
        external fun freeContext(contextPtr: Long)
    }
}

internal interface WhisperNativeBridge {
    fun initContext(modelPath: String): Long
    fun processAudio(contextPtr: Long, samples: FloatArray, numThreads: Int): String
    fun freeContext(contextPtr: Long)
}

internal object JniWhisperNativeBridge : WhisperNativeBridge {
    init {
        System.loadLibrary("whisper-jni")
    }

    override fun initContext(modelPath: String): Long = JniWhisperInference.initContext(modelPath)

    override fun processAudio(contextPtr: Long, samples: FloatArray, numThreads: Int): String {
        return JniWhisperInference.processAudio(
            contextPtr = contextPtr,
            samples = samples,
            numThreads = numThreads,
        )
    }

    override fun freeContext(contextPtr: Long) {
        JniWhisperInference.freeContext(contextPtr)
    }
}

internal fun interface ProcessorCountProvider {
    fun availableProcessors(): Int
}

internal object RuntimeProcessorCountProvider : ProcessorCountProvider {
    override fun availableProcessors(): Int = Runtime.getRuntime().availableProcessors()
}
