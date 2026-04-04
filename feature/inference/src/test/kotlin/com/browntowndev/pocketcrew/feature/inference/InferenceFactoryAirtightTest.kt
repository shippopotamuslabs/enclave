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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.File

class InferenceFactoryAirtightTest {

    private val mockContext = mockk<Context>(relaxed = true).also {
        every { it.getExternalFilesDir(null) } returns File("/mock")
    }
    private val mockModelRegistry = mockk<ModelRegistryPort>(relaxed = true)
    private val mockProcessThinkingTokens = mockk<ProcessThinkingTokensUseCase>(relaxed = true)
    private val mockLlamaChatSessionManager = mockk<LlamaChatSessionManager>(relaxed = true)
    private val mockLoggingPort = mockk<LoggingPort>(relaxed = true)
    private val mockMediaPipeFactory = mockk<MediaPipeInferenceServiceFactory>(relaxed = true)

    private fun createFactory(): InferenceFactoryImpl {
        return InferenceFactoryImpl(
            context = mockContext,
            conversationManagerProvider = { mockk<ConversationManagerPort>(relaxed = true) },
            modelRegistry = mockModelRegistry,
            processThinkingTokens = mockProcessThinkingTokens,
            llamaChatSessionManagerProvider = { mockLlamaChatSessionManager },
            loggingPort = mockLoggingPort,
            mediaPipeFactory = mockMediaPipeFactory,
            inferenceLockManager = InferenceLockManagerImpl()
        )
    }

    private fun taskAsset(sha: String): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/model",
                remoteFileName = "model.task",
                localFileName = "model.task",
                sha256 = sha,
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.TASK
            ),
            configurations = emptyList()
        )
    }

    @Test
    fun `repeated identical requests only create one engine instance`() = runTest {
        val factory = createFactory()
        val service = mockk<LlmInferencePort>(relaxed = true)
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns taskAsset("shared-sha")
        every { mockMediaPipeFactory.create(any()) } returns service

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.FAST) { it }
        val third = factory.withInferenceService(ModelType.FAST) { it }

        assertSame(service, first)
        assertSame(first, second)
        assertSame(second, third)
        verify(exactly = 1) { mockMediaPipeFactory.create(any()) }
        coVerify(exactly = 0) { service.closeSession() }
    }

    @Test
    fun `idle engine remains loaded for reuse until a different file is requested`() = runTest {
        val factory = createFactory()
        val firstService = mockk<LlmInferencePort>(relaxed = true)
        val secondService = mockk<LlmInferencePort>(relaxed = true)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns taskAsset("sha-one")
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns taskAsset("sha-two")
        every { mockMediaPipeFactory.create(any()) } returnsMany listOf(firstService, secondService)

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val reused = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertSame(firstService, first)
        assertSame(first, reused)
        assertSame(secondService, second)
        coVerify(exactly = 1) { firstService.closeSession() }
    }
}
