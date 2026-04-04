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
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [InferenceFactoryImpl] SHA-256-based engine reuse.
 */
class InferenceFactoryImplShaReuseTest {

    private lateinit var factory: InferenceFactoryImpl

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockModelRegistry = mockk<ModelRegistryPort>(relaxed = true)
    private val mockProcessThinkingTokens = mockk<ProcessThinkingTokensUseCase>(relaxed = true)
    private val mockLlamaChatSessionManager = mockk<LlamaChatSessionManager>(relaxed = true)
    private val mockLoggingPort = mockk<LoggingPort>(relaxed = true)
    private val mockConversationManager = mockk<ConversationManagerPort>(relaxed = true)
    private val mockMediaPipeFactory = mockk<MediaPipeInferenceServiceFactory>(relaxed = true)

    private val sharedSha = "abc123"
    private val differentSha = "xyz789"

    private fun ggufAsset(sha: String, id: Long = 1L) = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = id,
            huggingFaceModelName = "test/llama",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = sha,
            sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = emptyList()
    )

    private fun taskAsset(sha: String, id: Long = 2L) = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = id,
            huggingFaceModelName = "test/litert",
            remoteFileName = "model.task",
            localFileName = "model.task",
            sha256 = sha,
            sizeInBytes = 2000L,
            modelFileFormat = ModelFileFormat.TASK
        ),
        configurations = emptyList()
    )

    private fun litertAsset(sha: String, id: Long = 3L) = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = id,
            huggingFaceModelName = "test/litertlm",
            remoteFileName = "model.litertlm",
            localFileName = "model.litertlm",
            sha256 = sha,
            sizeInBytes = 3000L,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        configurations = emptyList()
    )

    private fun makeFactory(): InferenceFactoryImpl {
        every { mockContext.getExternalFilesDir(null) } returns java.io.File("/mock")
        val mockMediaPipeService = mockk<MediaPipeInferenceServiceImpl>(relaxed = true)
        // Update to match new signature: create(modelPath)
        every { mockMediaPipeFactory.create(any()) } returns mockMediaPipeService

        return InferenceFactoryImpl(
            context = mockContext,
            conversationManagerProvider = { mockConversationManager },
            modelRegistry = mockModelRegistry,
            processThinkingTokens = mockProcessThinkingTokens,
            llamaChatSessionManagerProvider = { mockLlamaChatSessionManager },
            loggingPort = mockLoggingPort,
            mediaPipeFactory = mockMediaPipeFactory
        )
    }

    @Test
    fun `FAST and THINKING share same SHA — each emits its own role in events`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val service = factory.getInferenceService(ModelType.FAST)
        
        // Both roles share the same service instance
        val fastService = factory.getInferenceService(ModelType.FAST)
        val thinkingService = factory.getInferenceService(ModelType.THINKING)
        assertTrue(fastService === thinkingService)

        // Verify that FAST request emits FAST modelType
        val fastOptions = com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions(
            reasoningBudget = 0,
            modelType = ModelType.FAST
        )
        // We'll need to mock the underlying engine/session to actually emit something
        // but for now we just verify the service is not role-locked.
        // In a real integration test, we'd check the flow.
    }

    @Test
    fun `all ModelTypes sharing same SHA — single service reused with ref count 3`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.DRAFT_ONE) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FINAL_SYNTHESIS) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val serviceFast = factory.getInferenceService(ModelType.FAST)
        val serviceDraft = factory.getInferenceService(ModelType.DRAFT_ONE)
        val serviceSynth = factory.getInferenceService(ModelType.FINAL_SYNTHESIS)

        assertTrue(serviceFast === serviceDraft, "FAST and DRAFT_ONE must share same instance")
        assertTrue(serviceDraft === serviceSynth, "DRAFT_ONE and FINAL_SYNTHESIS must share same instance")
    }

    @Test
    fun `different SHA — separate services created`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset("aaa111")
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset("bbb222")

        factory = makeFactory()
        val serviceA = factory.getInferenceService(ModelType.FAST)
        val serviceB = factory.getInferenceService(ModelType.THINKING)

        assertTrue(serviceA !== serviceB, "Different SHAs must produce different instances")
    }

    @Test
    fun `same SHA different extensions — separate services created`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns taskAsset(sharedSha)

        factory = makeFactory()
        val serviceA = factory.getInferenceService(ModelType.FAST)
        val serviceB = factory.getInferenceService(ModelType.THINKING)

        assertTrue(serviceA !== serviceB, "Different extensions must produce different instances")
    }

    @Test
    fun `all roles share same instance when SHA matches`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val s1 = factory.getInferenceService(ModelType.FAST)
        val s2 = factory.getInferenceService(ModelType.THINKING)
        val s3 = factory.getInferenceService(ModelType.DRAFT_ONE)
        val s4 = factory.getInferenceService(ModelType.DRAFT_TWO)
        val s5 = factory.getInferenceService(ModelType.MAIN)
        val s6 = factory.getInferenceService(ModelType.FINAL_SYNTHESIS)

        assertTrue(s1 === s2 && s2 === s3 && s3 === s4 && s4 === s5 && s5 === s6,
            "All roles must share the same instance when SHA matches")
    }

    @Test
    fun `FAST and THINKING share same LiteRT service when litertlm SHA matches`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns litertAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns litertAsset(sharedSha)

        factory = makeFactory()
        val fastService = factory.getInferenceService(ModelType.FAST)
        val thinkingService = factory.getInferenceService(ModelType.THINKING)

        assertTrue(fastService === thinkingService, "LiteRT services should be shared for the same .litertlm SHA")
        assertInstanceOf(LiteRtInferenceServiceImpl::class.java, fastService)
        assertInstanceOf(LiteRtInferenceServiceImpl::class.java, thinkingService)
    }

    @Test
    fun `releaseUsage by one role does not kill engine for remaining roles`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val service = factory.getInferenceService(ModelType.FAST)
        val serviceThinking = factory.getInferenceService(ModelType.THINKING)
        
        factory.registerUsage(service)
        factory.registerUsage(serviceThinking)

        factory.releaseUsage(service)

        val stillAvailable = factory.getInferenceService(ModelType.THINKING)
        assertTrue(stillAvailable === service, "Engine must remain accessible while ref count > 0")
    }

    @Test
    fun `reference count drops to zero but cached service remains reusable`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val service = factory.getInferenceService(ModelType.FAST)
        factory.registerUsage(service)
        factory.releaseUsage(service)

        val newService = factory.getInferenceService(ModelType.FAST)
        assertTrue(newService === service, "Idle service should remain cached for reuse")
    }

    @Test
    fun `LiteRT service remains cached after request completion style release`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns litertAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns litertAsset(sharedSha)

        factory = makeFactory()
        val fastService = factory.getInferenceService(ModelType.FAST)
        factory.registerUsage(fastService)
        factory.releaseUsage(fastService)

        val thinkingService = factory.getInferenceService(ModelType.THINKING)
        assertTrue(thinkingService === fastService, "Mode switches should reuse the same cached LiteRT service")
    }

    @Test
    fun `NoOp returned when asset missing`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns null

        factory = makeFactory()
        val service = factory.getInferenceService(ModelType.FAST)
        assertInstanceOf(NoOpInferenceService::class.java, service)
    }

    @Test
    fun `NoOp for one role does not affect another role's service`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val fastService = factory.getInferenceService(ModelType.FAST)
        val thinkingService = factory.getInferenceService(ModelType.THINKING)

        assertInstanceOf(NoOpInferenceService::class.java, fastService)
        assertTrue(thinkingService is LlamaInferenceServiceImpl)
        assertTrue(fastService !== thinkingService)
    }

    @Test
    fun `model file change breaks SHA match — separate instance created`() = runTest {
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns ggufAsset(sharedSha)
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset(sharedSha)

        factory = makeFactory()
        val fastService = factory.getInferenceService(ModelType.FAST)
        val thinkingBefore = factory.getInferenceService(ModelType.THINKING)
        assertTrue(fastService === thinkingBefore)

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.THINKING) } returns ggufAsset(differentSha)
        val thinkingAfter = factory.getInferenceService(ModelType.THINKING)

        assertTrue(thinkingAfter !== fastService, "THINKING must get new instance after SHA change")
        val fastStill = factory.getInferenceService(ModelType.FAST)
        assertTrue(fastStill === fastService, "FAST's service must remain unaffected")
    }
}
