package com.browntowndev.enclave.feature.chat

import app.cash.turbine.test
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.feature.chat.delegates.ChatInputCoordinator
import io.mockk.mockk
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatInputCoordinatorOverrideTest {
    private lateinit var coordinator: ChatInputCoordinator

    @BeforeEach
    fun setUp() {
        coordinator =
            ChatInputCoordinator(
                stageImageAttachmentUseCase = mockk(relaxed = true),
                chatUseCases = mockk(relaxed = true),
                errorHandler = mockk(relaxed = true),
            )
    }

    @Test
    @DisplayName("S04 — Local override set: selectedModelOverride and selectedMode updated")
    fun s04_localOverrideSet() =
        runTest {
            val override =
                ChatModelOverride.Local(
                    configId = LocalModelConfigurationId("local-qwen-preset"),
                    displayName = "Qwen 3 8B Fast",
                )

            coordinator.onModelOverrideSelected(override)

            assertEquals(override, coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.OTHER, coordinator.selectedMode.value)
        }

    @Test
    @DisplayName("S05 — API override set: selectedModelOverride and selectedMode updated")
    fun s05_apiOverrideSet() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )

            coordinator.onModelOverrideSelected(override)

            assertEquals(override, coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.OTHER, coordinator.selectedMode.value)
        }

    @Test
    @DisplayName("S09 — Override cleared on FAST mode selection")
    fun s09_clearedOnFast() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            coordinator.onModelOverrideSelected(override)
            coordinator.onModeChange(ChatModeUi.OTHER)
            assertEquals(override, coordinator.selectedModelOverride.value)

            coordinator.onModeChange(ChatModeUi.FAST)

            assertNull(coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.FAST, coordinator.selectedMode.value)
        }

    @Test
    @DisplayName("S10 — Override cleared on THINKING mode selection")
    fun s10_clearedOnThinking() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            coordinator.onModelOverrideSelected(override)
            coordinator.onModeChange(ChatModeUi.OTHER)

            coordinator.onModeChange(ChatModeUi.THINKING)

            assertNull(coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.THINKING, coordinator.selectedMode.value)
        }

    @Test
    @DisplayName("S11 — Override cleared on COUNCIL mode selection")
    fun s11_clearedOnCouncil() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            coordinator.onModelOverrideSelected(override)
            coordinator.onModeChange(ChatModeUi.OTHER)

            coordinator.onModeChange(ChatModeUi.COUNCIL)

            assertNull(coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.COUNCIL, coordinator.selectedMode.value)
        }
}
