package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseTelemetryPersistenceTest {
    @Test
    fun `accumulator persists telemetry rows with assistant message`() =
        runTest {
            val chatRepository = FakeChatRepository()

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
                messageRepository = FakeMessageRepository(),
                embeddingEngine = FakeEmbeddingEngine(),
                extractedUrls = emptySet(),
            )(manager)

            assertEquals(1, chatRepository.persistAllMessageDataCalls.size)
            assertEquals(
                TelemetryTestFixtures.mOpenAi1,
                chatRepository.persistAllMessageDataCalls.single().messageId,
            )
            assertEquals(
                "0.002500",
                chatRepository.persistAllMessageDataTelemetryCalls
                    .single()
                    .single()
                    .costUsd
                    ?.toPlainString(),
            )
        }
}
