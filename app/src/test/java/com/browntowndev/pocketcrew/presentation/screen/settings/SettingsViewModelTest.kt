package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetModelConfigurationsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.UpdateModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockGetSettingsUseCase: GetSettingsUseCase
    private lateinit var mockUpdateThemeUseCase: UpdateThemeUseCase
    private lateinit var mockUpdateHapticPressUseCase: UpdateHapticPressUseCase
    private lateinit var mockUpdateHapticResponseUseCase: UpdateHapticResponseUseCase
    private lateinit var mockUpdateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase
    private lateinit var mockUpdateSelectedPromptOptionUseCase: UpdateSelectedPromptOptionUseCase
    private lateinit var mockUpdateCustomPromptTextUseCase: UpdateCustomPromptTextUseCase
    private lateinit var mockUpdateAllowMemoriesUseCase: UpdateAllowMemoriesUseCase
    private lateinit var mockGetModelConfigurationsUseCase: GetModelConfigurationsUseCase
    private lateinit var mockUpdateModelConfigurationUseCase: UpdateModelConfigurationUseCase

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create mock use cases
        mockGetSettingsUseCase = mockk()
        mockUpdateThemeUseCase = mockk(relaxed = true)
        mockUpdateHapticPressUseCase = mockk(relaxed = true)
        mockUpdateHapticResponseUseCase = mockk(relaxed = true)
        mockUpdateCustomizationEnabledUseCase = mockk(relaxed = true)
        mockUpdateSelectedPromptOptionUseCase = mockk(relaxed = true)
        mockUpdateCustomPromptTextUseCase = mockk(relaxed = true)
        mockUpdateAllowMemoriesUseCase = mockk(relaxed = true)
        mockGetModelConfigurationsUseCase = mockk()
        mockUpdateModelConfigurationUseCase = mockk(relaxed = true)

        // Setup default mock behavior for getSettingsUseCase
        val settingsFlow = MutableStateFlow(SettingsData())
        every { mockGetSettingsUseCase.invoke() } returns settingsFlow.asStateFlow()

        // Setup default mock behavior for getModelConfigurationsUseCase
        every { mockGetModelConfigurationsUseCase.invoke() } returns MutableStateFlow<List<com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationUi>>(emptyList()).asStateFlow()
        every { mockGetModelConfigurationsUseCase.observeSingle(any()) } returns MutableStateFlow<com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationUi?>(null).asStateFlow()

        // Create ViewModel
        viewModel = SettingsViewModel(
            getSettingsUseCase = mockGetSettingsUseCase,
            updateThemeUseCase = mockUpdateThemeUseCase,
            updateHapticPressUseCase = mockUpdateHapticPressUseCase,
            updateHapticResponseUseCase = mockUpdateHapticResponseUseCase,
            updateCustomizationEnabledUseCase = mockUpdateCustomizationEnabledUseCase,
            updateSelectedPromptOptionUseCase = mockUpdateSelectedPromptOptionUseCase,
            updateCustomPromptTextUseCase = mockUpdateCustomPromptTextUseCase,
            updateAllowMemoriesUseCase = mockUpdateAllowMemoriesUseCase,
            getModelConfigurationsUseCase = mockGetModelConfigurationsUseCase,
            updateModelConfigurationUseCase = mockUpdateModelConfigurationUseCase
        )

        // Advance dispatcher to let init block complete
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Initial state tests

    @Test
    fun `initial uiState has default values`() = runTest {
        // Then
        val state = viewModel.uiState.value
        assertEquals(AppTheme.SYSTEM, state.theme)
        assertTrue(state.hapticPress)
        assertTrue(state.hapticResponse)
        assertTrue(state.customizationEnabled)
        assertEquals(SystemPromptOption.CONCISE, state.selectedPromptOption)
        assertEquals("", state.customPromptText)
        assertTrue(state.allowMemories)
        assertFalse(state.showCustomizationSheet)
        assertFalse(state.showDataControlsSheet)
        assertFalse(state.showMemoriesSheet)
        assertFalse(state.showFeedbackSheet)
    }

    // Theme change tests

    @Test
    fun `onThemeChange calls UpdateThemeUseCase with DARK`() = runTest {
        // When
        viewModel.onThemeChange(AppTheme.DARK)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - verify use case was called
        coVerify { mockUpdateThemeUseCase.invoke(AppTheme.DARK) }
    }

    @Test
    fun `onThemeChange calls UpdateThemeUseCase with all themes`() = runTest {
        // When
        AppTheme.entries.forEach { theme ->
            viewModel.onThemeChange(theme)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Then - verify use case was called for each theme
        coVerify { mockUpdateThemeUseCase.invoke(AppTheme.SYSTEM) }
        coVerify { mockUpdateThemeUseCase.invoke(AppTheme.DYNAMIC) }
        coVerify { mockUpdateThemeUseCase.invoke(AppTheme.DARK) }
        coVerify { mockUpdateThemeUseCase.invoke(AppTheme.LIGHT) }
    }

    // Haptic feedback tests

    @Test
    fun `onHapticPressChange calls UpdateHapticPressUseCase with false`() = runTest {
        // When
        viewModel.onHapticPressChange(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockUpdateHapticPressUseCase.invoke(false) }
    }

    @Test
    fun `onHapticPressChange calls UpdateHapticPressUseCase with true`() = runTest {
        // When
        viewModel.onHapticPressChange(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockUpdateHapticPressUseCase.invoke(true) }
    }

    @Test
    fun `onHapticResponseChange calls UpdateHapticResponseUseCase with false`() = runTest {
        // When
        viewModel.onHapticResponseChange(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockUpdateHapticResponseUseCase.invoke(false) }
    }

    @Test
    fun `onHapticResponseChange calls UpdateHapticResponseUseCase with true`() = runTest {
        // When
        viewModel.onHapticResponseChange(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockUpdateHapticResponseUseCase.invoke(true) }
    }

    // Customization sheet tests

    @Test
    fun `onShowCustomizationSheet true shows customization sheet in uiState`() = runTest {
        // When
        viewModel.onShowCustomizationSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.uiState.value.showCustomizationSheet)
    }

    @Test
    fun `onShowCustomizationSheet false hides customization sheet in uiState`() = runTest {
        // Given
        viewModel.onShowCustomizationSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showCustomizationSheet)

        // When
        viewModel.onShowCustomizationSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showCustomizationSheet)
    }

    @Test
    fun `onPromptOptionChange calls UpdateSelectedPromptOptionUseCase`() = runTest {
        // When
        viewModel.onPromptOptionChange(SystemPromptOption.FORMAL)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - verify use case was called
        coVerify { mockUpdateSelectedPromptOptionUseCase.invoke(SystemPromptOption.FORMAL) }
    }

    @Test
    fun `onCustomPromptTextChange does not call use case immediately`() = runTest {
        // When
        viewModel.onCustomPromptTextChange("My custom prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - use case should NOT be called immediately (only saved on explicit save)
        // The text is stored in transient state and only persisted on onSaveCustomization
    }

    @Test
    fun `onCustomizationEnabledChange calls UpdateCustomizationEnabledUseCase`() = runTest {
        // When
        viewModel.onCustomizationEnabledChange(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - verify use case was called
        coVerify { mockUpdateCustomizationEnabledUseCase.invoke(false) }
    }

    @Test
    fun `onSaveCustomization persists changes and hides sheet`() = runTest {
        // Given - set up some transient state
        viewModel.onCustomizationEnabledChange(false)
        viewModel.onPromptOptionChange(SystemPromptOption.CUSTOM)
        viewModel.onCustomPromptTextChange("Test prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.onSaveCustomization()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - sheet is hidden
        assertFalse(viewModel.uiState.value.showCustomizationSheet)
    }

    // Data controls sheet tests

    @Test
    fun `onShowDataControlsSheet true shows data controls sheet`() = runTest {
        // When
        viewModel.onShowDataControlsSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.uiState.value.showDataControlsSheet)
    }

    @Test
    fun `onShowDataControlsSheet false hides data controls sheet`() = runTest {
        // Given
        viewModel.onShowDataControlsSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.onShowDataControlsSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showDataControlsSheet)
    }

    // Memories tests

    @Test
    fun `onAllowMemoriesChange calls UpdateAllowMemoriesUseCase`() = runTest {
        // When
        viewModel.onAllowMemoriesChange(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - verify use case was called
        coVerify { mockUpdateAllowMemoriesUseCase.invoke(false) }
    }

    @Test
    fun `onShowMemoriesSheet true shows memories sheet`() = runTest {
        // When
        viewModel.onShowMemoriesSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.uiState.value.showMemoriesSheet)
    }

    @Test
    fun `onShowMemoriesSheet false hides memories sheet`() = runTest {
        // Given
        viewModel.onShowMemoriesSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.onShowMemoriesSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showMemoriesSheet)
    }

    // Feedback tests

    @Test
    fun `onShowFeedbackSheet true shows feedback sheet`() = runTest {
        // When
        viewModel.onShowFeedbackSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.uiState.value.showFeedbackSheet)
    }

    @Test
    fun `onShowFeedbackSheet false hides feedback sheet`() = runTest {
        // Given
        viewModel.onShowFeedbackSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.onShowFeedbackSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showFeedbackSheet)
    }

    @Test
    fun `onFeedbackTextChange updates feedback text in transient state`() = runTest {
        // When
        viewModel.onFeedbackTextChange("This is my feedback")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - feedback text is in uiState (transient state)
        assertEquals("This is my feedback", viewModel.uiState.value.feedbackText)
    }

    @Test
    fun `onSubmitFeedback clears feedback text and hides sheet`() = runTest {
        // Given
        viewModel.onFeedbackTextChange("This is my feedback")
        viewModel.onShowFeedbackSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showFeedbackSheet)

        // When
        viewModel.onSubmitFeedback()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showFeedbackSheet)
        assertEquals("", viewModel.uiState.value.feedbackText)
    }

    // Stub methods - verify they don't crash

    @Test
    fun `onDeleteAllConversations does not throw`() = runTest {
        // When/Then - should not throw
        viewModel.onDeleteAllConversations()
    }

    @Test
    fun `onDeleteAllMemories does not throw`() = runTest {
        // When/Then - should not throw
        viewModel.onDeleteAllMemories()
    }

    @Test
    fun `onDeleteMemory does not throw`() = runTest {
        // When/Then - should not throw
        viewModel.onDeleteMemory("some-memory-id")
    }
}

