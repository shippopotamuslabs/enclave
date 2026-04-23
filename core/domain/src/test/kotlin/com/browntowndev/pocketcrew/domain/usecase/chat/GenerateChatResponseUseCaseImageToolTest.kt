package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseImageToolTest {

    @Test
    fun `FAST multimodal models send imageUris directly without pre-analysis`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        val visionService = FakeInferenceService(ModelType.VISION)
        inferenceFactory.serviceMap[ModelType.FAST] = fastService
        inferenceFactory.serviceMap[ModelType.VISION] = visionService

        val messageRepository = mockMessageRepository(
            currentMessage = Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "What should I fix?", imageUri = "file:///tmp/image.jpg"),
                role = Role.USER,
            ),
            resolvedImageTarget = ResolvedImageTarget(
                userMessageId = MessageId("1"),
                imageUri = "file:///tmp/image.jpg",
            ),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastVisionConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(),
        )

        useCase(
            prompt = "What should I fix?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
        assertEquals(listOf("file:///tmp/image.jpg"), fastService.getSentOptions().single().imageUris)
        assertTrue(fastService.getSentPrompts().single().contains("The user attached an image."))
        assertFalse(fastService.getSentPrompts().single().contains("attached_image_inspect"))
        assertFalse(fastService.getSentPrompts().single().contains("Attached image description:"))
    }

    @Test
    fun `FAST multimodal models persist image context on follow-up turns`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val currentMessage = Message(
            id = MessageId("2"),
            chatId = ChatId("chat"),
            content = Content(text = "What was the brand?"), // No image attached to THIS message
            role = Role.USER,
        )

        val messageRepository = mockMessageRepository(
            currentMessage = currentMessage,
            resolvedImageTarget = ResolvedImageTarget(
                userMessageId = MessageId("1"), // Resolved from PREVIOUS message
                imageUri = "file:///tmp/image.jpg",
            ),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastVisionConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(),
        )

        useCase(
            prompt = "What was the brand?",
            userMessageId = MessageId("2"),
            assistantMessageId = MessageId("3"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        assertEquals(listOf("file:///tmp/image.jpg"), fastService.getSentOptions().single().imageUris)
        assertTrue(fastService.getSentPrompts().single().contains("The user attached an image."))
    }

    @Test
    fun `CREW image attachments enable attached image inspect contract when API vision is configured`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val pipelineExecutor = mockk<PipelineExecutorPort>()
        val promptSlot = slot<String>()
        every { pipelineExecutor.executePipeline(any(), capture(promptSlot)) } returns emptyFlow()

        val messageRepository = mockMessageRepository(
            currentMessage = Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "Inspect this", imageUri = "file:///photo.jpg"),
                role = Role.USER,
            ),
            resolvedImageTarget = ResolvedImageTarget(
                userMessageId = MessageId("1"),
                imageUri = "file:///photo.jpg",
            ),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(),
            pipelineExecutor = pipelineExecutor,
        )

        useCase(
            prompt = "Inspect this",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.CREW,
            backgroundInferenceEnabled = false,
        ).toList()

        assertTrue(promptSlot.captured.contains("attached_image_inspect"))
        assertTrue(promptSlot.captured.contains("Inspect this"))
    }

    @Test
    fun `FAST non vision models expose attached image inspect when API vision is configured`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val messageRepository = mockMessageRepository(
            currentMessage = Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "What color is the bicycle?", imageUri = "file:///photo.jpg"),
                role = Role.USER,
            ),
            resolvedImageTarget = ResolvedImageTarget(
                userMessageId = MessageId("1"),
                imageUri = "file:///photo.jpg",
            ),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(),
        )

        useCase(
            prompt = "What color is the bicycle?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        val options = fastService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertTrue(options.availableTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
        assertTrue(fastService.getSentPrompts().single().contains("use attached_image_inspect"))
        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
    }

    @Test
    fun `persisted assistant content strips image tool traces`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse(
                        chunk = """,{"name":"attached_image_inspect","arguments":{"question":"What color is the bicycle?"}},""",
                        modelType = ModelType.FAST,
                    ),
                    InferenceEvent.PartialResponse(
                        chunk = "<tool_result>{\"analysis\":\"It is red.\"}</tool_result>Based on the image, the bicycle is red.",
                        modelType = ModelType.FAST,
                    ),
                    InferenceEvent.Finished(ModelType.FAST),
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val contentSlot = slot<String>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit

        val messageRepository = mockMessageRepository(
            currentMessage = Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "What color is the bicycle?", imageUri = "file:///photo.jpg"),
                role = Role.USER,
            ),
            resolvedImageTarget = ResolvedImageTarget(
                userMessageId = MessageId("1"),
                imageUri = "file:///photo.jpg",
            ),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(),
        )

        useCase(
            prompt = "What color is the bicycle?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        val persisted = contentSlot.captured
        assertFalse(persisted.contains("attached_image_inspect"))
        assertFalse(persisted.contains("<tool_result>"))
        assertTrue(persisted.contains("Based on the image, the bicycle is red."))
    }

    @Test
    fun `historical image messages rehydrate with persisted analysis`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val priorUser = Message(
            id = MessageId("prior-user"),
            chatId = ChatId("chat"),
            content = Content(text = "", imageUri = "file:///prior.jpg"),
            role = Role.USER,
            messageState = MessageState.COMPLETE,
        )
        val currentUser = Message(
            id = MessageId("1"),
            chatId = ChatId("chat"),
            content = Content(text = "What changed?"),
            role = Role.USER,
        )
        val analysis = MessageVisionAnalysis(
            id = "analysis-1",
            userMessageId = MessageId("prior-user"),
            imageUri = "file:///prior.jpg",
            promptText = "",
            analysisText = "A cracked pipe joint under the sink.",
            modelType = ModelType.VISION,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val messageRepository = mockMessageRepository(
            currentMessage = currentUser,
            chatMessages = listOf(priorUser, currentUser),
            visionAnalyses = mapOf(MessageId("prior-user") to listOf(analysis)),
        )

        val useCase = createUseCase(
            inferenceFactory = inferenceFactory,
            messageRepository = messageRepository,
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(),
        )

        useCase(
            prompt = "What changed?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        assertTrue(fastService.getHistory().single().content.contains("A cracked pipe joint under the sink."))
        assertFalse(fastService.getHistory().single().content.contains("[User attached an image]"))
    }

    private fun createUseCase(
        inferenceFactory: FakeInferenceFactory,
        messageRepository: MessageRepository,
        activeModelProvider: ActiveModelProviderPort,
        settingsRepository: SettingsRepository,
        pipelineExecutor: PipelineExecutorPort = mockPipelineExecutor(),
        chatRepository: ChatRepository = mockk(relaxed = true),
    ): GenerateChatResponseUseCase {
        val directExecutor = DirectChatInferenceExecutor(
            inferenceFactory = inferenceFactory,
            activeModelProvider = activeModelProvider,
            messageRepository = messageRepository,
            settingsRepository = settingsRepository,
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk<LoggingPort>(relaxed = true),
        )
        return GenerateChatResponseUseCase(
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            activeModelProvider = activeModelProvider,
            extractedUrlTracker = object : com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort {
                override val urls: Set<String> get() = emptySet()
                override fun add(url: String) {}
                override fun clear() {}
            },
            chatInferenceExecutor = directExecutor,
            embeddingEngine = mockk(relaxed = true),
        )
    }

    private fun mockActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration?,
    ): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns fastConfig
        coEvery { getActiveConfiguration(ModelType.VISION) } returns visionConfig
    }

    private fun fastTextConfig(): ActiveModelConfiguration = ActiveModelConfiguration(
        id = LocalModelConfigurationId("fast-text"),
        isLocal = true,
        name = "Fast",
        systemPrompt = "Be concise.",
        reasoningEffort = null,
        temperature = 0.7,
        topK = 40,
        topP = 0.95,
        maxTokens = 512,
        minP = 0.0,
        repetitionPenalty = 1.1,
        contextWindow = 4096,
        thinkingEnabled = false,
        isMultimodal = false,
    )

    private fun fastVisionConfig(): ActiveModelConfiguration = fastTextConfig().copy(
        id = LocalModelConfigurationId("fast-vision"),
        name = "Fast Vision",
        isMultimodal = true,
    )

    private fun apiVisionConfig(): ActiveModelConfiguration = ActiveModelConfiguration(
        id = ApiModelConfigurationId("vision-api"),
        isLocal = false,
        name = "Vision API",
        systemPrompt = "Inspect the image carefully.",
        reasoningEffort = null,
        temperature = 0.2,
        topK = 40,
        topP = 0.95,
        maxTokens = 1024,
        minP = 0.0,
        repetitionPenalty = 1.0,
        contextWindow = 8192,
        thinkingEnabled = false,
        isMultimodal = true,
    )

    private fun mockMessageRepository(
        currentMessage: Message,
        resolvedImageTarget: ResolvedImageTarget? = null,
        chatMessages: List<Message> = emptyList(),
        visionAnalyses: Map<MessageId, List<MessageVisionAnalysis>> = emptyMap(),
    ): MessageRepository = mockk {
        coEvery { getMessageById(any()) } returns currentMessage
        coEvery { getMessagesForChat(any()) } returns chatMessages
        coEvery { getVisionAnalysesForMessages(any()) } answers {
            val ids = args[0] as List<MessageId>
            ids.associateWith { visionAnalyses[it].orEmpty() }.filterValues { it.isNotEmpty() }
        }
        coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns resolvedImageTarget
        coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
        coEvery { getChatSummary(any()) } returns null
    }

    private fun mockSettingsRepository(settings: SettingsData = SettingsData()): SettingsRepository = mockk {
        every { settingsFlow } returns flowOf(settings)
    }

    private fun mockPipelineExecutor(): PipelineExecutorPort = mockk {
        every { executePipeline(any(), any()) } returns emptyFlow()
        coEvery { stopPipeline(any()) } returns Unit
        coEvery { resumeFromState(any(), any(), any(), any()) } returns emptyFlow()
    }
}
