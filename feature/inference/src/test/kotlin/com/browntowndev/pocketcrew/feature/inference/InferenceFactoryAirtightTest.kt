package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class InferenceFactoryAirtightTest {

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockProcessThinkingTokens: ProcessThinkingTokensUseCase
    private lateinit var mockLlamaChatSessionManager: LlamaChatSessionManager
    private lateinit var mockLoggingPort: LoggingPort
    private lateinit var mockMediaPipeFactory: MediaPipeInferenceServiceFactory

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockProcessThinkingTokens = mockk(relaxed = true)
        mockLlamaChatSessionManager = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)
        mockMediaPipeFactory = mockk(relaxed = true)

        every { mockContext.getExternalFilesDir(null) } returns File("/mock")

        factory = InferenceFactoryImpl(
            context = mockContext,
            conversationManagerProvider = { mockk<ConversationManagerPort>(relaxed = true) },
            modelRegistry = mockModelRegistry,
            processThinkingTokens = mockProcessThinkingTokens,
            llamaChatSessionManagerProvider = { mockLlamaChatSessionManager },
            loggingPort = mockLoggingPort,
            mediaPipeFactory = mockMediaPipeFactory
        )
    }

    private fun createAsset(sha: String, extension: String): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/model",
                remoteFileName = "model.$extension",
                localFileName = "model.$extension",
                sha256 = sha,
                sizeInBytes = 1000L,
                modelFileFormat = if (extension == "gguf") ModelFileFormat.GGUF else ModelFileFormat.TASK
            ),
            configurations = emptyList()
        )
    }

    @Test
    fun `identifies two different ModelTypes sharing same SHA as same instance`() = runTest {
        val sharedSha = "abc123"
        val asset = createAsset(sharedSha, "gguf")

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset

        val service1 = factory.getInferenceService(ModelType.FAST)
        val service2 = factory.getInferenceService(ModelType.THINKING)

        assertSame(service1, service2, "Services sharing the same SHA-256 and extension MUST be the same instance")
    }

    @Test
    fun `identifies same SHA but different extension as different instance`() = runTest {
        val sharedSha = "abc123"
        val ggufAsset = createAsset(sharedSha, "gguf")
        val taskAsset = createAsset(sharedSha, "task")

        // Mock MediaPipe factory to return a mock service
        val mockMediaPipeService = mockk<MediaPipeInferenceServiceImpl>(relaxed = true)
        every { mockMediaPipeFactory.create(any()) } returns mockMediaPipeService

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns taskAsset

        val service1 = factory.getInferenceService(ModelType.FAST)
        val service2 = factory.getInferenceService(ModelType.THINKING)

        assertNotSame(service1, service2, "Services with different extensions MUST be different instances even if SHA is same")
    }

    @Test
    fun `reference count prevents premature engine shutdown`() = runTest {
        val sharedSha = "abc123"
        val asset = createAsset(sharedSha, "gguf")

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset

        val service1 = factory.getInferenceService(ModelType.FAST)
        val service2 = factory.getInferenceService(ModelType.THINKING)

        factory.registerUsage(service1)
        factory.registerUsage(service2)

        // Release FAST - should still be alive because THINKING is using it
        factory.releaseUsage(service1)
        
        // Verifying it is still in cache by requesting again - should be same instance
        val service3 = factory.getInferenceService(ModelType.FAST)
        assertSame(service1, service3)
    }

    @Test
    fun `reference count reaching zero keeps idle service cached`() = runTest {
        val sharedSha = "abc123"
        val asset = createAsset(sharedSha, "gguf")

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset

        val service1 = factory.getInferenceService(ModelType.FAST)
        factory.registerUsage(service1)
        
        // Release last reference
        factory.releaseUsage(service1)

        // Request again - should be the SAME instance because idle services remain cached
        val service2 = factory.getInferenceService(ModelType.FAST)
        assertSame(service1, service2)
    }

    @Test
    fun `registerUsage handles missing service mapping gracefully`() = runTest {
        // Need a service instance to call these. Since they should handle missing mapping gracefully:
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        factory.registerUsage(mockService)
        factory.releaseUsage(mockService)
    }

    @Test
    fun `concurrency handle identical SHA requests without racing`() = runTest {
        val sharedSha = "abc123"
        val asset = createAsset(sharedSha, "gguf")
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns asset

        val services = (1..10).map {
            async {
                factory.getInferenceService(ModelType.FAST)
            }
        }.awaitAll()

        val first = services.first()
        services.forEach { 
            assertSame(first, it, "All concurrent requests for same identity should return same instance")
        }
    }
}
