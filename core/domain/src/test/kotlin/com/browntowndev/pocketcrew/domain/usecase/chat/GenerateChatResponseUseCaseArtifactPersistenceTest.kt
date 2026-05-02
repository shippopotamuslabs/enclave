package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.pocketcrew.domain.usecase.FakeChatRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import io.mockk.slot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class GenerateChatResponseUseCaseArtifactPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val noOpTracker = object : ExtractedUrlTrackerPort {
        override val urls: Set<String> get() = emptySet()
        override fun add(url: String) {}
        override fun clear() {}
    }

    @Test
    fun `artifacts emitted during inference are persisted to the database`() = runTest {
        val chatId = ChatId("chat-1")
        val userMessageId = MessageId("user-1")
        val assistantMessageId = MessageId("assistant-1")
        val artifact = ArtifactGenerationRequest(
            documentType = DocumentType.PDF,
            title = "Test Artifact",
            sections = emptyList()
        )

        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.Artifacts(listOf(artifact), ModelType.FAST),
                    InferenceEvent.Finished(ModelType.FAST)
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService

        val chatRepository = FakeChatRepository()
        val snapshotPort = object : RecordingActiveChatTurnSnapshotPort() {
            private var snapshot: com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages? = null
            
            override suspend fun publish(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey, snapshot: com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages) {
                super.publish(key, snapshot)
                this.snapshot = snapshot
            }
            
            override suspend fun getSnapshot(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey): com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages? {
                return snapshot
            }
        }
 
        val useCase = GenerateChatResponseUseCase(
            pipelineExecutor = mockk(relaxed = true) {
                coEvery { executePipeline(any(), any()) } returns kotlinx.coroutines.flow.flow {
                    emit(MessageGenerationState.Processing(ModelType.FAST))
                    emit(MessageGenerationState.ArtifactsAttached(listOf(artifact), ModelType.FAST))
                    emit(MessageGenerationState.Finished(ModelType.FAST))
                }
            },
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true) {
                coEvery { getMessageById(userMessageId) } returns mockk(relaxed = true)
                coEvery { getMessagesForChat(chatId) } returns emptyList()
            },
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockk(relaxed = true) {
                coEvery { getActiveConfiguration(ModelType.FAST) } returns mockk(relaxed = true) {
                    coEvery { isLocal } returns false
                }
            },
            extractedUrlTracker = noOpTracker,
            chatInferenceExecutor = DirectChatInferenceExecutor(
                inferenceFactory = inferenceFactory,
                activeModelProvider = mockk(relaxed = true) {
                    coEvery { getActiveConfiguration(ModelType.FAST) } returns mockk(relaxed = true)
                },
                messageRepository = mockk(relaxed = true),
                settingsRepository = mockk(relaxed = true) {
                    coEvery { settingsFlow } returns flowOf(mockk(relaxed = true))
                },
                memoriesRepository = mockk(relaxed = true),
                embeddingEnginePort = mockk(relaxed = true) {
                    coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f, 0.2f)
                },
                searchToolPromptComposer = SearchToolPromptComposer(),
                loggingPort = mockk(relaxed = true),
            ),
            embeddingEngine = mockk(relaxed = true),
            activeChatTurnSnapshotPort = snapshotPort
        )
 
        useCase(
            prompt = "Generate an artifact",
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()
 
        advanceUntilIdle()
 
        assertEquals(1, chatRepository.persistedMessageData.size, "Should have persisted exactly 1 message")
        val data = chatRepository.persistedMessageData[0]
        assertEquals(assistantMessageId, data.messageId)
        assertTrue(data.artifacts.isNotEmpty(), "Artifacts list should not be empty. Found: ${data.artifacts}")
        assertEquals("Test Artifact", data.artifacts[0].title)
    }
 
    private open class RecordingActiveChatTurnSnapshotPort : com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort {
        override fun observe(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey): kotlinx.coroutines.flow.Flow<com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages?> = flowOf(null)
        override suspend fun publish(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey, snapshot: com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages) {}
        override suspend fun markSourcesExtracted(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey, urls: List<String>) {}
        override suspend fun acknowledgeHandoff(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey) {}
        override suspend fun attachArtifact(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey, assistantMessageId: MessageId, artifact: ArtifactGenerationRequest) {}
        override suspend fun clear(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey) {}
        override suspend fun getSnapshot(key: com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey): com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages? = null
    }
}
