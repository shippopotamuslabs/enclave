package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LlamaInferenceServiceImplTest {

    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var loggingPort: LoggingPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        sessionManager = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        localModelRepository = mockk()
        activeModelProvider = mockk()
        context = mockk(relaxed = true)

        every { context.getExternalFilesDir(null) } returns File("/tmp")

        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("7"),
            isLocal = true,
            name = "Llama Fast",
            modelId = "7",
            systemPrompt = "You are a helpful assistant.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("7")) } returns LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("99"),
                huggingFaceModelName = "test/llama",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
            ),
            configurations = emptyList(),
        )
        coEvery { sessionManager.initializeEngine(any()) } returns Unit
        coEvery { sessionManager.setHistory(any()) } returns Unit
        coEvery { sessionManager.startNewConversation() } returns Unit
        coEvery { sessionManager.clearConversation() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sendPrompt forwards imageUris to llama generation options`() = runTest {
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("9"),
            isLocal = true,
            name = "Llama Vision",
            modelId = "9",
            systemPrompt = "You describe images.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("9")) } returns LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("199"),
                huggingFaceModelName = "test/llama-vision",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                isMultimodal = true,
                mmprojLocalFileName = "mmproj.gguf",
            ),
            configurations = emptyList(),
        )
        coEvery { sessionManager.sendUserMessage(any()) } returns Unit
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed("Detailed image description")
        )

        val optionsSlot = slot<GenerationOptions>()
        val service = createService(ModelType.VISION)

        service.sendPrompt(
            prompt = "Describe this image in detail",
            options = GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.VISION,
                imageUris = listOf("file:///tmp/cat.jpg"),
                modelId = "9",
            ),
            closeConversation = false,
        ).toList()

        verify { sessionManager.streamAssistantResponseWithOptions(capture(optionsSlot)) }
        assertEquals(listOf("file:///tmp/cat.jpg"), optionsSlot.captured.imageUris)
    }

    @Test
    fun `sendPrompt returns typed error when vision capable gguf model is missing mmproj`() = runTest {
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("9"),
            isLocal = true,
            name = "Llama Vision",
            modelId = "9",
            systemPrompt = "You describe images.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("9")) } returns LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("199"),
                huggingFaceModelName = "test/llama-vision",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                isMultimodal = true,
            ),
            configurations = emptyList(),
        )

        val service = createService(ModelType.VISION)
        val events = service.sendPrompt(
            prompt = "Describe this image in detail",
            options = GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.VISION,
                imageUris = listOf("file:///tmp/cat.jpg"),
                modelId = "9",
            ),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Vision-capable GGUF model requires an mmproj companion file.", error.cause.message)
    }

    @Test
    fun `completeSingleTurn rebuilds session history from Rust messages`() = runTest {
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed("Assistant text"),
        )
        val capturedHistories = mutableListOf<List<ChatMessage>>()
        val service = createService(ModelType.FAST)

        val result = service.completeSingleTurn(
            sessionMessages = listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "Question", metadataJson = "null"),
                AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Prior answer", metadataJson = "null"),
            ),
            systemPrompt = "System",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
        )

        coVerify(atLeast = 1) { sessionManager.setHistory(capture(capturedHistories)) }
        assertEquals(
            listOf(
                ChatMessage(Role.USER, "Question"),
                ChatMessage(Role.ASSISTANT, "Prior answer"),
            ),
            capturedHistories.last(),
        )
        assertEquals(AgentFinishReason.Stop, result.finishReason)
        assertEquals("Assistant text", result.assistantText)
    }

    private fun createService(modelType: ModelType = ModelType.FAST): LlamaInferenceServiceImpl =
        LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            loggingPort = loggingPort,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            context = context,
            modelType = modelType,
        )
}
