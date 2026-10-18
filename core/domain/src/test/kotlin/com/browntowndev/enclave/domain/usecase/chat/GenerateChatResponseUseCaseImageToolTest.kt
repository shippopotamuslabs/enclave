package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakePromptComposer
import com.browntowndev.enclave.core.testing.FakeSearchIntentClassifier
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.FakeInferenceFactory
import com.browntowndev.enclave.domain.usecase.FakeInferenceLockManager
import com.browntowndev.enclave.domain.usecase.FakeInferenceService
import com.browntowndev.enclave.domain.usecase.FakePipelineExecutor
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseImageToolTest {
    @Test
    fun `FAST multimodal models send imageUris directly without pre-analysis`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val fastService =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            val visionService = FakeInferenceService(ModelType.VISION)
            inferenceFactory.serviceMap[ModelType.FAST] = fastService
            inferenceFactory.serviceMap[ModelType.VISION] = visionService

            val messageRepository =
                fakeMessageRepository(
                    currentMessage =
                        Message(
                            id = MessageId("1"),
                            chatId = ChatId("chat"),
                            content = Content(text = "What should I fix?", imageUris = listOf("file:///tmp/image.jpg")),
                            role = Role.USER,
                        ),
                    resolvedImageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("1"),
                            imageUris = listOf("file:///tmp/image.jpg"),
                        ),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastVisionConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(),
                )

            useCase(
                prompt = "What should I fix?",
                userMessageId = MessageId("1"),
                assistantMessageId = MessageId("2"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = false,
            ).toList()

            assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
            assertEquals(listOf("file:///tmp/image.jpg"), fastService.getSentOptions().single().imageUris)
            assertTrue(fastService.getSentPrompts().single().contains("The user attached one or more images."))
            assertFalse(fastService.getSentPrompts().single().contains("attached_image_inspect"))
            assertFalse(fastService.getSentPrompts().single().contains("Attached image description:"))
        }

    @Test
    fun `FAST multimodal models persist image context on follow-up turns`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val fastService =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            inferenceFactory.serviceMap[ModelType.FAST] = fastService

            val currentMessage =
                Message(
                    id = MessageId("2"),
                    chatId = ChatId("chat"),
                    content = Content(text = "What was the brand?"), // No image attached to THIS message
                    role = Role.USER,
                )

            val messageRepository =
                fakeMessageRepository(
                    currentMessage = currentMessage,
                    resolvedImageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("1"), // Resolved from PREVIOUS message
                            imageUris = listOf("file:///tmp/image.jpg"),
                        ),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastVisionConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(),
                )

            useCase(
                prompt = "What was the brand?",
                userMessageId = MessageId("2"),
                assistantMessageId = MessageId("3"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = false,
            ).toList()

            assertEquals(listOf("file:///tmp/image.jpg"), fastService.getSentOptions().single().imageUris)
            assertTrue(fastService.getSentPrompts().single().contains("The user attached one or more images."))
        }

    @Test
    fun `COUNCIL image attachments enable attached image inspect contract when API vision is configured`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val pipelineExecutor = FakePipelineExecutor()

            val messageRepository =
                fakeMessageRepository(
                    currentMessage =
                        Message(
                            id = MessageId("1"),
                            chatId = ChatId("chat"),
                            content = Content(text = "Inspect this", imageUris = listOf("file:///photo.jpg")),
                            role = Role.USER,
                        ),
                    resolvedImageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("1"),
                            imageUris = listOf("file:///photo.jpg"),
                        ),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository = fakeSettingsRepository(),
                    pipelineExecutor = pipelineExecutor,
                )

            useCase(
                prompt = "Inspect this",
                userMessageId = MessageId("1"),
                assistantMessageId = MessageId("2"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Council,
                backgroundInferenceEnabled = false,
            ).toList()

            val capturedPrompt = pipelineExecutor.lastUserMessage.orEmpty()
            assertTrue(capturedPrompt.contains("attached_image_inspect"))
            assertTrue(capturedPrompt.contains("Inspect this"))
        }

    @Test
    fun `FAST non vision models expose attached image inspect when API vision is configured`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val fastService =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            inferenceFactory.serviceMap[ModelType.FAST] = fastService

            val messageRepository =
                fakeMessageRepository(
                    currentMessage =
                        Message(
                            id = MessageId("1"),
                            chatId = ChatId("chat"),
                            content = Content(text = "What color is the bicycle?", imageUris = listOf("file:///photo.jpg")),
                            role = Role.USER,
                        ),
                    resolvedImageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("1"),
                            imageUris = listOf("file:///photo.jpg"),
                        ),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository = fakeSettingsRepository(),
                )

            useCase(
                prompt = "What color is the bicycle?",
                userMessageId = MessageId("1"),
                assistantMessageId = MessageId("2"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = false,
            ).toList()

            val options = fastService.getSentOptions().single()
            assertTrue(options.toolingEnabled)
            assertTrue(options.activeTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
            assertTrue(fastService.getSentPrompts().single().contains("use attached_image_inspect"))
            assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
        }

    @Test
    fun `persisted assistant content strips image tool traces`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val inferenceService =
                FakeInferenceService(ModelType.FAST).apply {
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
                        ),
                    )
                }
            inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
            val chatRepository = FakeChatRepository()

            val messageRepository =
                fakeMessageRepository(
                    currentMessage =
                        Message(
                            id = MessageId("1"),
                            chatId = ChatId("chat"),
                            content = Content(text = "What color is the bicycle?", imageUris = listOf("file:///photo.jpg")),
                            role = Role.USER,
                        ),
                    resolvedImageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("1"),
                            imageUris = listOf("file:///photo.jpg"),
                        ),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    chatRepository = chatRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository = fakeSettingsRepository(),
                )

            useCase(
                prompt = "What color is the bicycle?",
                userMessageId = MessageId("1"),
                assistantMessageId = MessageId("2"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = false,
            ).toList()

            val persisted =
                chatRepository.persistAllMessageDataCalls
                    .single { it.messageId == MessageId("2") }
                    .content
            assertFalse(persisted.contains("attached_image_inspect"))
            assertFalse(persisted.contains("<tool_result>"))
            assertTrue(persisted.contains("Based on the image, the bicycle is red."))
        }

    @Test
    fun `historical image messages rehydrate with persisted analysis`() =
        runTest {
            val inferenceFactory = FakeInferenceFactory()
            val fastService =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            inferenceFactory.serviceMap[ModelType.FAST] = fastService

            val priorUser =
                Message(
                    id = MessageId("prior-user"),
                    chatId = ChatId("chat"),
                    content = Content(text = "", imageUris = listOf("file:///prior.jpg")),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                )
            val currentUser =
                Message(
                    id = MessageId("1"),
                    chatId = ChatId("chat"),
                    content = Content(text = "What changed?"),
                    role = Role.USER,
                )
            val analysis =
                MessageVisionAnalysis(
                    id = "analysis-1",
                    userMessageId = MessageId("prior-user"),
                    imageUri = "file:///prior.jpg",
                    promptText = "",
                    analysisText = "A cracked pipe joint under the sink.",
                    modelType = ModelType.VISION,
                    createdAt = 1L,
                    updatedAt = 1L,
                )

            val messageRepository =
                fakeMessageRepository(
                    currentMessage = currentUser,
                    chatMessages = listOf(priorUser, currentUser),
                    visionAnalyses = mapOf(MessageId("prior-user") to listOf(analysis)),
                )

            val useCase =
                createUseCase(
                    inferenceFactory = inferenceFactory,
                    messageRepository = messageRepository,
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository = fakeSettingsRepository(),
                )

            useCase(
                prompt = "What changed?",
                userMessageId = MessageId("1"),
                assistantMessageId = MessageId("2"),
                chatId = ChatId("chat"),
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = false,
            ).toList()

            assertTrue(
                fastService
                    .getHistory()
                    .single()
                    .content
                    .contains("A cracked pipe joint under the sink."),
            )
            assertFalse(
                fastService
                    .getHistory()
                    .single()
                    .content
                    .contains("[User attached one or more images]"),
            )
        }

    private fun createUseCase(
        inferenceFactory: FakeInferenceFactory,
        messageRepository: MessageRepository,
        activeModelProvider: ActiveModelProviderPort,
        settingsRepository: SettingsRepository,
        pipelineExecutor: FakePipelineExecutor = FakePipelineExecutor(),
        chatRepository: ChatRepository = FakeChatRepository(),
    ): GenerateChatResponseUseCase {
        val directExecutor =
            DirectChatInferenceExecutor(
                inferenceFactory = inferenceFactory,
                activeModelProvider = activeModelProvider,
                messageRepository = messageRepository,
                settingsRepository = settingsRepository,
                memoriesRepository = FakeMemoriesRepository(),
                embeddingEnginePort = FakeEmbeddingEngine(),
                promptComposer = FakePromptComposer(),
                defaultModelRepository = FakeDefaultModelRepository(),
                mediaProviderRepository = FakeMediaProviderRepository(),
                mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                loggingPort = FakeLoggingPort(),
                searchIntentClassifier = FakeSearchIntentClassifier(),
                toolExecutor = FakeToolExecutor(),
            )
        return GenerateChatResponseUseCase(
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = FakeLoggingPort(),
            activeModelProvider = activeModelProvider,
            extractedUrlTracker =
                object : ExtractedUrlTrackerPort {
                    override val urls: Set<String> get() = emptySet()

                    override fun add(url: String) {}

                    override fun clear() {}
                },
            chatInferenceExecutor = directExecutor,
            embeddingEngine = FakeEmbeddingEngine(),
            inferenceLockManager = FakeInferenceLockManager(),
        )
    }

    private fun fakeActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration?,
    ): FakeActiveModelProvider =
        FakeActiveModelProvider(configuration = fastConfig).apply {
            configurationsByType[ModelType.VISION] = visionConfig
        }

    private fun fastTextConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "test-model",
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

    private fun fastVisionConfig(): ActiveModelConfiguration =
        fastTextConfig().copy(
            id = LocalModelConfigurationId("fast-vision"),
            name = "Fast Vision",
            isMultimodal = true,
        )

    private fun apiVisionConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "test-model",
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

    private suspend fun fakeMessageRepository(
        currentMessage: Message,
        resolvedImageTarget: ResolvedImageTarget? = null,
        chatMessages: List<Message> = emptyList(),
        visionAnalyses: Map<MessageId, List<MessageVisionAnalysis>> = emptyMap(),
    ): FakeMessageRepository {
        val seed = (chatMessages + currentMessage).distinctBy { it.id }
        val repository = FakeMessageRepository(initial = seed)
        repository.resolvedImageTarget = resolvedImageTarget
        visionAnalyses.values.flatten().forEach { analysis ->
            repository.saveVisionAnalysis(
                userMessageId = analysis.userMessageId,
                imageUri = analysis.imageUri,
                promptText = analysis.promptText,
                analysisText = analysis.analysisText,
                modelType = analysis.modelType,
            )
        }
        return repository
    }

    private fun fakeSettingsRepository(settings: SettingsData = SettingsData()): FakeSettingsRepository =
        FakeSettingsRepository(initial = settings)
}
