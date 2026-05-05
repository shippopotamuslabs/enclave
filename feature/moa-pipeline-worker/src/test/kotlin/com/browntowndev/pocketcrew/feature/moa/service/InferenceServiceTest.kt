package com.browntowndev.pocketcrew.feature.moa.service

import android.app.NotificationManager
import android.content.Intent
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceServiceTest {

    private lateinit var service: InferenceService
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private val inferenceFactory = mockk<InferenceFactoryPort>()
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val inferenceEventBus = mockk<InferenceEventBus>(relaxed = true)
    private val llmInferenceService = mockk<LlmInferencePort>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private var serviceCompletion = CompletableDeferred<Unit>()

    private val invokedModelTypes = mutableListOf<ModelType>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        serviceCompletion = CompletableDeferred<Unit>()
        invokedModelTypes.clear()

        service = spyk(InferenceService())

        // Replace serviceScope with test dispatcher so pipeline runs synchronously
        service.serviceScope = CoroutineScope(testDispatcher + SupervisorJob())

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

        // Default inference behavior - track model types via side effect
        coEvery {
            inferenceFactory.withInferenceService(any<ModelType>(), any<suspend (LlmInferencePort) -> Unit>())
        } coAnswers {
            invokedModelTypes.add(firstArg())
            val block = args[1] as suspend (LlmInferencePort) -> Unit
            block(llmInferenceService)
        }

        every {
            llmInferenceService.sendPrompt(any(), options = any(), closeConversation = any())
        } returns flowOf(InferenceEvent.Finished(ModelType.FAST))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("ACTION_START executes full pipeline with all model types")
    fun actionStartExecutesFullPipeline() = runTest(testDispatcher) {
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

        // Then - verify all pipeline steps were invoked
        assertTrue(invokedModelTypes.contains(ModelType.DRAFT_ONE),
            "Expected DRAFT_ONE in $invokedModelTypes")
        assertTrue(invokedModelTypes.contains(ModelType.DRAFT_TWO),
            "Expected DRAFT_TWO in $invokedModelTypes")
        assertTrue(invokedModelTypes.contains(ModelType.MAIN),
            "Expected MAIN in $invokedModelTypes")
        assertTrue(invokedModelTypes.contains(ModelType.FINAL_SYNTHESIS),
            "Expected FINAL_SYNTHESIS in $invokedModelTypes")

        verify { service.startForeground(any(), any(), any()) }
    }

    @Test
    @DisplayName("ACTION_STOP cancels pipeline and stops service")
    fun actionStopCancelsPipeline() = runTest(testDispatcher) {
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
