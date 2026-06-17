package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("InferenceOrchestrator session sync")
class InferenceOrchestratorSessionTest {
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var orchestrator: InferenceOrchestrator

    @BeforeEach
    fun setUp() {
        savedStateHandle = SavedStateHandle()
        val settingsUseCases = mockk<SettingsUseCases>()
        every { settingsUseCases.getSettings() } returns
            MutableStateFlow(
                com.browntowndev.enclave.domain.port.repository
                    .SettingsData(),
            )
        orchestrator =
            InferenceOrchestrator(
                chatUseCases = mockk(relaxed = true),
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = mockk(relaxed = true),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = mockk(relaxed = true),
                errorHandler = mockk<ViewModelErrorHandler>(relaxed = true),
                savedStateHandle = savedStateHandle,
                pipelineExecutor = mockk(relaxed = true),
                moderatePromptUseCase = mockk<ModeratePromptUseCase>(relaxed = true),
                loggingPort = mockk(relaxed = true),
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
    }

    @Test
    @DisplayName("SC-PERF-01 — syncActiveChatFromNavigation sets currentChatId to nav id")
    fun scPerf01_syncSetsCurrentChatId() {
        orchestrator.seedCurrentChatId(ChatId("chat-a"))

        orchestrator.syncActiveChatFromNavigation(ChatId("chat-b"))

        assertEquals(ChatId("chat-b"), orchestrator.currentChatId.value)
    }

    @Test
    @DisplayName("SC-PERF-02 — syncActiveChatFromNavigation does not overwrite SavedStateHandle chatId")
    fun scPerf02_syncDoesNotOverwriteSavedStateHandle() {
        savedStateHandle["chatId"] = "nav-owned-chat"

        orchestrator.syncActiveChatFromNavigation(ChatId("chat-b"))

        assertEquals("nav-owned-chat", savedStateHandle.get<String>("chatId"))
        assertEquals(ChatId("chat-b"), orchestrator.currentChatId.value)
    }

    @Test
    @DisplayName("SC-PERF-03 — acknowledgedTurnKeys pruned to active chat only")
    fun scPerf03_acknowledgedTurnKeysPrunedToActiveChat() {
        val keyA = ActiveChatTurnKey(ChatId("chat-a"), MessageId("asst-a"))
        val keyB = ActiveChatTurnKey(ChatId("chat-b"), MessageId("asst-b"))
        orchestrator.seedAcknowledgedTurnKeys(setOf(keyA, keyB))

        orchestrator.syncActiveChatFromNavigation(ChatId("chat-b"))

        assertEquals(setOf(keyB), orchestrator.acknowledgedTurnKeys.value)
    }

    @Test
    @DisplayName("SC-PERF-03 — requestedTurnKey cleared when foreign chat")
    fun scPerf03_requestedTurnKeyClearedForForeignChat() {
        val foreignKey = ActiveChatTurnKey(ChatId("chat-a"), MessageId("asst-a"))
        orchestrator.seedRequestedTurnKey(foreignKey)

        orchestrator.syncActiveChatFromNavigation(ChatId("chat-b"))

        assertNull(orchestrator.requestedTurnKey.value)
    }

    @Test
    @DisplayName("SC-PERF-04 — stale orchestrator chat overridden by navigation sync")
    fun scPerf04_staleChatOverriddenByNavSync() {
        orchestrator.seedCurrentChatId(ChatId("chat-stale"))

        orchestrator.syncActiveChatFromNavigation(ChatId("chat-nav"))

        val resolved = orchestrator.currentChatId.value ?: ChatId("chat-nav")
        assertEquals(ChatId("chat-nav"), resolved)
    }

    @Test
    @DisplayName("SC-PERF-05 — sync with null navChatId is a no-op and preserves navigation chatId")
    fun scPerf05_syncNullIsNoOpAndPreservesSavedState() {
        orchestrator.seedCurrentChatId(ChatId("chat-a"))
        orchestrator.seedAcknowledgedTurnKeys(
            setOf(ActiveChatTurnKey(ChatId("chat-a"), MessageId("asst-a"))),
        )
        savedStateHandle["chatId"] = "existing-from-nav"

        orchestrator.syncActiveChatFromNavigation(null)

        assertEquals(ChatId("chat-a"), orchestrator.currentChatId.value)
        assertEquals("existing-from-nav", savedStateHandle.get<String>("chatId"))
        assertEquals(
            setOf(ActiveChatTurnKey(ChatId("chat-a"), MessageId("asst-a"))),
            orchestrator.acknowledgedTurnKeys.value,
        )
    }

    @Test
    @DisplayName("createNewChat clears orchestrator session and SavedStateHandle chatId")
    fun createNewChatClearsSession() {
        orchestrator.seedCurrentChatId(ChatId("chat-a"))
        savedStateHandle["chatId"] = "chat-a"

        orchestrator.createNewChat()

        assertNull(orchestrator.currentChatId.value)
        assertNull(savedStateHandle.get<String>("chatId"))
    }

    private fun InferenceOrchestrator.seedCurrentChatId(chatId: ChatId?) {
        setPrivateStateFlow("_currentChatId", chatId)
    }

    private fun InferenceOrchestrator.seedAcknowledgedTurnKeys(keys: Set<ActiveChatTurnKey>) {
        setPrivateStateFlow("_acknowledgedTurnKeys", keys)
    }

    private fun InferenceOrchestrator.seedRequestedTurnKey(key: ActiveChatTurnKey?) {
        setPrivateStateFlow("_requestedTurnKey", key)
    }

    private fun <T> InferenceOrchestrator.setPrivateStateFlow(
        fieldName: String,
        value: T,
    ) {
        val field = InferenceOrchestrator::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<T>
        flow.value = value
    }
}
