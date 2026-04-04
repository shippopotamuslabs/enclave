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
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.File

class InferenceFactoryImplTest {

    private val mockContext = mockk<Context>(relaxed = true).also {
        every { it.getExternalFilesDir(null) } returns File("/mock")
    }
    private val mockModelRegistry = mockk<ModelRegistryPort>(relaxed = true)
    private val mockProcessThinkingTokens = mockk<ProcessThinkingTokensUseCase>(relaxed = true)
    private val mockLlamaChatSessionManager = mockk<LlamaChatSessionManager>(relaxed = true)
    private val mockLoggingPort = mockk<LoggingPort>(relaxed = true)
    private val mockConversationManager = mockk<ConversationManagerPort>(relaxed = true)
    private val mockMediaPipeFactory = mockk<MediaPipeInferenceServiceFactory>(relaxed = true)

    private fun createFactory(): InferenceFactoryImpl {
        return InferenceFactoryImpl(
            context = mockContext,
            conversationManagerProvider = { mockConversationManager },
            modelRegistry = mockModelRegistry,
            processThinkingTokens = mockProcessThinkingTokens,
            llamaChatSessionManagerProvider = { mockLlamaChatSessionManager },
            loggingPort = mockLoggingPort,
            mediaPipeFactory = mockMediaPipeFactory,
            inferenceLockManager = InferenceLockManagerImpl()
        )
    }

    private fun asset(
        sha: String,
        localFileName: String,
        format: ModelFileFormat,
        id: Long = 1L
    ) = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = id,
            huggingFaceModelName = "test/model",
            remoteFileName = localFileName,
            localFileName = localFileName,
            sha256 = sha,
            sizeInBytes = 1000L,
            modelFileFormat = format
        ),
        configurations = emptyList()
    )

    @Test
    fun `resolves llama service for gguf files`() = runTest {
        val factory = createFactory()
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset("sha-1", "model.gguf", ModelFileFormat.GGUF)

        val service = factory.withInferenceService(ModelType.FAST) { it }

        assertInstanceOf(LlamaInferenceServiceImpl::class.java, service)
    }

    @Test
    fun `resolves mediapipe service for task files`() = runTest {
        val factory = createFactory()
        val mediaPipeService = mockk<LlmInferencePort>(relaxed = true)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset("sha-1", "model.task", ModelFileFormat.TASK)
        every { mockMediaPipeFactory.create(any()) } returns mediaPipeService

        val service = factory.withInferenceService(ModelType.FAST) { it }

        assertSame(mediaPipeService, service)
    }

    @Test
    fun `same model identity reuses loaded engine`() = runTest {
        val factory = createFactory()
        val mediaPipeService = mockk<LlmInferencePort>(relaxed = true)
        val sharedAsset = asset("sha-1", "model.task", ModelFileFormat.TASK)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns sharedAsset
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns sharedAsset
        every { mockMediaPipeFactory.create(any()) } returns mediaPipeService

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertSame(first, second)
    }

    @Test
    fun `different model identity closes old engine and loads replacement`() = runTest {
        val factory = createFactory()
        val firstService = mockk<LlmInferencePort>(relaxed = true)
        val secondService = mockk<LlmInferencePort>(relaxed = true)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset("sha-1", "model.task", ModelFileFormat.TASK)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset("sha-2", "model.task", ModelFileFormat.TASK, id = 2L)
        every { mockMediaPipeFactory.create(any()) } returnsMany listOf(firstService, secondService)

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(first, second)
        coVerify(exactly = 1) { firstService.closeSession() }
    }
}
