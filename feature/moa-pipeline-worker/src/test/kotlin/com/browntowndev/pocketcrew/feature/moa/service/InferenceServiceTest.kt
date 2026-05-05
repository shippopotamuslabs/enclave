package com.browntowndev.pocketcrew.feature.moa.service

import android.app.NotificationManager
import android.content.Intent
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServiceTest {

    private lateinit var service: InferenceService
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private val inferenceFactory = mockk<InferenceFactoryPort>()
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val inferenceEventBus = mockk<InferenceEventBus>(relaxed = true)
    private val llmInferenceService = mockk<LlmInferencePort>()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val serviceCompletion = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.Default } returns testDispatcher
        Dispatchers.setMain(testDispatcher)

        service = spyk(InferenceService())
        
        // Inject mocks
        service.notificationManager = notificationManager
        service.logger = logger
        val lazyFactory = mockk<dagger.Lazy<InferenceFactoryPort>>()
        every { lazyFactory.get() } returns inferenceFactory
        service.inferenceFactoryProvider = lazyFactory
        service.activeModelProvider = activeModelProvider
        service.inferenceEventBus = inferenceEventBus

        // Mock active model config
        val mockActiveConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration>(relaxed = true)
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns mockActiveConfig

        // Stub Service methods
        every { service.startForeground(any(), any(), any()) } returns Unit
        every { service.stopForeground(any<Int>()) } returns Unit
        every { service.stopSelf() } answers { 
            serviceCompletion.complete(Unit) 
        }
        every { service.stopSelf(any()) } answers { 
            serviceCompletion.complete(Unit) 
        }
        
        // Mock internal methods to avoid Android framework calls
        every { service.createCancelPendingIntent() } returns mockk(relaxed = true)
        every { service.createProgressNotification(any(), any(), any()) } returns mockk(relaxed = true)

        // Default inference behavior
        coEvery { 
            inferenceFactory.withInferenceService<Any?>(any(), any()) 
        } coAnswers {
            val block = secondArg<suspend (LlmInferencePort) -> Any?>()
            block.invoke(llmInferenceService)
        }
        
        every { 
            llmInferenceService.sendPrompt(any(), options = any(), closeConversation = any()) 
        } returns flowOf(InferenceEvent.Finished(ModelType.FAST))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `ACTION_START executes full pipeline`() = runTest(testDispatcher) {
        // Given
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns InferenceService.ACTION_START
        every { intent.getStringExtra(InferenceService.EXTRA_CHAT_ID) } returns "chat-123"
        every { intent.getStringExtra(InferenceService.EXTRA_USER_MESSAGE) } returns "hello"

        // When
        service.onStartCommand(intent, 0, 1)

        // Wait for completion
        withTimeout(15000) {
            serviceCompletion.await()
        }

        // Unmock static dispatchers before verification to avoid confusion in MockK
        unmockkStatic(Dispatchers::class)

        // Then
        coVerify(exactly = 1) { inferenceFactory.withInferenceService(ModelType.DRAFT_ONE, any()) }
        coVerify(exactly = 1) { inferenceFactory.withInferenceService(ModelType.DRAFT_TWO, any()) }
        coVerify(exactly = 1) { inferenceFactory.withInferenceService(ModelType.MAIN, any()) }
        coVerify(exactly = 1) { inferenceFactory.withInferenceService(ModelType.FINAL_SYNTHESIS, any()) }
        
        coVerify { inferenceEventBus.emitPipelineState("chat-123", any()) }
        verify { service.startForeground(any(), any(), any()) }
    }

    @Test
    fun `ACTION_STOP cancels pipeline`() = runTest(testDispatcher) {
        // Given
        val intentStop = mockk<Intent>(relaxed = true)
        every { intentStop.action } returns InferenceService.ACTION_STOP

        // When
        service.onStartCommand(intentStop, 0, 1)

        // Then
        verify { service.stopForeground(any<Int>()) }
        verify { service.stopSelf() }
    }
}
