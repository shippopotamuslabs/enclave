package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationResponse
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [LiteRtInferenceServiceImpl.sendPrompt] with `GenerationOptions` overload.
 */
class LiteRtInferenceServiceImplGenerationOptionsTest {

    private lateinit var mockConversationManager: ConversationManagerPort
    private lateinit var mockConversation: ConversationPort
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        mockConversationManager = mockk(relaxed = true)
        mockConversation = mockk(relaxed = true)
        processThinkingTokens = ProcessThinkingTokensUseCase()

        coEvery { mockConversationManager.getConversation(any(), any(), any()) } returns mockConversation
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `sendPrompt with GenerationOptions accepts call`() = runTest {
        val responses = flowOf(ConversationResponse(text = "response"))
        every { mockConversation.sendMessageAsync(any(), any()) } returns responses

        val service = LiteRtInferenceServiceImpl(mockConversationManager, processThinkingTokens, mockk(relaxed = true), ModelType.FAST)
        val options = GenerationOptions(reasoningBudget = 2048)
        // Actually collect the flow to exercise the implementation
        val events = service.sendPrompt("hello", options, closeConversation = false).toList()
        assertTrue(events.isNotEmpty(), "Flow must emit events, not throw NotImplementedError")
    }

    @Test
    fun `sendPrompt with image uris forwards multimodal options to conversation`() = runTest {
        val options = GenerationOptions(
            reasoningBudget = 0,
            imageUris = listOf("file:///tmp/test-image.jpg"),
        )
        val optionSlot = slot<GenerationOptions>()
        val responses = flowOf(ConversationResponse(text = "response"))
        every { mockConversation.sendMessageAsync(any(), capture(optionSlot)) } returns responses

        val service = LiteRtInferenceServiceImpl(mockConversationManager, processThinkingTokens, mockk(relaxed = true), ModelType.VISION)
        service.sendPrompt("describe", options, closeConversation = false).toList()

        assertEquals(listOf("file:///tmp/test-image.jpg"), optionSlot.captured.imageUris)
    }
}
