package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.domain.model.AppTheme
import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for SettingsRepository interface contract.
 * Uses FakeSettingsRepository to verify the interface contract.
 */
class SettingsRepositoryTest {

    private lateinit var repository: FakeSettingsRepository

    @BeforeEach
    fun setup() {
        repository = FakeSettingsRepository()
    }

    // Test settingsFlow returns default values when no preferences exist

    @Test
    fun `settingsFlow returns default values initially`() = runTest {
        // When
        val settings = repository.settingsFlow.first()

        // Then
        assertEquals(AppTheme.SYSTEM, settings.theme)
        assertTrue(settings.hapticPress)
        assertTrue(settings.hapticResponse)
        assertTrue(settings.customizationEnabled)
        assertEquals(SystemPromptOption.CONCISE, settings.selectedPromptOption)
        assertEquals("", settings.customPromptText)
        assertTrue(settings.allowMemories)
    }

    // Test updateTheme

    @Test
    fun `updateTheme updates theme in settingsFlow`() = runTest {
        // When
        repository.updateTheme(AppTheme.DARK)

        // Then
        val settings = repository.settingsFlow.first()
        assertEquals(AppTheme.DARK, settings.theme)
    }

    @Test
    fun `updateTheme works for all theme values`() = runTest {
        // Given
        val themes = listOf(AppTheme.SYSTEM, AppTheme.DYNAMIC, AppTheme.DARK, AppTheme.LIGHT)

        // When & Then
        themes.forEach { theme ->
            repository.updateTheme(theme)
            val settings = repository.settingsFlow.first()
            assertEquals(theme, settings.theme)
        }
    }

    // Test updateHapticPress

    @Test
    fun `updateHapticPress updates value in settingsFlow`() = runTest {
        // When
        repository.updateHapticPress(false)

        // Then
        val settings = repository.settingsFlow.first()
        assertFalse(settings.hapticPress)
    }

    @Test
    fun `updateHapticPress can toggle between true and false`() = runTest {
        // When
        repository.updateHapticPress(false)
        assertFalse(repository.settingsFlow.first().hapticPress)

        repository.updateHapticPress(true)
        assertTrue(repository.settingsFlow.first().hapticPress)
    }

    // Test updateHapticResponse

    @Test
    fun `updateHapticResponse updates value in settingsFlow`() = runTest {
        // When
        repository.updateHapticResponse(false)

        // Then
        val settings = repository.settingsFlow.first()
        assertFalse(settings.hapticResponse)
    }

    // Test updateCustomizationEnabled

    @Test
    fun `updateCustomizationEnabled updates value in settingsFlow`() = runTest {
        // When
        repository.updateCustomizationEnabled(false)

        // Then
        val settings = repository.settingsFlow.first()
        assertFalse(settings.customizationEnabled)
    }

    // Test updateSelectedPromptOption

    @Test
    fun `updateSelectedPromptOption updates value in settingsFlow`() = runTest {
        // When
        repository.updateSelectedPromptOption(SystemPromptOption.CUSTOM)

        // Then
        val settings = repository.settingsFlow.first()
        assertEquals(SystemPromptOption.CUSTOM, settings.selectedPromptOption)
    }

    @Test
    fun `updateSelectedPromptOption works for all options`() = runTest {
        // Given
        val options = listOf(
            SystemPromptOption.CUSTOM,
            SystemPromptOption.CONCISE,
            SystemPromptOption.FORMAL,
            SystemPromptOption.RIGOROUS
        )

        // When & Then
        options.forEach { option ->
            repository.updateSelectedPromptOption(option)
            val settings = repository.settingsFlow.first()
            assertEquals(option, settings.selectedPromptOption)
        }
    }

    // Test updateCustomPromptText

    @Test
    fun `updateCustomPromptText updates text in settingsFlow`() = runTest {
        // When
        repository.updateCustomPromptText("My custom prompt")

        // Then
        val settings = repository.settingsFlow.first()
        assertEquals("My custom prompt", settings.customPromptText)
    }

    @Test
    fun `updateCustomPromptText allows empty string`() = runTest {
        // When
        repository.updateCustomPromptText("")

        // Then
        val settings = repository.settingsFlow.first()
        assertEquals("", settings.customPromptText)
    }

    // Test updateAllowMemories

    @Test
    fun `updateAllowMemories updates value in settingsFlow`() = runTest {
        // When
        repository.updateAllowMemories(false)

        // Then
        val settings = repository.settingsFlow.first()
        assertFalse(settings.allowMemories)
    }

    // Test multiple settings updates

    @Test
    fun `multiple settings updates are all persisted`() = runTest {
        // When
        repository.updateTheme(AppTheme.DARK)
        repository.updateHapticPress(false)
        repository.updateHapticResponse(false)
        repository.updateCustomizationEnabled(false)
        repository.updateSelectedPromptOption(SystemPromptOption.RIGOROUS)
        repository.updateCustomPromptText("Test prompt")
        repository.updateAllowMemories(false)

        // Then
        val settings = repository.settingsFlow.first()
        assertEquals(AppTheme.DARK, settings.theme)
        assertFalse(settings.hapticPress)
        assertFalse(settings.hapticResponse)
        assertFalse(settings.customizationEnabled)
        assertEquals(SystemPromptOption.RIGOROUS, settings.selectedPromptOption)
        assertEquals("Test prompt", settings.customPromptText)
        assertFalse(settings.allowMemories)
    }

    // Test error handling

    @Test
    fun `updateTheme throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateTheme = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateTheme(AppTheme.DARK) }
        }
    }

    @Test
    fun `updateHapticPress throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateHapticPress = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateHapticPress(false) }
        }
    }

    @Test
    fun `updateHapticResponse throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateHapticResponse = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateHapticResponse(false) }
        }
    }

    @Test
    fun `updateCustomizationEnabled throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateCustomizationEnabled = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateCustomizationEnabled(false) }
        }
    }

    @Test
    fun `updateSelectedPromptOption throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateSelectedPromptOption = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateSelectedPromptOption(SystemPromptOption.CONCISE) }
        }
    }

    @Test
    fun `updateCustomPromptText throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateCustomPromptText = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateCustomPromptText("test") }
        }
    }

    @Test
    fun `updateAllowMemories throws exception when repository fails`() = runTest {
        // Given
        repository.shouldThrowOnUpdateAllowMemories = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { repository.updateAllowMemories(false) }
        }
    }
}

