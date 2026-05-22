package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var getSettingsUseCase: GetSettingsUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        getSettingsUseCase = GetSettingsUseCase(fakeRepository)
    }

    @Test
    fun `invoke returns settings flow from repository`() =
        runTest {
            // Given - repository with default settings
            val settingsFlow = getSettingsUseCase()
            val settings = settingsFlow.first()

            // Then - default settings should be returned
            assertEquals(AppTheme.SYSTEM, settings.theme)
            assertTrue(settings.hapticResponse)
            assertTrue(settings.customizationEnabled)
            assertEquals(SystemPromptOption.CONCISE, settings.selectedPromptOption)
            assertEquals("", settings.customPromptText)
            assertTrue(settings.allowMemories)
        }

    @Test
    fun `invoke returns updated settings after repository changes`() =
        runTest {
            // Given - repository with updated settings
            fakeRepository.updateTheme(AppTheme.DARK)
            fakeRepository.updateCustomPromptText("Custom text")

            // When
            val settings = getSettingsUseCase().first()

            // Then - updated settings should be returned
            assertEquals(AppTheme.DARK, settings.theme)
            assertEquals("Custom text", settings.customPromptText)
        }

    @Test
    fun `invoke returns settings flow that emits on changes`() =
        runTest {
            // Given
            val flow = getSettingsUseCase()
            val initialSettings = flow.first()

            // When - update repository
            fakeRepository.updateTheme(AppTheme.DYNAMIC)
            fakeRepository.updateSelectedPromptOption(SystemPromptOption.RIGOROUS)

            // Then - flow emits new values
            val updatedSettings = flow.first()
            assertNotEquals(initialSettings.theme, updatedSettings.theme)
            assertEquals(AppTheme.DYNAMIC, updatedSettings.theme)
            assertEquals(SystemPromptOption.RIGOROUS, updatedSettings.selectedPromptOption)
        }
}
