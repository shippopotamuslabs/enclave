package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetModelConfigurationsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.UpdateModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
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
    val selectedModelConfig: ModelConfigurationUi? = null,
    // BYOK Sheet
    val showByokSheet: Boolean = false,
    val selectedApiModel: ApiModelConfigUi? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateThemeUseCase: UpdateThemeUseCase,
    private val updateHapticPressUseCase: UpdateHapticPressUseCase,
    private val updateHapticResponseUseCase: UpdateHapticResponseUseCase,
    private val updateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase,
    private val updateSelectedPromptOptionUseCase: UpdateSelectedPromptOptionUseCase,
    private val updateCustomPromptTextUseCase: UpdateCustomPromptTextUseCase,
    private val updateAllowMemoriesUseCase: UpdateAllowMemoriesUseCase,
    private val getModelConfigurationsUseCase: GetModelConfigurationsUseCase,
    private val updateModelConfigurationUseCase: UpdateModelConfigurationUseCase,
    private val getApiModelsUseCase: GetApiModelsUseCase,
    private val saveApiModelUseCase: SaveApiModelUseCase,
    private val deleteApiModelUseCase: DeleteApiModelUseCase,
    private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
    private val setDefaultModelUseCase: SetDefaultModelUseCase,
    private val errorHandler: com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _transientState = MutableStateFlow(TransientState())

    // Security: Keep API key in a separate flow cleared after save
    private val _currentApiKey = MutableStateFlow("")
    val currentApiKey: StateFlow<String> = _currentApiKey

    // Model configurations flow - follows 2026 Compose best practices
    private val modelConfigsFlow = getModelConfigurationsUseCase()

    val uiState: StateFlow<SettingsUiState> = combine(
        getSettingsUseCase(),
        modelConfigsFlow,
        getApiModelsUseCase(),
        getDefaultModelsUseCase(),
        _transientState,
    ) { persistedSettings, modelConfigs, apiModels, defaultModels, transientState ->
        // Use transient state's selectedModelConfig if available (for editing),
        // otherwise use the flow's selectedConfig (initial load)
        val selectedConfig = transientState.selectedModelConfig
            ?: transientState.selectedModelType?.let { type ->
                modelConfigs.find { it.modelType == type }
            }
            
        val selectedApiConfig = transientState.selectedApiModel

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
            // Available HuggingFace models
            availableHuggingFaceModels = modelConfigs.distinctBy { it.huggingFaceModelName },
            // BYOK Sheet
            showByokSheet = transientState.showByokSheet,
            apiModels = apiModels.map { it.toUi() },
            selectedApiModel = selectedApiConfig,
            // Default model assignments
            defaultAssignments = defaultModels.map { def ->
                DefaultModelAssignmentUi(
                    modelType = def.modelType,
                    source = def.source,
                    currentModelName = def.onDeviceDisplayName ?: def.apiModelConfig?.displayName ?: "Unknown",
                    providerName = def.apiModelConfig?.provider?.displayName
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    private fun ApiModelConfig.toUi() = ApiModelConfigUi(
        id = id,
        displayName = displayName,
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl ?: "",
        isVision = isVision,
        thinkingEnabled = thinkingEnabled,
        maxTokens = maxTokens.toString(),
        contextWindow = contextWindow.toString(),
        temperature = temperature,
        topP = topP,
        topK = topK ?: 40
    )

    // Theme
    fun onThemeChange(theme: AppTheme) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update theme", "Failed to update theme")) {
            updateThemeUseCase(theme)
        }
    }

    // Haptic Feedback
    fun onHapticPressChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update haptic press setting", "Failed to update setting")) {
            updateHapticPressUseCase(enabled)
        }
    }

    fun onHapticResponseChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update haptic response setting", "Failed to update setting")) {
            updateHapticResponseUseCase(enabled)
        }
    }

    // Customization
    fun onShowCustomizationSheet(show: Boolean) {
        _transientState.update { it.copy(showCustomizationSheet = show) }
    }

    fun onCustomizationEnabledChange(enabled: Boolean) {
        _transientState.update { it.copy(customizationEnabled = enabled) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update customization setting", "Failed to update setting")) {
            updateCustomizationEnabledUseCase(enabled)
        }
    }

    fun onPromptOptionChange(option: SystemPromptOption) {
        _transientState.update { it.copy(selectedPromptOption = option) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update prompt option", "Failed to update setting")) {
            updateSelectedPromptOptionUseCase(option)
        }
    }

    fun onCustomPromptTextChange(text: String) {
        _transientState.update { it.copy(customPromptText = text) }
    }

    fun onSaveCustomization() {
        val currentState = _transientState.value
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save customization", "Failed to save settings")) {
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
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update memory setting", "Failed to update setting")) {
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
                selectedModelConfig = null // Derived in combine
            )
        }
    }

    fun onClearSelectedModel() {
        _transientState.update { it.copy(selectedModelType = null, selectedModelConfig = null) }
    }

    fun onHuggingFaceModelNameChange(name: String) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(huggingFaceModelName = name))
        }
    }

    fun onTemperatureChange(value: Double) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(temperature = value))
        }
    }

    fun onTopKChange(value: Int) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(topK = value))
        }
    }

    fun onTopPChange(value: Double) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(topP = value))
        }
    }

    fun onMaxTokensChange(value: String) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(maxTokens = value))
        }
    }

    fun onContextWindowChange(value: String) {
        _transientState.update { state ->
            val config = state.selectedModelConfig ?: return@update state
            state.copy(selectedModelConfig = config.copy(contextWindow = value))
        }
    }

    fun onSaveModelConfig(onSuccess: () -> Unit) {
        val config = _transientState.value.selectedModelConfig ?: return
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save model configuration", "Failed to save configuration")) {
            updateModelConfigurationUseCase(config)
            onSuccess()
        }
    }

    // BYOK Setup
    fun onShowByokSheet(show: Boolean) {
        _transientState.update { it.copy(showByokSheet = show, selectedApiModel = null) }
    }

    fun onSelectApiModel(modelId: Long?) {
        if (modelId == null) {
            _transientState.update { it.copy(selectedApiModel = ApiModelConfigUi()) }
            return
        }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to load API model", "Failed to load configuration")) {
            val apiModels = getApiModelsUseCase().first()
            val config = apiModels.find { it.id == modelId }?.toUi()
            _transientState.update { it.copy(selectedApiModel = config) }
        }
    }

    fun onApiModelFieldChange(config: ApiModelConfigUi) {
        _transientState.update { it.copy(selectedApiModel = config) }
    }

    fun onApiKeyChange(key: String) {
        _currentApiKey.value = key
    }

    fun onSaveApiModel(onSuccess: () -> Unit) {
        val config = _transientState.value.selectedApiModel ?: return
        val apiKeyToSave = _currentApiKey.value // Capture atomically before async work
        
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API config", "Failed to save configuration")) {
            saveApiModelUseCase(
                id = config.id,
                displayName = config.displayName,
                provider = config.provider,
                modelId = config.modelId,
                apiKey = apiKeyToSave,
                baseUrl = config.baseUrl.takeIf { it.isNotBlank() },
                isVision = config.isVision,
                thinkingEnabled = config.thinkingEnabled,
                maxTokens = config.maxTokens.toIntOrNull() ?: 4096,
                contextWindow = config.contextWindow.toIntOrNull() ?: 4096,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK
            )
            _currentApiKey.value = "" // Clear immediately after save
            onSuccess()
        }
    }

    fun onDeleteApiModel(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete API config", "Failed to delete configuration")) {
            deleteApiModelUseCase(id)
            onSuccess()
        }
    }

    fun onBackToByokList() {
        _transientState.update { it.copy(selectedApiModel = null) }
    }

    fun onSetDefaultModel(modelType: ModelType, source: ModelSource, apiModelId: Long? = null) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to set default model", "Failed to update default model")) {
            setDefaultModelUseCase(modelType, source, apiModelId)
        }
    }
}
