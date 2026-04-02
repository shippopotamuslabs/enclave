package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests that InferenceFactoryImpl correctly resolves services dynamically based on file extensions.
 */
class InferenceFactoryImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockProcessThinkingTokens: ProcessThinkingTokensUseCase
    private lateinit var mockLlamaChatSessionManager: LlamaChatSessionManager
    private lateinit var mockLoggingPort: LoggingPort
    private lateinit var mockConversationManagerProvider: (ModelType) -> ConversationManagerPort
    private lateinit var mockMediaPipeFactory: MediaPipeInferenceServiceFactory

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockProcessThinkingTokens = mockk(relaxed = true)
        mockLlamaChatSessionManager = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)
        val mockConversationManager = mockk<ConversationManagerPort>(relaxed = true)
        mockConversationManagerProvider = { mockConversationManager }
        mockMediaPipeFactory = mockk(relaxed = true)

        // Mock java.io.File to avoid filesystem access errors
        every { mockContext.getExternalFilesDir(null) } returns File("/mock")

        // Mock the MediaPipe factory so we don't hit UnsatisfiedLinkError
        val mockMediaPipeService = mockk<MediaPipeInferenceServiceImpl>(relaxed = true)
        every { mockMediaPipeFactory.create(any(), any()) } returns mockMediaPipeService

        factory = InferenceFactoryImpl(
            context = mockContext,
            conversationManagerProvider = mockConversationManagerProvider,
            modelRegistry = mockModelRegistry,
            processThinkingTokens = mockProcessThinkingTokens,
            llamaChatSessionManager = mockLlamaChatSessionManager,
            loggingPort = mockLoggingPort,
            mediaPipeFactory = mockMediaPipeFactory
        )
    }

    @Test
    fun `resolves LlamaInferenceServiceImpl for gguf files`() = runTest {
        val ggufAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/llama",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "hash",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset

        val service = factory.getInferenceService(ModelType.FAST)
        assertTrue(service is LlamaInferenceServiceImpl)
    }

    @Test
    fun `resolves MediaPipeInferenceServiceImpl for task files`() = runTest {
        val taskAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 2L,
                huggingFaceModelName = "test/litert",
                remoteFileName = "model.task",
                localFileName = "model.task",
                sha256 = "hash2",
                sizeInBytes = 2000L,
                modelFileFormat = ModelFileFormat.TASK
            ),
            configurations = emptyList()
        )
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns taskAsset

        val service = factory.getInferenceService(ModelType.FAST)
        assertTrue(service is MediaPipeInferenceServiceImpl || service.javaClass.simpleName.contains("Subclass")) // MockK proxies
    }

    @Test
    fun `dynamically switches service implementation when model assignment changes`() = runTest {
        val ggufAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/llama",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "hash",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        val taskAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 2L,
                huggingFaceModelName = "test/litert",
                remoteFileName = "model.task",
                localFileName = "model.task",
                sha256 = "hash2",
                sizeInBytes = 2000L,
                modelFileFormat = ModelFileFormat.TASK
            ),
            configurations = emptyList()
        )

        // Initial assignment is GGUF
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset
        val firstService = factory.getInferenceService(ModelType.FAST)
        assertTrue(firstService is LlamaInferenceServiceImpl)

        // User changes assignment to .task at runtime
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns taskAsset
        val secondService = factory.getInferenceService(ModelType.FAST)
        assertTrue(secondService is MediaPipeInferenceServiceImpl || secondService.javaClass.simpleName.contains("Subclass"))
    }

    @Test
    fun `forces service recreation when same-extension model changes SHA`() = runTest {
        val oldAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/llama-old",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "old-sha",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        val newAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 2L,
                huggingFaceModelName = "test/llama-new",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "new-sha",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        // Initial assignment is old SHA
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns oldAsset
        val firstService = factory.getInferenceService(ModelType.FAST)
        assertTrue(firstService is LlamaInferenceServiceImpl)

        // Model file changes to new SHA (but same extension .gguf)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns newAsset
        val secondService = factory.getInferenceService(ModelType.FAST)
        
        // They should be DIFFERENT instances because SHA changed
        assertTrue(firstService !== secondService)
    }
}
