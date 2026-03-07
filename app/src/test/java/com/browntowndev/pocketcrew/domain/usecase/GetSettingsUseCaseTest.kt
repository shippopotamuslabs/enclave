package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.AppTheme
import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GetSettingsUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var getSettingsUseCase: GetSettingsUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        getSettingsUseCase = GetSettingsUseCase(fakeRepository)
    }

    @Test
    fun `invoke returns settings flow from repository`() = runTest {
        // Given - repository with default settings
        val settingsFlow = getSettingsUseCase()
        val settings = settingsFlow.first()

        // Then - default settings should be returned
        assertEquals(AppTheme.SYSTEM, settings.theme)
        assertTrue(settings.hapticPress)
        assertTrue(settings.hapticResponse)
        assertTrue(settings.customizationEnabled)
        assertEquals(SystemPromptOption.CONCISE, settings.selectedPromptOption)
        assertEquals("", settings.customPromptText)
        assertTrue(settings.allowMemories)
    }

    @Test
    fun `invoke returns updated settings after repository changes`() = runTest {
        // Given - repository with updated settings
        fakeRepository.updateTheme(AppTheme.DARK)
        fakeRepository.updateHapticPress(false)
        fakeRepository.updateCustomPromptText("Custom text")

        // When
        val settings = getSettingsUseCase().first()

        // Then - updated settings should be returned
        assertEquals(AppTheme.DARK, settings.theme)
        assertFalse(settings.hapticPress)
        assertEquals("Custom text", settings.customPromptText)
    }

    @Test
    fun `invoke returns settings flow that emits on changes`() = runTest {
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

