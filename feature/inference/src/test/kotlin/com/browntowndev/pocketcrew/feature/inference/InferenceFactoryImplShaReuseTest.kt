package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFailsWith

class InferenceFactoryImplShaReuseTest {

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

    private fun ggufAsset(sha: String) = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = 1L,
            huggingFaceModelName = "test/llama",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = sha,
            sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = emptyList()
    )

    @Test
    fun `all roles sharing same file reuse one loaded engine`() = runTest {
        val factory = createFactory()
        val asset = ggufAsset("shared-sha")
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns asset

        val fast = factory.withInferenceService(ModelType.FAST) { it }
        val thinking = factory.withInferenceService(ModelType.THINKING) { it }
        val draft = factory.withInferenceService(ModelType.DRAFT_ONE) { it }

        assertSame(fast, thinking)
        assertSame(thinking, draft)
    }

    @Test
    fun `missing asset returns no-op service`() = runTest {
        val factory = createFactory()
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns null

        val service = factory.withInferenceService(ModelType.FAST) { it }

        assertInstanceOf(NoOpInferenceService::class.java, service)
    }

    @Test
    fun `busy lock rejects concurrent local inference`() = runTest {
        val factory = createFactory()
        val mediaPipeService = mockk<LlmInferencePort>(relaxed = true)
        val taskAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 1L,
                huggingFaceModelName = "test/task",
                remoteFileName = "model.task",
                localFileName = "model.task",
                sha256 = "shared-sha",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.TASK
            ),
            configurations = emptyList()
        )
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns taskAsset
        every { mockMediaPipeFactory.create(any<String>(), any<ModelType>()) } returns mediaPipeService

        factory.withInferenceService(ModelType.FAST) {
            assertFailsWith<InferenceBusyException> {
                factory.withInferenceService(ModelType.THINKING) { inner -> inner }
            }
        }
    }
}
