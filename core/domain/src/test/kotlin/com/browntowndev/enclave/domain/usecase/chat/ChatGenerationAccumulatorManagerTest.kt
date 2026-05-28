package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatGenerationAccumulatorManagerTest {
    @Test
    fun `non crew mode accumulates streaming state onto default assistant message`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("thinking", ModelType.FAST))
            val result = manager.reduce(MessageGenerationState.GeneratingText("answer", ModelType.FAST))
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

            val snapshot = result.messages.getValue(MessageId("assistant"))
            assertEquals(MessageState.GENERATING, snapshot.messageState)
            assertEquals("thinking", snapshot.thinkingRaw)
            assertEquals("answer", snapshot.content)
            assertEquals(PipelineStep.FINAL, snapshot.pipelineStep)
            assertTrue(manager.messages.getValue(MessageId("assistant")).isComplete)
        }

    @Test
    fun `second thinking wave clears thinkingEndTime after interleaved answer tokens`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("cot1", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("answer1", ModelType.FAST))

            val acc = manager.messages.getValue(MessageId("assistant"))
            assertTrue(acc.thinkingEndTime != null, "First answer segment should close thinking window")
            assertEquals(MessageState.GENERATING, acc.currentState)

            manager.reduce(MessageGenerationState.ThinkingLive("cot2", ModelType.FAST))

            assertNull(acc.thinkingEndTime, "New CoT wave must reset end time so duration/timer are not stale")
            assertEquals(MessageState.THINKING, acc.currentState)
            assertEquals("cot1cot2", acc.thinkingRaw.toString())
            assertEquals("answer1", acc.content.toString())
        }

    @Test
    fun `interleaved thinking and answer yields ordered transcript segments`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("a", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("1", ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("b", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("2", ModelType.FAST))

            val segs = manager.messages.getValue(MessageId("assistant")).toTranscriptSegments()
            assertEquals(4, segs.size)
            val t0 = segs[0] as MessageTranscriptSegment.Thinking
            val a1 = segs[1] as MessageTranscriptSegment.AssistantText
            val t2 = segs[2] as MessageTranscriptSegment.Thinking
            val a3 = segs[3] as MessageTranscriptSegment.AssistantText
            assertEquals("a", t0.text)
            assertEquals("1", a1.text)
            assertEquals("b", t2.text)
            assertEquals("2", a3.text)
            assertEquals(0, t0.ordinal)
            assertEquals(3, a3.ordinal)
        }

    @Test
    fun `snapshot keeps active thinking segment open until answer starts`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val snapshot =
                manager
                    .reduce(MessageGenerationState.ThinkingLive("still thinking", ModelType.FAST))
                    .messages
                    .getValue(MessageId("assistant"))
            val thinking = snapshot.transcriptSegments.single() as MessageTranscriptSegment.Thinking
            val accumulator = manager.messages.getValue(MessageId("assistant"))

            assertEquals(MessageState.THINKING, snapshot.messageState)
            assertNull(thinking.thinkingEndTime)
            assertNull(thinking.thinkingDurationSeconds)
            assertNull(accumulator.thinkingEndTime)
        }

    @Test
    fun `processing event publishes PROCESSING on fresh accumulator`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val snapshot =
                manager
                    .reduce(MessageGenerationState.Processing(ModelType.FAST))
                    .messages
                    .getValue(MessageId("assistant"))

            assertEquals(MessageState.PROCESSING, snapshot.messageState)
        }

    @Test
    fun `processing event downgrades idle GENERATING without content`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            val accumulator = manager.messages.getValue(MessageId("assistant"))
            accumulator.currentState = MessageState.GENERATING

            val snapshot =
                manager
                    .reduce(MessageGenerationState.Processing(ModelType.FAST))
                    .messages
                    .getValue(MessageId("assistant"))

            assertEquals(MessageState.PROCESSING, snapshot.messageState)
        }

    @Test
    fun `processing state does not downgrade active thinking`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            manager.reduce(MessageGenerationState.ThinkingLive("hmm", ModelType.FAST))
            val afterProcessing =
                manager
                    .reduce(MessageGenerationState.Processing(ModelType.FAST))
                    .messages
                    .getValue(MessageId("assistant"))

            assertEquals(MessageState.THINKING, afterProcessing.messageState)
            val segment = afterProcessing.transcriptSegments.single() as MessageTranscriptSegment.Thinking
            assertEquals("hmm", segment.text)
        }

    @Test
    fun `crew mode creates distinct assistant messages per model step`() =
        runTest {
            val chatRepository = mockk<ChatRepository>()
            coEvery {
                chatRepository.createAssistantMessage(
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    modelType = ModelType.MAIN,
                    pipelineStep = PipelineStep.SYNTHESIS,
                )
            } returns MessageId("main-assistant")
            coEvery {
                chatRepository.createAssistantMessage(
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    modelType = ModelType.FINAL_SYNTHESIS,
                    pipelineStep = PipelineStep.FINAL,
                )
            } returns MessageId("final-assistant")

            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.COUNCIL,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("unused"),
                    chatRepository = chatRepository,
                )

            val first = manager.reduce(MessageGenerationState.Processing(ModelType.MAIN))
            val second = manager.reduce(MessageGenerationState.Processing(ModelType.FINAL_SYNTHESIS))

            assertTrue(first.messages.containsKey(MessageId("main-assistant")))
            assertTrue(second.messages.containsKey(MessageId("final-assistant")))
            coVerify(exactly = 1) {
                chatRepository.createAssistantMessage(
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    modelType = ModelType.MAIN,
                    pipelineStep = PipelineStep.SYNTHESIS,
                )
            }
            coVerify(exactly = 1) {
                chatRepository.createAssistantMessage(
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    modelType = ModelType.FINAL_SYNTHESIS,
                    pipelineStep = PipelineStep.FINAL,
                )
            }
        }

    @Test
    fun `toMessagesState reuses unchanged message snapshot when another crew message updates`() =
        runTest {
            val chatRepository = mockk<ChatRepository>()
            coEvery {
                chatRepository.createAssistantMessage(
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    modelType = ModelType.MAIN,
                    pipelineStep = PipelineStep.SYNTHESIS,
                )
            } returns MessageId("main-assistant")
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.COUNCIL,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("draft-assistant"),
                    chatRepository = chatRepository,
                )

            val afterDraftToken =
                manager
                    .reduce(
                        MessageGenerationState.GeneratingText("draft", ModelType.DRAFT_ONE),
                    ).messages
                    .getValue(MessageId("draft-assistant"))
            val afterMainFirstToken =
                manager
                    .reduce(
                        MessageGenerationState.GeneratingText("main", ModelType.MAIN),
                    ).messages
                    .getValue(MessageId("draft-assistant"))
            val afterMainSecondToken =
                manager
                    .reduce(
                        MessageGenerationState.GeneratingText(" update", ModelType.MAIN),
                    ).messages
                    .getValue(MessageId("draft-assistant"))

            assertSame(afterDraftToken, afterMainFirstToken)
            assertSame(afterMainFirstToken, afterMainSecondToken)
        }

    @Test
    fun `telemetry capture invalidates cached message snapshot`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val beforeTelemetry =
                manager
                    .reduce(MessageGenerationState.GeneratingText("answer", ModelType.FAST))
                    .messages
                    .getValue(MessageId("assistant"))

            val afterFirstTelemetry =
                manager
                    .reduce(
                        MessageGenerationState.TelemetryCaptured(
                            telemetry = TelemetryTestFixtures.telOpenAi1,
                            modelType = ModelType.FAST,
                        ),
                    ).messages
                    .getValue(MessageId("assistant"))
            val afterSecondTelemetry =
                manager
                    .reduce(
                        MessageGenerationState.TelemetryCaptured(
                            telemetry = TelemetryTestFixtures.telAgent2,
                            modelType = ModelType.FAST,
                        ),
                    ).messages
                    .getValue(MessageId("assistant"))

            assertTrue(beforeTelemetry.telemetryRows.isEmpty())
            assertEquals(listOf(TelemetryTestFixtures.telOpenAi1), afterFirstTelemetry.telemetryRows)
            assertEquals(
                listOf(TelemetryTestFixtures.telOpenAi1, TelemetryTestFixtures.telAgent2),
                afterSecondTelemetry.telemetryRows,
            )
        }

    @Test
    fun `failed busy state writes user facing lock message`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val result =
                manager.reduce(
                    MessageGenerationState.Failed(
                        error = InferenceBusyException(),
                        modelType = ModelType.FAST,
                    ),
                )

            val snapshot = result.messages.getValue(MessageId("assistant"))
            assertTrue(snapshot.content.contains("Another message is in progress"))
            assertEquals(MessageState.COMPLETE, snapshot.messageState)
        }

    @Test
    fun `markWebSearchSourcesExtracted updates extracted flag on accumulator sources`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val source1 =
                WebSearchSource(
                    messageId = MessageId("assistant"),
                    title = "Source 1",
                    url = "https://example.com/page1",
                    content = "Content 1",
                    score = 0.9,
                    extracted = false,
                )
            val source2 =
                WebSearchSource(
                    messageId = MessageId("assistant"),
                    title = "Source 2",
                    url = "https://example.com/page2",
                    content = "Content 2",
                    score = 0.8,
                    extracted = false,
                )

            manager.reduce(MessageGenerationState.MediaAttached(listOf(source1.url, source2.url), modelType = ModelType.FAST))
            manager.reduce(MessageGenerationState.WebSearchSourcesAttached(listOf(source1, source2), ModelType.FAST))

            // Before marking, both should have extracted=false
            val snapshotBefore = manager.toMessagesState().messages.getValue(MessageId("assistant"))
            assertFalse(snapshotBefore.webSearchSources[0].extracted)
            assertFalse(snapshotBefore.webSearchSources[1].extracted)

            // Mark source1 as extracted
            manager.markWebSearchSourcesExtracted(listOf("https://example.com/page1"))

            val snapshotAfter = manager.toMessagesState().messages.getValue(MessageId("assistant"))
            assertTrue(
                snapshotAfter.webSearchSources.first { it.url == "https://example.com/page1" }.extracted,
                "Marked source should have extracted=true",
            )
            assertFalse(
                snapshotAfter.webSearchSources.first { it.url == "https://example.com/page2" }.extracted,
                "Unmarked source should still have extracted=false",
            )
        }

    @Test
    fun `ArtifactsAttached upgrades placeholder with same title when final payload arrives`() =
        runTest {
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = mockk(relaxed = true),
                )

            val placeholder =
                ArtifactGenerationRequest(
                    artifactId = "artifact-covenant",
                    title = "Covenant",
                    content = "",
                    targetFormat = MimeType.PDF,
                )
            val full =
                ArtifactGenerationRequest(
                    artifactId = "artifact-covenant",
                    title = "Covenant",
                    content = """{"version":"1.0"}""",
                    targetFormat = MimeType.PDF,
                )

            manager.reduce(MessageGenerationState.ArtifactsAttached(listOf(placeholder), ModelType.FAST))
            manager.reduce(MessageGenerationState.ArtifactsAttached(listOf(full), ModelType.FAST))

            val snapshot = manager.toMessagesState().messages.getValue(MessageId("assistant"))
            assertEquals(1, snapshot.artifacts.size)
            assertEquals(full.content, snapshot.artifacts.first().content)
        }
}
