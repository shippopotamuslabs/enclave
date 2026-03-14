package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GenerateChatResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * REAL unit tests for ChatViewModel Crew mode behavior.
 * Tests the ACTUAL production ChatViewModel with properly mocked dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCrewModeRealTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @MockK lateinit var chatUseCases: ChatUseCases
    @MockK lateinit var settingsUseCases: SettingsUseCases
    @MockK lateinit var modelRegistry: ModelRegistryPort
    @MockK lateinit var inferenceLockManager: InferenceLockManager
    @MockK lateinit var savedStateHandle: SavedStateHandle
    @MockK(relaxed = true) lateinit var context: Context

    private lateinit var viewModel: ChatViewModel

    private val generationFlow = MutableSharedFlow<MessageGenerationState>(extraBufferCapacity = 64)

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        // Minimal defaults
        every { settingsUseCases.getSettings() } returns flowOf(SettingsData(hapticPress = false, hapticResponse = false))
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockRegisteredModel("Test Model")
        every { savedStateHandle.get<Long>("chatId") } returns null

        // Create REAL production ViewModel
        viewModel = ChatViewModel(
            context = context,
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            modelRegistry = modelRegistry,
            inferenceLockManager = inferenceLockManager
        )

        // Start a background collector so that uiState (which uses WhileSubscribed) becomes active
        val job = testScope.launch {
            viewModel.uiState.collect {}
        }

        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ========================================================================
    // Test: FINAL step thinking is stored in completedSteps
    // ========================================================================

    @Test
    fun `Crew mode - FINAL StepCompleted with thinking is stored in completedSteps`() {
        // Given: 3 steps completed
        sendMessage(Mode.CREW)

        listOf(
            MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 30, thinkingSteps = listOf("T1"), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE),
            MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 25, thinkingSteps = listOf("T2"), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO),
            MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 45, thinkingSteps = listOf("T3"), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)
        ).forEach { state ->
            testScope.launch { generationFlow.emit(state) }
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // When: FINAL StepCompleted arrives WITH thinking
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Final Output",
            thinkingDurationSeconds = 300,  // 5 minutes
            thinkingSteps = listOf("Final thinking step 1", "Final thinking step 2"),
            modelDisplayName = "Final Model",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.FINAL
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: FINAL step with thinking is in completedSteps
        val afterMsg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        val completedSteps = afterMsg?.completedSteps

        assertNotNull(completedSteps)
        assertEquals(4, completedSteps!!.size)

        // FINAL should be the 4th step
        val finalStep = completedSteps.find { it.stepType == PipelineStep.FINAL }
        assertNotNull(finalStep)
        assertEquals(300, finalStep!!.thinkingDurationSeconds)
        assertEquals(2, finalStep.thinkingSteps.size)
        assertTrue(finalStep.thinkingComplete)  // This is what determines if "Thought For" shows
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockRegisteredModel(displayName: String): ModelConfiguration {
        return ModelConfiguration(
            modelType = ModelType.DRAFT_ONE,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "test.bin",
                localFileName = "test.bin",
                displayName = displayName,
                sha256 = "abc",
                sizeInBytes = 1000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                repetitionPenalty = 1.0,
                maxTokens = 2048,
                contextWindow = 4096
            ),
            persona = ModelConfiguration.Persona("You are helpful")
        )
    }

    private fun sendMessage(mode: Mode) {
        // Mock processPrompt
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L
        )

        // Mock generateChatResponse to return our flow
        every {
            chatUseCases.generateChatResponse(
                any<String>(),
                any<Long>(),
                any<Long>(),
                any<Long>(),
                mode
            )
        } returns generationFlow

        viewModel.onModeChange(mode)
        viewModel.onInputChange("test prompt")
        viewModel.onSendMessage()

        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ========================================================================
    // Test: Crew mode - ThinkingLive
    // ========================================================================

    @Test
    fun `Crew mode - ThinkingLive sets responseState to THINKING`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)

        // When: ThinkingLive arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Thinking"),
            modelType = ModelType.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is THINKING
        assertEquals(ResponseState.THINKING, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: Crew mode - GeneratingText (non-FINAL)
    // ========================================================================

    @Test
    fun `Crew mode - GeneratingText for Draft One sets responseState to GENERATING`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Emit ThinkingLive first
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(listOf("Thinking"), ModelType.DRAFT_ONE)) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.THINKING, viewModel.uiState.value.responseState)

        // When: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft one text")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is GENERATING
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Crew mode - GeneratingText for non-FINAL does NOT update message content`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Emit ThinkingLive then GeneratingText
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(listOf("Thinking"), ModelType.DRAFT_ONE)) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft one output")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content should be empty
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("", assistantMessage?.content ?: "")
    }

    // ========================================================================
    // Test: Crew mode - StepCompleted
    // ========================================================================

    @Test
    fun `Crew mode - StepCompleted adds to completedSteps`() {
        // Given: Send Crew message and generate text
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft one")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Draft One Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Brainstorming"),
            modelDisplayName = "Draft One Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: completedSteps has Draft One
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(1, assistantMessage?.completedSteps?.size ?: 0)
        assertEquals(PipelineStep.DRAFT_ONE, assistantMessage?.completedSteps?.get(0)?.stepType)
    }

    @Test
    fun `Crew mode - StepCompleted for non-FINAL sets responseState to PROCESSING`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft one")) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)

        // When: StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Draft One Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking"),
            modelDisplayName = "Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is PROCESSING
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: Crew mode - Draft Two (after Draft One complete)
    // ========================================================================

    @Test
    fun `Crew mode - Draft Two generating shows GENERATING`() {
        // Given: Draft One completed
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("D1")) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("T1"),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)

        // When: Draft Two generates
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft two")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is GENERATING
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Crew mode - Draft Two content stays empty`() {
        // Given: Draft One completed
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("T1"),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Draft Two generates
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("D2")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content is empty
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("", assistantMessage?.content ?: "")
    }

    // ========================================================================
    // Test: Crew mode - FINAL step
    // ========================================================================

    @Test
    fun `Crew mode - FINAL generating sets responseState to NONE`() {
        // Given: 3 steps completed
        sendMessage(Mode.CREW)

        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 30, thinkingSteps = listOf("T1"), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE)) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 25, thinkingSteps = listOf("T2"), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO)) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 45, thinkingSteps = listOf("T3"), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: FINAL generates
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Final response")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is NONE
        assertEquals(ResponseState.NONE, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Crew mode - FINAL generating updates message content`() {
        // Given: 3 steps completed
        sendMessage(Mode.CREW)

        listOf(
            MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 30, thinkingSteps = listOf("T1"), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE),
            MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 25, thinkingSteps = listOf("T2"), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO),
            MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 45, thinkingSteps = listOf("T3"), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)
        ).forEach { state ->
            testScope.launch { generationFlow.emit(state) }
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // When: FINAL generates
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Final response content")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content has final text
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Final response content", assistantMessage?.content)
    }

    // ========================================================================
    // Test: Non-Crew mode (Fast)
    // ========================================================================

    @Test
    fun `Fast mode - GeneratingText sets responseState to PROCESSING`() {
        // Given: Send FAST message
        sendMessage(Mode.FAST)

        // When: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Fast response")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is PROCESSING
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Fast mode - GeneratingText updates message content`() {
        // Given: Send FAST message
        sendMessage(Mode.FAST)

        // When: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Fast response text")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content is updated
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Fast response text", assistantMessage?.content)
    }

    // ========================================================================
    // Test: Non-thinking model scenarios (no ThinkingLive)
    // ========================================================================

    @Test
    fun `Crew mode - Direct GeneratingText without ThinkingLive shows GENERATING`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // When: GeneratingText arrives directly (no ThinkingLive) - non-thinking model
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Direct output")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is GENERATING
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Crew mode - StepCompleted with empty thinkingSteps stores empty list`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Output")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted with NO thinking steps (non-thinking model)
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Draft One Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = emptyList(),  // Empty - non-thinking model
            modelDisplayName = "Draft One Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: completedSteps has empty thinkingSteps
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(1, assistantMessage?.completedSteps?.size ?: 0)
        assertEquals(emptyList<String>(), assistantMessage?.completedSteps?.get(0)?.thinkingSteps)
    }

    // ========================================================================
    // Test: StepCompleted stores all data correctly
    // ========================================================================

    @Test
    fun `Crew mode - StepCompleted stores stepOutput correctly`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft one")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Expected Output Text",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Step 1", "Step 2"),
            modelDisplayName = "Custom Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: stepOutput is stored correctly
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Expected Output Text", assistantMessage?.completedSteps?.get(0)?.stepOutput)
    }

    @Test
    fun `Crew mode - StepCompleted stores thinkingDurationSeconds correctly`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 99,
            thinkingSteps = emptyList(),
            modelDisplayName = "Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: duration is stored
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(99, assistantMessage?.completedSteps?.get(0)?.thinkingDurationSeconds)
    }

    @Test
    fun `Crew mode - StepCompleted stores modelDisplayName correctly`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted arrives
        // Note: ViewModel gets modelDisplayName from ModelRegistry using modelType,
        // not from the generationState. Our mock returns "Test Model"
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = emptyList(),
            modelDisplayName = "Ignored - ViewModel uses registry",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: modelDisplayName is stored (from mock registry, not from state)
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Test Model", assistantMessage?.completedSteps?.get(0)?.modelDisplayName)
    }

    // ========================================================================
    // Test: Multiple steps (Draft Two)
    // ========================================================================

    @Test
    fun `Crew mode - Draft Two StepCompleted adds second entry to completedSteps`() {
        // Given: Draft One completed
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1 Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("T1"),
            modelDisplayName = "M1",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Draft Two completes
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D2 Output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("T2"),
            modelDisplayName = "M2",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: completedSteps has 2 entries
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(2, assistantMessage?.completedSteps?.size ?: 0)
        assertEquals(PipelineStep.DRAFT_ONE, assistantMessage?.completedSteps?.get(0)?.stepType)
        assertEquals(PipelineStep.DRAFT_TWO, assistantMessage?.completedSteps?.get(1)?.stepType)
    }

    @Test
    fun `Crew mode - Draft Two StepCompleted stores its own output`() {
        // Given: Draft One completed
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1 Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = emptyList(),
            modelDisplayName = "M1",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Draft Two completes
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D2 Specific Output",
            thinkingDurationSeconds = 25,
            thinkingSteps = emptyList(),
            modelDisplayName = "M2",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Draft Two's output is stored at index 1
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("D1 Output", assistantMessage?.completedSteps?.get(0)?.stepOutput)
        assertEquals("D2 Specific Output", assistantMessage?.completedSteps?.get(1)?.stepOutput)
    }

    // ========================================================================
    // Test: FINAL StepCompleted behavior
    // ========================================================================

    @Test
    fun `Crew mode - FINAL StepCompleted adds output to content`() {
        // Given: 3 steps completed
        sendMessage(Mode.CREW)

        listOf(
            MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 30, thinkingSteps = listOf("T1"), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE),
            MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 25, thinkingSteps = listOf("T2"), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO),
            MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 45, thinkingSteps = listOf("T3"), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)
        ).forEach { state ->
            testScope.launch { generationFlow.emit(state) }
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Verify content is empty before FINAL StepCompleted
        val beforeMsg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("", beforeMsg?.content)

        // When: FINAL StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Final Output Text",
            thinkingDurationSeconds = 20,
            thinkingSteps = listOf("Final Thought"),
            modelDisplayName = "Final Model",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.FINAL
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content has FINAL output
        val afterMsg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Final Output Text", afterMsg?.content)
    }

    @Test
    fun `Crew mode - FINAL StepCompleted sets responseState to NONE`() {
        // Given: 3 steps completed
        sendMessage(Mode.CREW)

        listOf(
            MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 30, thinkingSteps = listOf("T1"), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE),
            MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 25, thinkingSteps = listOf("T2"), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO),
            MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 45, thinkingSteps = listOf("T3"), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)
        ).forEach { state ->
            testScope.launch { generationFlow.emit(state) }
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // When: FINAL StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Final",
            thinkingDurationSeconds = 20,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.FINAL
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is NONE
        assertEquals(ResponseState.NONE, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: ThinkingLive clears thinkingSteps
    // ========================================================================

    @Test
    fun `Crew mode - GeneratingText preserves thinkingSteps for Thought For display`() {
        // Given: ThinkingLive was emitted
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Step 1", "Step 2", "Step 3"),
            modelType = ModelType.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify thinkingSteps is populated
        assertEquals(3, viewModel.uiState.value.thinkingSteps.size)

        // When: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Some text")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingSteps is PRESERVED in Crew mode (so "Thought For Xs" can be displayed)
        // It will be cleared when StepCompleted arrives
        assertEquals("thinkingSteps should be preserved for Thought For display", 3, viewModel.uiState.value.thinkingSteps.size)
    }

    @Test
    fun `Crew mode - GeneratingText clears thinkingStartTime`() {
        // Given: ThinkingLive was emitted
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Thinking"),
            modelType = ModelType.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify thinkingStartTime is set (greater than 0)
        assertTrue(viewModel.uiState.value.thinkingStartTime > 0)

        // When: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Text")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingStartTime is cleared
        assertEquals(0L, viewModel.uiState.value.thinkingStartTime)
    }

    // ========================================================================
    // Test: Error states
    // ========================================================================

    @Test
    fun `Crew mode - Blocked state sets responseState to NONE`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // When: Blocked arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.Blocked("Inference blocked")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is NONE
        assertEquals(ResponseState.NONE, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Crew mode - Failed state shows error in message content`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // When: Failed arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.Failed(IllegalStateException("Test error"))) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content shows error
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertTrue(assistantMessage?.content?.contains("Error") == true)
    }

    // ========================================================================
    // Test: Fast mode vs Crew mode differences
    // ========================================================================

    @Test
    fun `Fast mode - ThinkingLive sets responseState to THINKING`() {
        // Given: Send FAST message
        sendMessage(Mode.FAST)

        // When: ThinkingLive arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Thinking"),
            modelType = ModelType.FAST
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is THINKING
        assertEquals(ResponseState.THINKING, viewModel.uiState.value.responseState)
    }

    @Test
    fun `Fast mode with thinking steps - stores thinkingData at message level`() {
        // Given: Send FAST message with thinking steps (could be a thinking model)
        sendMessage(Mode.FAST)
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Step 1", "Step 2"),
            modelType = ModelType.FAST
        )) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Response")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Finished arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.Finished) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: message HAS thinkingData because thinking steps were provided
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertTrue(assistantMessage?.thinkingData != null)
    }

    // ========================================================================
    // Test: StepCompleted with thinking vs without thinking (Thought For display)
    // ========================================================================

    @Test
    fun `Crew mode - StepCompleted with thinking has thinkingSteps stored`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.ThinkingLive(
            steps = listOf("Brainstorming", "Planning"),
            modelType = ModelType.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // When: StepCompleted with thinking
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Brainstorming", "Planning"),
            modelDisplayName = "Model",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingSteps is stored in completedSteps
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(2, assistantMessage?.completedSteps?.get(0)?.thinkingSteps?.size)
    }

    // ========================================================================
    // Test: GeneratingText followed by StepCompleted (the bug fix)
    // This is the critical sequence that was causing "Generating..." to stick
    // ========================================================================

    @Test
    fun `Crew mode - StepCompleted sets PROCESSING even after GeneratingText`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // First, generate some text (simulating Draft Two generating)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("First chunk")) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)

        // More text generated
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Second chunk")) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)

        // Step completes - this should set responseState to PROCESSING
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Full output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Think 1"),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is PROCESSING (NOT GENERATING - this was the bug!)
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: Multiple GeneratingText events should NOT accumulate for non-FINAL
    // This catches the bug where content was being updated during non-FINAL steps
    // ========================================================================

    @Test
    fun `Crew mode - Multiple GeneratingText events do NOT accumulate content for non-FINAL`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Multiple chunks of text generated during Draft One
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("First chunk ")) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Second chunk ")) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Third chunk")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content should still be EMPTY (not accumulated)
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Content should be empty for non-FINAL step", "", assistantMessage?.content ?: "")
    }

    @Test
    fun `Crew mode - GeneratingText without ThinkingLive still shows GENERATING`() {
        // Given: Send Crew message (initial state is PROCESSING)
        sendMessage(Mode.CREW)
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)

        // When: GeneratingText arrives directly (no ThinkingLive - non-thinking model or model skips thinking)
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Direct output")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be GENERATING
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: Full step flow - Processing -> Generating -> StepCompleted -> Processing
    // This catches the bug where StepCompleted was overwritten by GeneratingText
    // ========================================================================

    @Test
    fun `Crew mode - Full step flow Processing to Generating to StepCompleted to Processing`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Initial state: PROCESSING
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)

        // Step 1: GeneratingText arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Draft text")) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)

        // Step 2: StepCompleted arrives
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // CRITICAL: Should be PROCESSING, not GENERATING
        assertEquals("StepCompleted should set PROCESSING", ResponseState.PROCESSING, viewModel.uiState.value.responseState)
    }

    // ========================================================================
    // Test: Non-thinking model flow (no ThinkingLive at all)
    // ========================================================================

    @Test
    fun `Crew mode - Non-thinking model generates text directly without ThinkingLive`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Model skips thinking and goes directly to generating
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Output without thinking")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState is GENERATING
        assertEquals(ResponseState.GENERATING, viewModel.uiState.value.responseState)

        // And: content is still empty (non-FINAL)
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("", assistantMessage?.content ?: "")

        // Step completes
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Final output",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),  // Empty - non-thinking model
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: PROCESSING, not GENERATING
        assertEquals(ResponseState.PROCESSING, viewModel.uiState.value.responseState)

        // And: step is recorded with empty thinking
        val completedMsg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(1, completedMsg?.completedSteps?.size ?: 0)
        assertEquals(emptyList<String>(), completedMsg?.completedSteps?.get(0)?.thinkingSteps)
    }

    // ========================================================================
    // Test: Completed steps count verification
    // ========================================================================

    @Test
    fun `Crew mode - completedSteps has correct count after each step`() {
        // Given: Send Crew message
        sendMessage(Mode.CREW)

        // Complete Draft One
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        var msg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("After D1: 1 step", 1, msg?.completedSteps?.size ?: 0)

        // Complete Draft Two
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D2",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        msg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("After D2: 2 steps", 2, msg?.completedSteps?.size ?: 0)

        // Complete Synthesis
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "Synth",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        msg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("After Synth: 3 steps", 3, msg?.completedSteps?.size ?: 0)
    }

    // ========================================================================
    // Test: FINAL step content accumulation
    // ========================================================================

    @Test
    fun `Crew mode - FINAL GeneratingText accumulates content`() {
        // Given: 3 steps completed (now on FINAL)
        sendMessage(Mode.CREW)

        listOf(
            MessageGenerationState.StepCompleted(stepOutput = "D1", thinkingDurationSeconds = 10, thinkingSteps = emptyList(), modelDisplayName = "M", modelType = ModelType.DRAFT_ONE, stepType = PipelineStep.DRAFT_ONE),
            MessageGenerationState.StepCompleted(stepOutput = "D2", thinkingDurationSeconds = 10, thinkingSteps = emptyList(), modelDisplayName = "M", modelType = ModelType.DRAFT_TWO, stepType = PipelineStep.DRAFT_TWO),
            MessageGenerationState.StepCompleted(stepOutput = "Synth", thinkingDurationSeconds = 10, thinkingSteps = emptyList(), modelDisplayName = "M", modelType = ModelType.MAIN, stepType = PipelineStep.SYNTHESIS)
        ).forEach { state ->
            testScope.launch { generationFlow.emit(state) }
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // When: Multiple GeneratingText events for FINAL step
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("Hello ")) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("world!")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: content ACCUMULATES for FINAL step
        val assistantMessage = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Hello world!", assistantMessage?.content)
    }

    // ========================================================================
    // Test: Flow continues after StepCompleted (would catch close() bug)
    // ========================================================================

    @Test
    fun `Crew mode - Flow continues after StepCompleted to receive next step events`() {
        // Given: Draft One completes
        sendMessage(Mode.CREW)
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D1",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify Draft One is recorded
        var msg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals(1, msg?.completedSteps?.size ?: 0)

        // When: Draft Two generates and completes
        testScope.launch { generationFlow.emit(MessageGenerationState.GeneratingText("D2")) }
        testDispatcher.scheduler.advanceUntilIdle()
        testScope.launch { generationFlow.emit(MessageGenerationState.StepCompleted(
            stepOutput = "D2",
            thinkingDurationSeconds = 10,
            thinkingSteps = emptyList(),
            modelDisplayName = "M",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Draft Two should also be recorded (flow continued after Draft One StepCompleted)
        msg = viewModel.uiState.value.messages.lastOrNull { it.role == MessageRole.Assistant }
        assertEquals("Flow should continue after StepCompleted", 2, msg?.completedSteps?.size ?: 0)
    }
}
