package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.google.common.util.concurrent.SettableFuture
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaPipeInferenceServiceImplGenerationOptionsTest {

    private lateinit var mockLlmInference: LlmInferenceWrapper
    private lateinit var mockSession: LlmSessionPort

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockLlmInference = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        val future = SettableFuture.create<String>()
        every { mockSession.generateResponseAsync(any<ProgressListener<String>>()) } returns future
        every { mockLlmInference.createSession(any()) } returns mockSession
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `sendPrompt with GenerationOptions accepts call without crashing`() = runTest {
        every { mockSession.addQueryChunk(any<String>()) } returns Unit

        val service = MediaPipeInferenceServiceImpl(mockLlmInference)
        val options = GenerationOptions(reasoningBudget = 2048, temperature = 0.9f)
        val result = service.sendPrompt("hello", options, closeConversation = false)
        assertNotNull(result)
    }

    @Test
    fun `sendPrompt without options uses default behavior`() = runTest {
        every { mockSession.addQueryChunk(any<String>()) } returns Unit

        val service = MediaPipeInferenceServiceImpl(mockLlmInference)
        val result = service.sendPrompt("hello", closeConversation = false)
        assertNotNull(result)
    }
}
