package com.browntowndev.pocketcrew.feature.inference

import com.google.mediapipe.framework.image.MPImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener

/**
 * Interface representing a stateful LLM session to facilitate testing.
 */
interface LlmSessionPort : AutoCloseable {
    fun addQueryChunk(chunk: String)
    fun addImage(image: MPImage)
    fun generateResponseAsync(progressListener: ProgressListener<String>): ListenableFuture<String>
    override fun close()
}

/**
 * Implementation of [LlmSessionPort] that wraps MediaPipe's [LlmInferenceSession].
 */
class MediaPipeSessionWrapper(private val session: LlmInferenceSession) : LlmSessionPort {
    override fun addQueryChunk(chunk: String) {
        session.addQueryChunk(chunk)
    }

    override fun addImage(image: MPImage) {
        session.addImage(image)
    }

    override fun generateResponseAsync(progressListener: ProgressListener<String>): ListenableFuture<String> {
        return session.generateResponseAsync(progressListener)
    }

    override fun close() {
        session.close()
    }
}

/**
 * Wrapper around MediaPipe's LlmInference to facilitate testing.
 * The SDK's LlmInference class has a static initializer that loads native libraries,
 * which makes it extremely difficult to mock in standard unit tests.
 */
class LlmInferenceWrapper constructor(private val llmInference: LlmInference) {
    fun createSession(options: LlmInferenceSession.LlmInferenceSessionOptions): LlmSessionPort {
        val session = LlmInferenceSession.createFromOptions(llmInference, options)
        return MediaPipeSessionWrapper(session)
    }

    fun close() {
        llmInference.close()
    }
}
