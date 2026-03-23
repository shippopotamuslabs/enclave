package com.browntowndev.pocketcrew.feature.moa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.PipelineStateRepository
import com.browntowndev.pocketcrew.feature.moa.service.InferenceService
import com.browntowndev.pocketcrew.feature.moa.service.InferenceServiceStarter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServicePipelineExecutorTest {

    private lateinit var context: Context
    private lateinit var serviceStarter: InferenceServiceStarter
    private lateinit var pipelineStateRepository: PipelineStateRepository
    private lateinit var loggingPort: LoggingPort

    private lateinit var executor: InferenceServicePipelineExecutor

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        serviceStarter = mockk(relaxed = true)
        pipelineStateRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)

        executor = InferenceServicePipelineExecutor(
            context = context,
            serviceStarter = serviceStarter,
            pipelineStateRepository = pipelineStateRepository,
            loggingPort = loggingPort
        )
    }

    @Test
    fun `executePipeline forwards thinking chunks with newlines and spaces`() = runTest {
        io.mockk.mockkObject(PipelineState.Companion)
        val mockState = mockk<PipelineState>()
        every { mockState.toJson() } returns "{}"
        every { mockState.currentStep } returns PipelineStep.DRAFT_ONE
        every { PipelineState.createInitial(any(), any()) } returns mockState

        val receiverSlot = slot<BroadcastReceiver>()
        every {
            context.registerReceiver(
                capture(receiverSlot),
                any<IntentFilter>(),
                eq(Context.RECEIVER_NOT_EXPORTED)
            )
        } returns null

        val events = mutableListOf<MessageGenerationState>()

        val job = launch {
            executor.executePipeline("chat1", "Hello").collect { state ->
                events.add(state)
            }
        }

        advanceUntilIdle()

        val receiver = receiverSlot.captured

        // Simulate receiving a thinking chunk with only a newline
        val intentNewline = mockk<Intent>()
        every { intentNewline.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentNewline.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns "\n"
        every { intentNewline.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns null
        every { intentNewline.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentNewline)

        // Simulate receiving a thinking chunk with spaces
        val intentSpaces = mockk<Intent>()
        every { intentSpaces.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns "   "
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns null
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentSpaces)

        // Empty strings should be filtered out
        val intentEmpty = mockk<Intent>()
        every { intentEmpty.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns ""
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns null
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentEmpty)

        // Finish step to close flow
        val intentComplete = mockk<Intent>()
        every { intentComplete.action } returns InferenceService.BROADCAST_STEP_COMPLETED
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns "done"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_MODEL_DISPLAY_NAME) } returns "Main"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_TYPE) } returns PipelineStep.FINAL.name
        receiver.onReceive(context, intentComplete)

        job.join()

        // We expect the newline and space chunks to be kept
        val thinkingEvents = events.filterIsInstance<MessageGenerationState.ThinkingLive>()
        assertEquals(2, thinkingEvents.size)
        assertEquals("\n", thinkingEvents[0].thinkingChunk)
        assertEquals("   ", thinkingEvents[1].thinkingChunk)
        
        // We do not expect any empty chunk
        assertTrue(thinkingEvents.none { it.thinkingChunk == "" })
    }

    @Test
    fun `executePipeline forwards step output chunks with newlines and spaces`() = runTest {
        io.mockk.mockkObject(PipelineState.Companion)
        val mockState = mockk<PipelineState>()
        every { mockState.toJson() } returns "{}"
        every { mockState.currentStep } returns PipelineStep.DRAFT_ONE
        every { PipelineState.createInitial(any(), any()) } returns mockState

        val receiverSlot = slot<BroadcastReceiver>()
        every {
            context.registerReceiver(
                capture(receiverSlot),
                any<IntentFilter>(),
                eq(Context.RECEIVER_NOT_EXPORTED)
            )
        } returns null

        val events = mutableListOf<MessageGenerationState>()

        val job = launch {
            executor.executePipeline("chat1", "Hello").collect { state ->
                events.add(state)
            }
        }

        advanceUntilIdle()

        val receiver = receiverSlot.captured

        // Simulate receiving an output chunk with only a newline
        val intentNewline = mockk<Intent>()
        every { intentNewline.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentNewline.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns null
        every { intentNewline.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns "\n"
        every { intentNewline.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentNewline)

        // Simulate receiving an output chunk with spaces
        val intentSpaces = mockk<Intent>()
        every { intentSpaces.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns null
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns "   "
        every { intentSpaces.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentSpaces)

        // Empty strings should be filtered out
        val intentEmpty = mockk<Intent>()
        every { intentEmpty.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns null
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns ""
        every { intentEmpty.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentEmpty)

        // Finish step to close flow
        val intentComplete = mockk<Intent>()
        every { intentComplete.action } returns InferenceService.BROADCAST_STEP_COMPLETED
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns "done"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_MODEL_DISPLAY_NAME) } returns "Main"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_TYPE) } returns PipelineStep.FINAL.name
        receiver.onReceive(context, intentComplete)

        job.join()

        // We expect the newline and space chunks to be kept
        val outputEvents = events.filterIsInstance<MessageGenerationState.GeneratingText>()
        assertEquals(2, outputEvents.size)
        assertEquals("\n", outputEvents[0].textDelta)
        assertEquals("   ", outputEvents[1].textDelta)

        // We do not expect any empty chunk
        assertTrue(outputEvents.none { it.textDelta == "" })
    }

    @Test
    fun `resumeFromState forwards thinking chunks with newlines and spaces`() = runTest {
        io.mockk.mockkObject(PipelineState.Companion)
        val mockState = mockk<PipelineState>()
        every { mockState.toJson() } returns "{}"
        every { mockState.currentStep } returns PipelineStep.DRAFT_ONE
        
        // Mock the repository to return the state
        io.mockk.coEvery { pipelineStateRepository.getPipelineState(any()) } returns mockState

        val receiverSlot = slot<BroadcastReceiver>()
        every {
            context.registerReceiver(
                capture(receiverSlot),
                any<IntentFilter>(),
                eq(Context.RECEIVER_NOT_EXPORTED)
            )
        } returns null

        val events = mutableListOf<MessageGenerationState>()

        val job = launch {
            executor.resumeFromState("chat1", "pipeline1", {}, {}).collect { state ->
                events.add(state)
            }
        }

        advanceUntilIdle()

        val receiver = receiverSlot.captured

        // Simulate receiving a thinking chunk with only a newline
        val intentNewline = mockk<Intent>()
        every { intentNewline.action } returns InferenceService.BROADCAST_PROGRESS
        every { intentNewline.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK) } returns "\n"
        every { intentNewline.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns null
        every { intentNewline.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        receiver.onReceive(context, intentNewline)

        // Finish step to close flow
        val intentComplete = mockk<Intent>()
        every { intentComplete.action } returns InferenceService.BROADCAST_STEP_COMPLETED
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) } returns "done"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_MODEL_DISPLAY_NAME) } returns "Main"
        every { intentComplete.getStringExtra(InferenceService.EXTRA_MODEL_TYPE) } returns ModelType.MAIN.name
        every { intentComplete.getStringExtra(InferenceService.EXTRA_STEP_TYPE) } returns PipelineStep.FINAL.name
        receiver.onReceive(context, intentComplete)

        job.join()

        val thinkingEvents = events.filterIsInstance<MessageGenerationState.ThinkingLive>()
        assertEquals(1, thinkingEvents.size)
        assertEquals("\n", thinkingEvents[0].thinkingChunk)
    }
}