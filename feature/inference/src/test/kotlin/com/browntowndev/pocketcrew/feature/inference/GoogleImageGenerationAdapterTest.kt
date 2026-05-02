package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleImageGenerationAdapter
import com.google.genai.Client
import com.google.genai.interactions.models.interactions.CreateModelInteractionParams
import com.google.genai.interactions.models.interactions.Interaction
import com.google.genai.interactions.models.interactions.Content as InteractionContent
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleImageGenerationAdapterTest {
    private val clientProvider = mockk<GoogleGenAiClientProviderPort>()

    @Test
    fun testMockkMatch() {
        val mock = mockk<GoogleGenAiClientProviderPort>()
        val client = mockk<Client>()
        every { mock.getClient(any(), any(), any(), any()) } returns client
        
        assertNotNull(mock.getClient("key", null, emptyMap(), "v1alpha"))
    }

    @Test
    fun generateImage_success_callsInteractionsCreate() = runTest {
        val requestSlot = slot<CreateModelInteractionParams>()
        val mockProvider = mockk<GoogleGenAiClientProviderPort>()
        val mockClient = spyk(Client.builder().apiKey("key").build())
        val adapter = object : GoogleImageGenerationAdapter(mockProvider) {
            override fun createInteraction(
                client: Client,
                params: CreateModelInteractionParams
            ): Interaction {
                requestSlot.captured = params
                val image = mockk<com.google.genai.types.Image>()
                every { image.imageBytes() } returns java.util.Optional.of("modern".toByteArray())
                
                val content = mockk<InteractionContent>(relaxed = true)
                // Interactions.Content.image() returns Optional<Image>
                val imageMethod = content.javaClass.methods.find { it.name == "image" && it.parameterCount == 0 }
                every { imageMethod?.invoke(content) } answers { java.util.Optional.of(image) }
                
                val interaction = mockk<Interaction>(relaxed = true)
                val outputsMethod = interaction.javaClass.methods.find { it.name == "outputs" && it.parameterCount == 0 }
                every { outputsMethod?.invoke(interaction) } answers { java.util.Optional.of(listOf(content)) }
                
                return interaction
            }
        }
        
        every { mockProvider.getClient(any(), any(), any(), any()) } returns mockClient
        
        // Mock the services inside Client
        val mockModels = mockk<com.google.genai.Models>(relaxed = true)
        val mockInteractions = mockk<com.google.genai.interactions.services.blocking.InteractionService>(relaxed = true)
        
        val modelsField = mockClient.javaClass.getDeclaredField("models")
        modelsField.isAccessible = true
        modelsField.set(mockClient, mockModels)
        
        val interactionsField = mockClient.javaClass.getDeclaredField("interactions")
        interactionsField.isAccessible = true
        interactionsField.set(mockClient, mockInteractions)

        val result = adapter.generateImage(
            prompt = "modern prompt",
            apiKey = "key",
            modelId = "gemini-3.1-flash-image-preview",
            baseUrl = null,
            settings = ImageGenerationSettings(),
        )

        assertTrue(result.isSuccess)
        assertEquals("modern", String(result.getOrThrow()[0]))
    }
}
