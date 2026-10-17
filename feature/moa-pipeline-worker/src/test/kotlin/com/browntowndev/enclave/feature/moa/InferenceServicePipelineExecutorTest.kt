package com.browntowndev.enclave.feature.moa

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakePipelineStateRepository
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.feature.inference.InferenceEventBus
import com.browntowndev.enclave.feature.moa.service.FakeInferenceServiceStarter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServicePipelineExecutorTest {
    private lateinit var serviceStarter: FakeInferenceServiceStarter
    private lateinit var pipelineStateRepository: FakePipelineStateRepository
    private lateinit var inferenceEventBus: InferenceEventBus

    private lateinit var executor: InferenceServicePipelineExecutor

    @BeforeEach
    fun setup() {
        serviceStarter = FakeInferenceServiceStarter(ApplicationProvider.getApplicationContext())
        pipelineStateRepository = FakePipelineStateRepository()
        inferenceEventBus = InferenceEventBus()

        executor =
            InferenceServicePipelineExecutor(
                serviceStarter = serviceStarter,
                pipelineStateRepository = pipelineStateRepository,
                inferenceEventBus = inferenceEventBus,
            )
    }

    @Test
    fun `executePipeline collects thinking chunks until terminal FINAL StepCompleted`() =
        runTest {
            val chatId = "chat1"
            val emittedStates = mutableListOf<MessageGenerationState>()

            val job =
                launch {
                    executor.executePipeline(chatId, "Hello").toList(emittedStates)
                }

            advanceUntilIdle()

            // Simulate service emitting states via event bus
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.ThinkingLive("\n", ModelType.DRAFT_ONE))
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.ThinkingLive("   ", ModelType.DRAFT_ONE))
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.GeneratingText("Hello!", ModelType.DRAFT_ONE))
            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.StepCompleted(
                    stepOutput = "",
                    modelDisplayName = "Main",
                    modelType = ModelType.MAIN,
                    stepType = PipelineStep.FINAL,
                ),
            )

            advanceUntilIdle()

            job.join()

            val thinkingEvents = emittedStates.filterIsInstance<MessageGenerationState.ThinkingLive>()
            assertEquals(2, thinkingEvents.size)
            assertEquals("\n", thinkingEvents[0].thinkingChunk)
            assertEquals("   ", thinkingEvents[1].thinkingChunk)
            assertTrue(emittedStates.any { it is MessageGenerationState.GeneratingText })
            assertTrue(emittedStates.any { it is MessageGenerationState.StepCompleted })
        }

    @Test
    fun `executePipeline passes through terminal Finished state`() =
        runTest {
            val chatId = "chat1"
            val emittedStates = mutableListOf<MessageGenerationState>()

            val job =
                launch {
                    executor.executePipeline(chatId, "Hello").toList(emittedStates)
                }

            advanceUntilIdle()

            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.Processing(ModelType.DRAFT_ONE))
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.Finished(ModelType.FINAL_SYNTHESIS))

            advanceUntilIdle()

            job.join()

            assertTrue(emittedStates.any { it is MessageGenerationState.Processing })
            assertTrue(emittedStates.any { it is MessageGenerationState.Finished })
        }

    @Test
    fun `executePipeline terminates on Failed state`() =
        runTest {
            val chatId = "chat1"
            val emittedStates = mutableListOf<MessageGenerationState>()

            val job =
                launch {
                    executor.executePipeline(chatId, "Hello").toList(emittedStates)
                }

            advanceUntilIdle()

            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.Processing(ModelType.DRAFT_ONE))
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.Failed(IllegalStateException("Mock error"), ModelType.MAIN))

            advanceUntilIdle()

            job.join()

            assertTrue(emittedStates.any { it is MessageGenerationState.Processing })
            assertTrue(emittedStates.any { it is MessageGenerationState.Failed })
        }

    @Test
    fun `executePipeline does not terminate on non-FINAL StepCompleted states`() =
        runTest {
            val chatId = "chat1"
            val emittedStates = mutableListOf<MessageGenerationState>()

            val job =
                launch {
                    executor.executePipeline(chatId, "Hello").toList(emittedStates)
                }

            advanceUntilIdle()

            // Non-final steps should NOT terminate the flow
            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.StepCompleted(
                    stepOutput = "",
                    modelDisplayName = "Draft",
                    modelType = ModelType.DRAFT_ONE,
                    stepType = PipelineStep.DRAFT_ONE,
                ),
            )
            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.StepCompleted(
                    stepOutput = "",
                    modelDisplayName = "Main",
                    modelType = ModelType.MAIN,
                    stepType = PipelineStep.FINAL,
                ),
            )

            advanceUntilIdle()

            job.join()

            val stepCompletedEvents = emittedStates.filterIsInstance<MessageGenerationState.StepCompleted>()
            assertEquals(2, stepCompletedEvents.size)
            assertTrue(stepCompletedEvents.any { it.stepType == PipelineStep.DRAFT_ONE })
            assertTrue(stepCompletedEvents.any { it.stepType == PipelineStep.FINAL })
        }

    @Test
    fun `stopPipeline stops the service and clears state`() =
        runTest {
            val pipelineId = "pipeline1"

            executor.stopPipeline(pipelineId)

            assertEquals(1, serviceStarter.stopCount)
            assertEquals(listOf(pipelineId), pipelineStateRepository.clearCalls)
        }

    @Test
    fun `executePipeline clears pipeline stream on collector cancellation before terminal state`() =
        runTest {
            val chatId = "chat1"

            val collectJob =
                launch {
                    executor.executePipeline(chatId, "Hello").toList(mutableListOf())
                }
            advanceUntilIdle()

            // Emit a non-terminal state
            inferenceEventBus.emitPipelineState(chatId, MessageGenerationState.Processing(ModelType.DRAFT_ONE))
            advanceUntilIdle()

            // Cancel the collector before any terminal state
            collectJob.cancel()
            advanceUntilIdle()

            // After cancellation, the pipeline stream should be cleared
            assertFalse(
                inferenceEventBus.hasPipelineStream(chatId),
                "Pipeline stream must be cleared when collector cancels before terminal state",
            )
        }

    @Test
    fun `executePipeline clears pipeline stream when startService throws exception`() =
        runTest {
            val chatId = "chat1"

            serviceStarter.startFailure = IllegalStateException("Service start failed")

            val thrown =
                runCatching {
                    executor.executePipeline(chatId, "Hello").toList()
                }.exceptionOrNull()
            assertTrue(thrown is IllegalStateException)
            assertEquals("Service start failed", thrown?.message)

            assertFalse(inferenceEventBus.hasPipelineStream(chatId), "Pipeline stream must be cleared when startService throws")
        }
}
