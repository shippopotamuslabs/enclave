package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseTelemetryPersistenceTest {
    @Test
    fun `accumulator persists telemetry rows with assistant message`() =
        runTest {
            val chatRepository = mockk<ChatRepository>(relaxed = true)
            val telemetrySlot = slot<List<com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry>>()
            coEvery {
                chatRepository.persistAllMessageData(
                    messageId = any(),
                    modelType = any(),
                    thinkingStartTime = any(),
                    thinkingEndTime = any(),
                    thinkingDuration = any(),
                    thinkingRaw = any(),
                    content = any(),
                    messageState = any(),
                    pipelineStep = any(),
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                    telemetry = capture(telemetrySlot),
                )
            } returns Unit

            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = TelemetryTestFixtures.chat1,
                    userMessageId = MessageId("user-1"),
                    defaultAssistantMessageId = TelemetryTestFixtures.mOpenAi1,
                    chatRepository = chatRepository,
                )
            manager.reduce(
                MessageGenerationState.TelemetryCaptured(
                    telemetry = TelemetryTestFixtures.telOpenAi1,
                    modelType = ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

            PersistAccumulatedChatMessagesUseCase(
                chatRepository = chatRepository,
                messageRepository = mockk(relaxed = true),
                embeddingEngine =
                    mockk<EmbeddingEnginePort> {
                        coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f)
                    },
                extractedUrls = emptySet(),
            )(manager)

            coVerify(exactly = 1) {
                chatRepository.persistAllMessageData(
                    messageId = TelemetryTestFixtures.mOpenAi1,
                    telemetry = any(),
                    modelType = any(),
                    thinkingStartTime = any(),
                    thinkingEndTime = any(),
                    thinkingDuration = any(),
                    thinkingRaw = any(),
                    content = any(),
                    messageState = any(),
                    pipelineStep = any(),
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            }
            assertEquals(
                "0.002500",
                telemetrySlot.captured
                    .single()
                    .costUsd
                    ?.toPlainString(),
            )
        }
}
