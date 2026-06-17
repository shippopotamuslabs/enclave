package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceOrchestratorSendResponseModeTest {
    private val testDispatcher = StandardTestDispatcher()

    @RegisterExtension
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var chatUseCases: ChatUseCases
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var errorHandler: RecordingErrorHandler
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
        errorHandler = RecordingErrorHandler()
        chatUseCases = mockk(relaxed = true)
        settingsUseCases = mockk()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        coEvery { chatUseCases.submitPrompt(any()) } returns
            UserPromptResult(
                userMessageId = MessageId("user-1"),
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 100L,
            )

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = mockk(relaxed = true),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = mockk(relaxed = true),
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
                pipelineExecutor = mockk(relaxed = true),
                moderatePromptUseCase = mockk(relaxed = true),
                loggingPort = mockk(relaxed = true),
                applicationScope = applicationScope,
            )
    }

    @Test
    @DisplayName("S06 — generateChatResponse receives ChatResponseMode.Other with override")
    fun s06_overrideThreadedAsOtherResponseMode() =
        runTest(testDispatcher) {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            val responseMode = ChatResponseMode.Other(modelOverride = override)

            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = responseMode,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify {
                chatUseCases.generateChatResponse(
                    prompt = "Hi",
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = responseMode,
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    @Test
    @DisplayName("S06b — FAST mode produces ChatResponseMode.Fast, never carries override")
    fun s06b_fastModeProducesFastResponseMode() =
        runTest(testDispatcher) {
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify {
                chatUseCases.generateChatResponse(
                    prompt = "Hi",
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = ChatResponseMode.Fast,
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    private class RecordingErrorHandler : ViewModelErrorHandler {
        private val _errorEvents = MutableSharedFlow<String>()
        override val errorEvents: SharedFlow<String> = _errorEvents
        val handledErrors = mutableListOf<Throwable>()

        override fun handleError(
            tag: String,
            message: String,
            throwable: Throwable,
            userMessage: String,
        ) {
            handledErrors += throwable
        }

        override fun coroutineExceptionHandler(
            tag: String,
            message: String,
            userMessage: String,
        ): CoroutineExceptionHandler =
            CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
                handleError(tag, message, throwable, userMessage)
            }
    }
}
