package com.browntowndev.pocketcrew.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.AppTheme
import com.browntowndev.pocketcrew.domain.model.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI-only transient state that doesn't need persistence.
 */
private data class TransientState(
    val showCustomizationSheet: Boolean = false,
    val customizationEnabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",
    val showDataControlsSheet: Boolean = false,
    val allowMemories: Boolean = true,
    val showMemoriesSheet: Boolean = false,
    val memories: List<StoredMemory> = emptyList(),
    val showFeedbackSheet: Boolean = false,
    val feedbackText: String = "",
    // Model Configuration
    val showModelConfigSheet: Boolean = false,
    val selectedModelType: ModelType? = null,
    val selectedModelConfig: ModelConfigurationUi? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateThemeUseCase: UpdateThemeUseCase,
    private val updateHapticPressUseCase: UpdateHapticPressUseCase,
    private val updateHapticResponseUseCase: UpdateHapticResponseUseCase,
    private val updateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase,
    private val updateSelectedPromptOptionUseCase: UpdateSelectedPromptOptionUseCase,
    private val updateCustomPromptTextUseCase: UpdateCustomPromptTextUseCase,
    private val updateAllowMemoriesUseCase: UpdateAllowMemoriesUseCase,
    private val getModelConfigurationsUseCase: GetModelConfigurationsUseCase,
    private val updateModelConfigurationUseCase: UpdateModelConfigurationUseCase
) : ViewModel() {

    private val _transientState = MutableStateFlow(TransientState())

    // Model configurations flow - follows 2026 Compose best practices
    private val modelConfigsFlow = getModelConfigurationsUseCase()

    val uiState: StateFlow<SettingsUiState> = combine(
        getSettingsUseCase(),
        modelConfigsFlow,
        _transientState,
    ) { persistedSettings, modelConfigs, transientState ->
        // Use transient state's selectedModelConfig if available (for editing),
        // otherwise use the flow's selectedConfig (initial load)
        val selectedConfig = transientState.selectedModelConfig
            ?: transientState.selectedModelType?.let { type ->
                modelConfigs.find { it.modelType == type }
            }

        SettingsUiState(
            // Persisted settings from repository
            theme = persistedSettings.theme,
            hapticPress = persistedSettings.hapticPress,
            hapticResponse = persistedSettings.hapticResponse,
            customizationEnabled = persistedSettings.customizationEnabled,
            selectedPromptOption = persistedSettings.selectedPromptOption,
            customPromptText = persistedSettings.customPromptText,
            allowMemories = persistedSettings.allowMemories,
            // Transient UI state
            showCustomizationSheet = transientState.showCustomizationSheet,
            showDataControlsSheet = transientState.showDataControlsSheet,
            showMemoriesSheet = transientState.showMemoriesSheet,
            showFeedbackSheet = transientState.showFeedbackSheet,
            feedbackText = transientState.feedbackText,
            memories = transientState.memories,
            // Model Configuration state
            showModelConfigSheet = transientState.showModelConfigSheet,
            modelConfigurations = modelConfigs,
            selectedModelType = transientState.selectedModelType,
            selectedModelConfig = selectedConfig,
            // Available HuggingFace models (currently just the registered one)
            availableHuggingFaceModels = modelConfigs.map { it.huggingFaceModelName }.distinct()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    // Theme
    fun onThemeChange(theme: AppTheme) {
        viewModelScope.launch {
            updateThemeUseCase(theme)
        }
    }

    // Haptic Feedback
    fun onHapticPressChange(enabled: Boolean) {
        viewModelScope.launch {
            updateHapticPressUseCase(enabled)
        }
    }

    fun onHapticResponseChange(enabled: Boolean) {
        viewModelScope.launch {
            updateHapticResponseUseCase(enabled)
        }
    }

    // Customization
    fun onShowCustomizationSheet(show: Boolean) {
        _transientState.update { it.copy(showCustomizationSheet = show) }
    }

    fun onCustomizationEnabledChange(enabled: Boolean) {
        _transientState.update { it.copy(customizationEnabled = enabled) }
        viewModelScope.launch {
            updateCustomizationEnabledUseCase(enabled)
        }
    }

    fun onPromptOptionChange(option: SystemPromptOption) {
        _transientState.update { it.copy(selectedPromptOption = option) }
        viewModelScope.launch {
            updateSelectedPromptOptionUseCase(option)
        }
    }

    fun onCustomPromptTextChange(text: String) {
        _transientState.update { it.copy(customPromptText = text) }
    }

    fun onSaveCustomization() {
        val currentState = _transientState.value
        viewModelScope.launch {
            updateCustomizationEnabledUseCase(currentState.customizationEnabled)
            updateSelectedPromptOptionUseCase(currentState.selectedPromptOption)
            updateCustomPromptTextUseCase(currentState.customPromptText)
        }
        onShowCustomizationSheet(false)
    }

    // Data Controls
    fun onShowDataControlsSheet(show: Boolean) {
        _transientState.update { it.copy(showDataControlsSheet = show) }
    }

    fun onAllowMemoriesChange(enabled: Boolean) {
        _transientState.update { it.copy(allowMemories = enabled) }
        viewModelScope.launch {
            updateAllowMemoriesUseCase(enabled)
        }
    }

    fun onDeleteAllConversations() {
        // Stub: Persist to DataStore - not implemented
    }

    fun onDeleteAllMemories() {
        // Stub: Persist to DataStore - not implemented
    }

    // Memories
    fun onShowMemoriesSheet(show: Boolean) {
        _transientState.update { it.copy(showMemoriesSheet = show) }
    }

    fun onDeleteMemory(memoryId: String) {
        // Stub: Persist to DataStore - not implemented
    }

    // Feedback
    fun onShowFeedbackSheet(show: Boolean) {
        _transientState.update { it.copy(showFeedbackSheet = show) }
    }

    fun onFeedbackTextChange(text: String) {
        _transientState.update { it.copy(feedbackText = text) }
    }

    fun onSubmitFeedback() {
        // Stub: Persist to DataStore - not implemented
        _transientState.update { it.copy(feedbackText = "", showFeedbackSheet = false) }
    }

    // Model Configuration
    fun onShowModelConfigSheet(show: Boolean) {
        _transientState.update { it.copy(showModelConfigSheet = show, selectedModelType = null) }
    }

    fun onSelectModelType(modelType: ModelType) {
        _transientState.update {
            it.copy(
                selectedModelType = modelType,
                selectedModelConfig = null // Will be derived from modelConfigs in combine
            )
        }
    }

    fun onBackToModelList() {
        _transientState.update { it.copy(selectedModelType = null, selectedModelConfig = null) }
    }

    fun onHuggingFaceModelNameChange(modelName: String) {
        val currentConfig = _transientState.value.selectedModelConfig ?: return
        val updatedConfig = currentConfig.copy(huggingFaceModelName = modelName)
        _transientState.update { it.copy(selectedModelConfig = updatedConfig) }
    }

    fun onTemperatureChange(temperature: Double) {
        val currentConfig = _transientState.value.selectedModelConfig ?: return
        val updatedConfig = currentConfig.copy(temperature = temperature)
        _transientState.update { it.copy(selectedModelConfig = updatedConfig) }
    }

    fun onTopKChange(topK: Int) {
        val currentConfig = _transientState.value.selectedModelConfig ?: return
        val updatedConfig = currentConfig.copy(topK = topK)
        _transientState.update { it.copy(selectedModelConfig = updatedConfig) }
    }

    fun onTopPChange(topP: Double) {
        val currentConfig = _transientState.value.selectedModelConfig ?: return
        val updatedConfig = currentConfig.copy(topP = topP)
        _transientState.update { it.copy(selectedModelConfig = updatedConfig) }
    }

    fun onSaveModelConfig() {
        val config = _transientState.value.selectedModelConfig ?: return
        viewModelScope.launch {
            updateModelConfigurationUseCase(config)
        }
        onBackToModelList()
    }
}
