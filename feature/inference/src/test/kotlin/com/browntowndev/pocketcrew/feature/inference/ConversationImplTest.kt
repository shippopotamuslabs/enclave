package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.ConversationResponse
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConversationImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockLiteRtConversation: Conversation
    private lateinit var conversationImpl: ConversationImpl

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        mockContext = mockk(relaxed = true)
        mockLiteRtConversation = mockk(relaxed = true)
        conversationImpl = ConversationImpl(mockContext, mockLiteRtConversation)
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        io.mockk.unmockkStatic(Log::class)
    }

    @Test
    fun `sendMessageAsync passes enable_thinking true when reasoningBudget is positive`() = runTest {
        // Given
        val options = GenerationOptions(reasoningBudget = 1024)
        val mockMessage = mockk<Message>(relaxed = true)
        val contextSlot = slot<Map<String, Any>>()

        every {
            mockLiteRtConversation.sendMessageAsync(any<Message>(), capture(contextSlot))
        } returns flowOf(mockMessage)

        // When
        conversationImpl.sendMessageAsync("Hello", options).toList()

        // Then
        assertEquals("true", contextSlot.captured["enable_thinking"])
    }

    @Test
    fun `sendMessageAsync passes empty map when reasoningBudget is zero or null`() = runTest {
        // Given
        val options = GenerationOptions(reasoningBudget = 0)
        val mockMessage = mockk<Message>(relaxed = true)
        val contextSlot = slot<Map<String, Any>>()

        every {
            mockLiteRtConversation.sendMessageAsync(any<Message>(), capture(contextSlot))
        } returns flowOf(mockMessage)

        // When
        conversationImpl.sendMessageAsync("Hello", options).toList()

        // Then
        assertTrue(contextSlot.captured.isEmpty())
    }

    @Test
    fun `sendMessageAsync passes empty map when options is null`() = runTest {
        // Given — null options means no GenerationOptions provided at all
        val mockMessage = mockk<Message>(relaxed = true)
        val contextSlot = slot<Map<String, Any>>()
        
        every { 
            mockLiteRtConversation.sendMessageAsync(any<Message>(), capture(contextSlot)) 
        } returns flowOf(mockMessage)

        // When
        conversationImpl.sendMessageAsync("Hello", null).toList()

        // Then — no enable_thinking key should be present
        assertTrue(contextSlot.captured.isEmpty())
    }

    @Test
    fun `sendMessageAsync correctly maps text and thought channels`() = runTest {
        // Given
        val mockLiteRtMessage = mockk<Message>()
        val mockContents = mockk<Contents>()
        val textContent = Content.Text("Hello world")
        
        every { mockLiteRtMessage.contents } returns mockContents
        every { mockContents.contents } returns listOf(textContent)
        every { mockLiteRtMessage.channels } returns mapOf("thought" to "I am thinking")
        
        every { 
            mockLiteRtConversation.sendMessageAsync(any<Message>(), any<Map<String, Any>>()) 
        } returns flowOf(mockLiteRtMessage)

        // When
        val results: List<ConversationResponse> = conversationImpl.sendMessageAsync("Hello", null).toList()

        // Then
        assertEquals(1, results.size)
        assertEquals("Hello world", results[0].text)
        assertEquals("I am thinking", results[0].thought)
    }

    @Test
    fun `sendMessageAsync handles empty thought channel`() = runTest {
        // Given
        val mockLiteRtMessage = mockk<Message>()
        val mockContents = mockk<Contents>()
        val textContent = Content.Text("Response")
        
        every { mockLiteRtMessage.contents } returns mockContents
        every { mockContents.contents } returns listOf(textContent)
        every { mockLiteRtMessage.channels } returns emptyMap()
        
        every { 
            mockLiteRtConversation.sendMessageAsync(any<Message>(), any<Map<String, Any>>()) 
        } returns flowOf(mockLiteRtMessage)

        // When
        val results: List<ConversationResponse> = conversationImpl.sendMessageAsync("Hello", null).toList()

        // Then
        assertEquals(1, results.size)
        assertEquals("Response", results[0].text)
        assertEquals("", results[0].thought)
    }

    @Test
    fun `cancelProcess delegates to LiteRT conversation`() {
        // When
        conversationImpl.cancelProcess()

        // Then
        verify { mockLiteRtConversation.cancelProcess() }
    }

}
