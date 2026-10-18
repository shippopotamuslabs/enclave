package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [LiteRtInferenceServiceImpl.sendPrompt] with `GenerationOptions` overload.
 */
class LiteRtInferenceServiceImplGenerationOptionsTest {
    private lateinit var conversationManager: FakeConversationManager
    private lateinit var conversation: FakeLiteRtConversation
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        conversation = FakeLiteRtConversation()
        conversationManager =
            FakeConversationManager().also { it.conversation = conversation }
        processThinkingTokens = ProcessThinkingTokensUseCase()
    }

    @Test
    fun `sendPrompt with GenerationOptions accepts call`() =
        runTest {
            conversation.responseFlow = flowOf(ConversationResponse(text = "response"))

            val service = LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, FakeToolExecutionEventPort(), ModelType.FAST)
            val options = GenerationOptions(reasoningBudget = 2048)
            // Actually collect the flow to exercise the implementation
            val events = service.sendPrompt("hello", options, closeConversation = false).toList()
            assertTrue(events.isNotEmpty(), "Flow must emit events, not throw NotImplementedError")
        }

    @Test
    fun `sendPrompt with image uris forwards multimodal options to conversation`() =
        runTest {
            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    imageUris = listOf("file:///tmp/test-image.jpg"),
                )
            conversation.responseFlow = flowOf(ConversationResponse(text = "response"))

            val service =
                LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, FakeToolExecutionEventPort(), ModelType.VISION)
            service.sendPrompt("describe", options, closeConversation = false).toList()

            assertEquals(listOf("file:///tmp/test-image.jpg"), conversation.sendMessageCalls.single().second?.imageUris)
        }
}
