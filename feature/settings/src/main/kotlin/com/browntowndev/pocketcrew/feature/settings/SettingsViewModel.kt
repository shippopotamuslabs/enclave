package com.browntowndev.pocketcrew.feature.settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


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
    val availableToDownloadModels: List<LocalModelAsset> = emptyList(),
    val selectedLocalModelAsset: LocalModelAssetUi? = null,
    val selectedLocalModelConfig: LocalModelConfigUi? = null,
    // BYOK Sheet
    val showByokSheet: Boolean = false,
    val selectedApiModelAsset: ApiModelAssetUi? = null,
    val selectedApiModelConfig: ApiModelConfigUi? = null,
    // Deletion Flow
    val showCannotDeleteLastModelAlert: Boolean = false,
    val pendingDeletionModelId: Long? = null,
    val pendingDeletionConfigId: Long? = null,
    val pendingDeletionApiCredentialsId: Long? = null,
    val pendingDeletionApiConfigId: Long? = null,
    val modelTypesNeedingReassignment: List<ModelType> = emptyList(),
    val reassignmentOptions: List<ReassignmentOptionUi> = emptyList(),
    // Assignment Selection Dialog
    val showAssignmentDialog: Boolean = false,
    val editingAssignmentSlot: ModelType? = null
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
    private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
    private val saveLocalModelConfigurationUseCase: SaveLocalModelConfigurationUseCase,
    private val deleteLocalModelConfigurationUseCase: DeleteLocalModelConfigurationUseCase,
    private val deleteLocalModelMetadataUseCase: DeleteLocalModelMetadataUseCase,
    private val deleteLocalModelUseCase: DeleteLocalModelUseCase,
    private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
    private val saveApiCredentialsUseCase: SaveApiCredentialsUseCase,
    private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase,
    private val saveApiModelConfigurationUseCase: SaveApiModelConfigurationUseCase,
    private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase,
    private val getDefaultModelsUseCase: GetDefaultModelsUseCase,
    private val setDefaultModelUseCase: SetDefaultModelUseCase,
    private val errorHandler: ViewModelErrorHandler
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _transientState = MutableStateFlow(TransientState())

    // Security: Keep API key in a separate flow cleared after save
    private val _currentApiKey = MutableStateFlow("")
    val currentApiKey: StateFlow<String> = _currentApiKey

    // Model configurations flow - follows 2026 Compose best practices
    private val localModelAssetsFlow = getLocalModelAssetsUseCase()
    private val apiModelAssetsFlow = getApiModelAssetsUseCase()

    val uiState: StateFlow<SettingsUiState> = combine(
        getSettingsUseCase(),
        localModelAssetsFlow,
        apiModelAssetsFlow,
        getDefaultModelsUseCase(),
        _transientState,
    ) { persistedSettings, localAssets, apiAssets, defaultModels, transientState ->
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
            localModels = localAssets.map { it.toUi() },
            availableToDownloadModels = transientState.availableToDownloadModels.map { it.toUi() },
            selectedLocalModelAsset = transientState.selectedLocalModelAsset?.let { selected ->
                if (selected.metadataId != 0L) {
                    localAssets.find { it.metadata.id == selected.metadataId }?.toUi() ?: selected
                } else {
                    selected
                }
            },
            selectedLocalModelConfig = transientState.selectedLocalModelConfig?.let { selected ->
                if (selected.id != 0L) {
                    val asset = localAssets.find { it.metadata.id == selected.localModelId }
                    asset?.configurations?.find { it.id == selected.id }?.toUi() ?: selected
                } else {
                    selected
                }
            },
            // Available HuggingFace models for new configurations
            availableHuggingFaceModels = localAssets.map {
                LocalModelMetadataUi(
                    id = it.metadata.id,
                    huggingFaceModelName = it.metadata.huggingFaceModelName
                )
            }.distinctBy { it.huggingFaceModelName },
            // BYOK Sheet
            showByokSheet = transientState.showByokSheet,
            apiModels = apiAssets.map { it.toUi() },
            selectedApiModelAsset = transientState.selectedApiModelAsset?.let { selected ->
                if (selected.credentialsId != 0L) {
                    apiAssets.find { it.credentials.id == selected.credentialsId }?.toUi() ?: selected
                } else {
                    selected
                }
            },
            selectedApiModelConfig = transientState.selectedApiModelConfig?.let { selected ->
                if (selected.id != 0L) {
                    val asset = apiAssets.find { it.credentials.id == selected.credentialsId }
                    asset?.configurations?.find { it.id == selected.id }?.toUi() ?: selected
                } else {
                    selected
                }
            },
            // Default model assignments
            defaultAssignments = defaultModels.map { def ->
                DefaultModelAssignmentUi(
                    modelType = def.modelType,
                    source = if (def.apiConfigId != null) ModelSource.API else ModelSource.ON_DEVICE,
                    currentModelName = def.displayName ?: "Unknown",
                    displayLabel = def.modelType.displayLabel,
                    providerName = def.providerName
                )
            },
            // Deletion Flow
            showCannotDeleteLastModelAlert = transientState.showCannotDeleteLastModelAlert,
            pendingDeletionModelId = transientState.pendingDeletionModelId,
            pendingDeletionConfigId = transientState.pendingDeletionConfigId,
            modelTypesNeedingReassignment = transientState.modelTypesNeedingReassignment,
            reassignmentOptions = transientState.reassignmentOptions,
            // Assignment Dialog
            showAssignmentDialog = transientState.showAssignmentDialog,
            editingAssignmentSlot = transientState.editingAssignmentSlot
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    private fun ApiModelAsset.toUi() = ApiModelAssetUi(
        credentialsId = credentials.id,
        displayName = credentials.displayName,
        provider = credentials.provider,
        modelId = credentials.modelId,
        baseUrl = credentials.baseUrl,
        isVision = credentials.isVision,
        credentialAlias = credentials.credentialAlias,
        configurations = configurations.map { it.toUi() }
    )

    private fun ApiModelConfiguration.toUi() = ApiModelConfigUi(
        id = id,
        credentialsId = apiCredentialsId,
        displayName = displayName,
        maxTokens = maxTokens.toString(),
        contextWindow = contextWindow.toString(),
        temperature = temperature,
        topP = topP,
        topK = topK?.toString() ?: "40",
        minP = minP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        customHeaders = customHeaders.map { CustomHeaderUi(it.key, it.value) },
        thinkingEnabled = false,
        systemPrompt = systemPrompt
    )

    private fun LocalModelAsset.toUi() = LocalModelAssetUi(
        metadataId = metadata.id,
        huggingFaceModelName = metadata.huggingFaceModelName,
        remoteFileName = metadata.remoteFileName,
        sizeInBytes = metadata.sizeInBytes,
        configurations = configurations.map { it.toUi() },
        visionCapable = metadata.visionCapable
    )

    private fun LocalModelConfiguration.toUi() = LocalModelConfigUi(
        id = id,
        localModelId = localModelId,
        displayName = displayName,
        maxTokens = maxTokens.toString(),
        contextWindow = contextWindow.toString(),
        temperature = temperature,
        topP = topP,
        topK = topK?.toString() ?: "",
        minP = minP,
        repetitionPenalty = repetitionPenalty,
        systemPrompt = systemPrompt,
        isSystemPreset = isSystemPreset
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

    // Model Configuration (Local Models)
    fun onShowModelConfigSheet(show: Boolean) {
        if (show) {
            viewModelScope.launch {
                val softDeleted = getLocalModelAssetsUseCase.getSoftDeletedModels()
                _transientState.update { it.copy(
                    showModelConfigSheet = true,
                    availableToDownloadModels = softDeleted,
                    selectedLocalModelAsset = null,
                    selectedLocalModelConfig = null
                ) }
            }
        } else {
            _transientState.update { it.copy(showModelConfigSheet = false) }
        }
    }

    fun onSelectModelType(modelType: ModelType) {
        viewModelScope.launch {
            val defaults = getDefaultModelsUseCase().first()
            val assignment = defaults.find { it.modelType == modelType }

            if (assignment != null) {
                if (assignment.localConfigId != null) {
                    val localAssets = localModelAssetsFlow.first()
                    val asset = localAssets.find { asset ->
                        asset.configurations.any { it.id == assignment.localConfigId }
                    }
                    asset?.let {
                        onSelectLocalModelAsset(it.toUi())
                        val config = it.configurations.find { it.id == assignment.localConfigId }
                        onSelectLocalModelConfig(config?.toUi())
                    }
                } else if (assignment.apiConfigId != null) {
                    val apiAssets = apiModelAssetsFlow.first()
                    val asset = apiAssets.find { asset ->
                        asset.configurations.any { it.id == assignment.apiConfigId }
                    }
                    asset?.let {
                        onSelectApiModelAsset(it.toUi())
                        val config = it.configurations.find { it.id == assignment.apiConfigId }
                        onSelectApiModelConfig(config?.toUi())
                    }
                }
            }
        }
    }

    fun onClearSelectedModel() {
        _transientState.update {
            it.copy(
                selectedLocalModelAsset = null,
                selectedLocalModelConfig = null,
                selectedApiModelAsset = null,
                selectedApiModelConfig = null
            )
        }
    }

    fun onSelectLocalModelAsset(asset: LocalModelAssetUi?) {
        _transientState.update { it.copy(selectedLocalModelAsset = asset, selectedLocalModelConfig = null) }
    }

    fun onSelectLocalModelConfig(config: LocalModelConfigUi?) {
        _transientState.update { it.copy(selectedLocalModelConfig = config ?: LocalModelConfigUi()) }
    }

    fun onClearSelectedLocalModel() {
        _transientState.update { it.copy(selectedLocalModelAsset = null, selectedLocalModelConfig = null) }
    }

    fun onLocalModelConfigFieldChange(config: LocalModelConfigUi) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { it.copy(selectedLocalModelConfig = config) }
    }

    fun onHuggingFaceModelNameChange(huggingFaceModelName: String) {
        val asset = uiState.value.localModels.find { it.huggingFaceModelName == huggingFaceModelName }
        asset?.let { onSelectLocalModelAsset(it) }
    }

    fun onTemperatureChange(temperature: Double) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { 
            it.copy(
                selectedLocalModelConfig = it.selectedLocalModelConfig?.copy(temperature = temperature),
                selectedApiModelConfig = it.selectedApiModelConfig?.copy(temperature = temperature)
            )
        }
    }

    fun onTopKChange(topK: String) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { 
            it.copy(
                selectedLocalModelConfig = it.selectedLocalModelConfig?.copy(topK = topK),
                selectedApiModelConfig = it.selectedApiModelConfig?.copy(topK = topK)
            )
        }
    }

    fun onTopPChange(topP: Double) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { 
            it.copy(
                selectedLocalModelConfig = it.selectedLocalModelConfig?.copy(topP = topP),
                selectedApiModelConfig = it.selectedApiModelConfig?.copy(topP = topP)
            )
        }
    }

    fun onMaxTokensChange(maxTokens: String) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { 
            it.copy(
                selectedLocalModelConfig = it.selectedLocalModelConfig?.copy(maxTokens = maxTokens),
                selectedApiModelConfig = it.selectedApiModelConfig?.copy(maxTokens = maxTokens)
            )
        }
    }

    fun onContextWindowChange(contextWindow: String) {
        if (_transientState.value.selectedLocalModelConfig?.isSystemPreset == true) return
        _transientState.update { 
            it.copy(
                selectedLocalModelConfig = it.selectedLocalModelConfig?.copy(contextWindow = contextWindow),
                selectedApiModelConfig = it.selectedApiModelConfig?.copy(contextWindow = contextWindow)
            )
        }
    }

    fun onSaveModelConfig(onSuccess: () -> Unit) {
        if (_transientState.value.selectedLocalModelConfig != null) {
            onSaveLocalModelConfig(onSuccess)
        } else if (_transientState.value.selectedApiModelConfig != null) {
            onSaveApiModelConfig(onSuccess)
        } else {
            onSuccess()
        }
    }

    fun onSaveLocalModelConfig(onSuccess: () -> Unit) {
        val configUi = _transientState.value.selectedLocalModelConfig ?: return
        val assetUi = _transientState.value.selectedLocalModelAsset ?: return
        
        if (configUi.isSystemPreset) return
        
        val config = LocalModelConfiguration(
            id = configUi.id,
            localModelId = assetUi.metadataId,
            displayName = configUi.displayName,
            maxTokens = configUi.maxTokens.toIntOrNull() ?: 4096,
            contextWindow = configUi.contextWindow.toIntOrNull() ?: 4096,
            temperature = configUi.temperature,
            topP = configUi.topP,
            topK = configUi.topK.toIntOrNull(),
            minP = configUi.minP,
            repetitionPenalty = configUi.repetitionPenalty,
            systemPrompt = configUi.systemPrompt
        )

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save local model configuration", "Failed to save configuration")) {
            saveLocalModelConfigurationUseCase(config)
            onSuccess()
        }
    }

    fun onDeleteLocalModelConfig(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Check if this config is a default
            val defaults = getDefaultModelsUseCase().first()
            val needingReassignment = defaults.filter { it.localConfigId == id }.map { it.modelType }

            if (needingReassignment.isNotEmpty()) {
                val localAssets = localModelAssetsFlow.first()
                val apiAssets = apiModelAssetsFlow.first()
                val deletedAsset = localAssets.find { asset ->
                    asset.configurations.any { config -> config.id == id }
                }
                val requiresVisionCompatibility =
                    ModelType.VISION in needingReassignment ||
                        deletedAsset?.metadata?.visionCapable == true
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeLocalConfigId = id,
                    requireVisionCompatibility = requiresVisionCompatibility
                )

                _transientState.update { it.copy(
                    pendingDeletionConfigId = id,
                    modelTypesNeedingReassignment = needingReassignment,
                    reassignmentOptions = options
                ) }
            } else {
                viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete local model configuration", "Failed to delete configuration")) {
                    deleteLocalModelConfigurationUseCase(id).getOrThrow()
                    onSuccess()
                }
            }
        }
    }

    fun onDeleteLocalModelAsset(id: Long) {
        viewModelScope.launch {
            if (deleteLocalModelUseCase.isLastModel(id)) {
                _transientState.update { it.copy(showCannotDeleteLastModelAlert = true) }
                return@launch
            }

            val needingReassignment = deleteLocalModelUseCase.getModelTypesNeedingReassignment(id)
            if (needingReassignment.isNotEmpty()) {
                val localAssets = localModelAssetsFlow.first()
                val apiAssets = apiModelAssetsFlow.first()
                val deletedAsset = localAssets.find { it.metadata.id == id }
                val requiresVisionCompatibility =
                    ModelType.VISION in needingReassignment ||
                        deletedAsset?.metadata?.visionCapable == true
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeLocalModelId = id,
                    requireVisionCompatibility = requiresVisionCompatibility
                )

                _transientState.update { it.copy(
                    pendingDeletionModelId = id,
                    modelTypesNeedingReassignment = needingReassignment,
                    reassignmentOptions = options
                ) }
            } else {
                viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete local model asset", "Failed to delete asset")) {
                    deleteLocalModelUseCase(id).getOrThrow()
                }
            }
        }
    }

    fun onConfirmDeletionWithReassignment(replacementLocalConfigId: Long?, replacementApiConfigId: Long?) {
        val modelId = _transientState.value.pendingDeletionModelId
        val configId = _transientState.value.pendingDeletionConfigId
        val apiCredentialsId = _transientState.value.pendingDeletionApiCredentialsId
        val apiConfigId = _transientState.value.pendingDeletionApiConfigId

        if (_transientState.value.modelTypesNeedingReassignment.isNotEmpty() &&
            replacementLocalConfigId == null &&
            replacementApiConfigId == null
        ) {
            return
        }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to complete deletion with reassignment", "Failed to delete")) {
            when {
                modelId != null -> {
                    deleteLocalModelUseCase(modelId, replacementLocalConfigId, replacementApiConfigId).getOrThrow()
                }
                configId != null -> {
                    // Reassign slots first
                    val needingReassignment = _transientState.value.modelTypesNeedingReassignment
                    needingReassignment.forEach { modelType ->
                        setDefaultModelUseCase(modelType, replacementLocalConfigId, replacementApiConfigId)
                    }
                    deleteLocalModelConfigurationUseCase(configId).getOrThrow()
                }
                apiCredentialsId != null -> {
                    deleteApiCredentialsUseCase(apiCredentialsId, replacementLocalConfigId, replacementApiConfigId).getOrThrow()
                }
                apiConfigId != null -> {
                    deleteApiModelConfigurationUseCase(apiConfigId, replacementLocalConfigId, replacementApiConfigId).getOrThrow()
                }
            }
            onDismissDeletionSafety()
        }
    }

    fun onDismissDeletionSafety() {
        _transientState.update { it.copy(
            showCannotDeleteLastModelAlert = false,
            pendingDeletionModelId = null,
            pendingDeletionConfigId = null,
            pendingDeletionApiCredentialsId = null,
            pendingDeletionApiConfigId = null,
            modelTypesNeedingReassignment = emptyList(),
            reassignmentOptions = emptyList()
        ) }
    }

    fun onShowByokSheet(show: Boolean) {
        _transientState.update {
            if (show) {
                it.copy(
                    showByokSheet = true,
                    selectedApiModelAsset = null,
                    selectedApiModelConfig = null
                )
            } else {
                it.copy(showByokSheet = false)
            }
        }
    }

    fun onSelectApiModelAsset(id: Long?) {
        val asset = uiState.value.apiModels.find { it.credentialsId == id }
        onSelectApiModelAsset(asset)
    }

    fun onSelectApiModelAsset(asset: ApiModelAssetUi?) {
        _transientState.update { 
            it.copy(
                selectedApiModelAsset = asset, 
                selectedApiModelConfig = null 
            ) 
        }
    }

    fun onSelectApiModelConfig(config: ApiModelConfigUi?) {
        _transientState.update { it.copy(selectedApiModelConfig = config) }
    }

    fun onApiModelAssetFieldChange(asset: ApiModelAssetUi) {
        _transientState.update { it.copy(selectedApiModelAsset = asset) }
    }

    fun onApiModelConfigFieldChange(config: ApiModelConfigUi) {
        _transientState.update { it.copy(selectedApiModelConfig = config) }
    }

    fun onApiKeyChange(key: String) {
        _currentApiKey.value = key
    }

    fun onSaveApiCredentials(onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit) {
        val assetUi = _transientState.value.selectedApiModelAsset ?: return
        val apiKeyToSave = _currentApiKey.value
        val isNewAsset = assetUi.credentialsId == 0L
        
        val credentials = com.browntowndev.pocketcrew.domain.model.config.ApiCredentials(
            id = assetUi.credentialsId,
            displayName = assetUi.displayName,
            provider = assetUi.provider,
            modelId = assetUi.modelId,
            baseUrl = assetUi.baseUrl.takeIf { !it.isNullOrBlank() },
            isVision = assetUi.isVision,
            credentialAlias = assetUi.credentialAlias
        )

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API credentials", "Failed to save credentials")) {
            val id = saveApiCredentialsUseCase(credentials, apiKeyToSave)
            
            var configUi: ApiModelConfigUi? = null
            if (isNewAsset) {
                val defaultConfig = ApiModelConfiguration(
                    apiCredentialsId = id,
                    displayName = "Default Preset"
                )
                saveApiModelConfigurationUseCase(defaultConfig).onSuccess { configId ->
                    configUi = defaultConfig.copy(id = configId).toUi()
                }
            }
            
            val updatedAsset = assetUi.copy(
                credentialsId = id,
                configurations = configUi?.let { listOf(it) } ?: assetUi.configurations
            )
            
            _currentApiKey.value = "" // Clear immediately after save
            onSuccess(updatedAsset, configUi)
        }
    }

    fun onSaveApiModelConfig(onSuccess: () -> Unit) {
        val configUi = _transientState.value.selectedApiModelConfig ?: return
        val assetUi = _transientState.value.selectedApiModelAsset ?: return

        val config = ApiModelConfiguration(
            id = configUi.id,
            apiCredentialsId = assetUi.credentialsId,
            displayName = configUi.displayName,
            maxTokens = configUi.maxTokens.toIntOrNull() ?: 4096,
            contextWindow = configUi.contextWindow.toIntOrNull() ?: 4096,
            temperature = configUi.temperature,
            topP = configUi.topP,
            topK = configUi.topK.toIntOrNull() ?: 40,
            minP = configUi.minP,
            frequencyPenalty = configUi.frequencyPenalty,
            presencePenalty = configUi.presencePenalty,
            systemPrompt = configUi.systemPrompt,
            customHeaders = configUi.customHeaders
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .associate { it.key to it.value }
        )

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API configuration", "Failed to save configuration")) {
            saveApiModelConfigurationUseCase(config)
            onSuccess()
        }
    }

    fun onAddCustomHeader() {
        _transientState.update { state ->
            val config = state.selectedApiModelConfig ?: return@update state
            state.copy(
                selectedApiModelConfig = config.copy(
                    customHeaders = config.customHeaders + CustomHeaderUi()
                )
            )
        }
    }

    private fun buildReassignmentOptions(
        localAssets: List<LocalModelAsset>,
        apiAssets: List<ApiModelAsset>,
        excludeLocalModelId: Long? = null,
        excludeLocalConfigId: Long? = null,
        excludeApiCredentialsId: Long? = null,
        excludeApiConfigId: Long? = null,
        requireVisionCompatibility: Boolean
    ): List<ReassignmentOptionUi> {
        val options = mutableListOf<ReassignmentOptionUi>()

        localAssets.forEach { asset ->
            if (asset.metadata.id == excludeLocalModelId) return@forEach
            if (requireVisionCompatibility && !asset.metadata.visionCapable) return@forEach

            asset.configurations.forEach { config ->
                if (config.id == excludeLocalConfigId) return@forEach
                options.add(
                    ReassignmentOptionUi(
                        configId = config.id,
                        displayName = config.displayName.ifBlank { asset.metadata.huggingFaceModelName },
                        source = ModelSource.ON_DEVICE,
                        localModelId = asset.metadata.id
                    )
                )
            }
        }

        apiAssets.forEach { asset ->
            if (asset.credentials.id == excludeApiCredentialsId) return@forEach
            if (requireVisionCompatibility && !asset.credentials.isVision) return@forEach

            asset.configurations.forEach { config ->
                if (config.id == excludeApiConfigId) return@forEach
                val displayName = if (asset.credentials.displayName == config.displayName) {
                    asset.credentials.displayName
                } else {
                    "${asset.credentials.displayName} - ${config.displayName}"
                }
                options.add(
                    ReassignmentOptionUi(
                        configId = config.id,
                        displayName = displayName,
                        source = ModelSource.API,
                        providerName = asset.credentials.provider.name,
                        apiCredentialsId = asset.credentials.id
                    )
                )
            }
        }

        return options
    }

    fun onDeleteCustomHeader(index: Int) {
        _transientState.update { state ->
            val config = state.selectedApiModelConfig ?: return@update state
            state.copy(
                selectedApiModelConfig = config.copy(
                    customHeaders = config.customHeaders.toMutableList().apply { removeAt(index) }
                )
            )
        }
    }

    fun onCustomHeaderChange(index: Int, header: CustomHeaderUi) {
        _transientState.update { state ->
            val config = state.selectedApiModelConfig ?: return@update state
            state.copy(
                selectedApiModelConfig = config.copy(
                    customHeaders = config.customHeaders.toMutableList().apply { this[index] = header }
                )
            )
        }
    }

    fun onDeleteApiCredentials(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete API credentials", "Failed to delete credentials")) {
            deleteApiCredentialsUseCase(id)
            onSuccess()
        }
    }

    fun onDeleteApiModelConfig(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val needingReassignment = deleteApiModelConfigurationUseCase.getModelTypesNeedingReassignment(id)

            if (needingReassignment.isNotEmpty()) {
                val localAssets = localModelAssetsFlow.first()
                val apiAssets = apiModelAssetsFlow.first()
                val deletedAsset = apiAssets.find { asset ->
                    asset.configurations.any { config -> config.id == id }
                }
                val requiresVisionCompatibility =
                    ModelType.VISION in needingReassignment ||
                        deletedAsset?.isVision == true
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeApiConfigId = id,
                    requireVisionCompatibility = requiresVisionCompatibility
                )

                _transientState.update { it.copy(
                    pendingDeletionApiConfigId = id,
                    modelTypesNeedingReassignment = needingReassignment,
                    reassignmentOptions = options
                ) }
            } else {
                viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete API configuration", "Failed to delete configuration")) {
                    deleteApiModelConfigurationUseCase(id).getOrThrow()
                    onSuccess()
                }
            }
        }
    }

    fun onDeleteApiModelAsset(id: Long) {
        viewModelScope.launch {
            if (deleteApiCredentialsUseCase.isLastModel(id)) {
                _transientState.update { it.copy(showCannotDeleteLastModelAlert = true) }
                return@launch
            }

            val needingReassignment = deleteApiCredentialsUseCase.getModelTypesNeedingReassignment(id)
            if (needingReassignment.isNotEmpty()) {
                val localAssets = localModelAssetsFlow.first()
                val apiAssets = apiModelAssetsFlow.first()
                val deletedAsset = apiAssets.find { it.credentialsId == id }
                val requiresVisionCompatibility =
                    ModelType.VISION in needingReassignment ||
                        deletedAsset?.isVision == true
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeApiCredentialsId = id,
                    requireVisionCompatibility = requiresVisionCompatibility
                )

                _transientState.update { it.copy(
                    pendingDeletionApiCredentialsId = id,
                    modelTypesNeedingReassignment = needingReassignment,
                    reassignmentOptions = options
                ) }
            } else {
                viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete API provider", "Failed to delete provider")) {
                    deleteApiCredentialsUseCase(id).getOrThrow()
                }
            }
        }
    }

    fun onBackToByokList() {
        _transientState.update { it.copy(selectedApiModelAsset = null, selectedApiModelConfig = null) }
    }

    fun onCleanupCustomHeaders() {
        _transientState.update { state ->
            val config = state.selectedApiModelConfig ?: return@update state
            state.copy(
                selectedApiModelConfig = config.copy(
                    customHeaders = config.customHeaders.filter {
                        it.key.isNotBlank() && it.value.isNotBlank()
                    }
                )
            )
        }
    }

    fun onSetDefaultModel(modelType: ModelType, localConfigId: Long?, apiConfigId: Long?) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to set default model", "Failed to update default model")) {
            setDefaultModelUseCase(modelType, localConfigId, apiConfigId)
            onShowAssignmentDialog(false, null)
        }
    }

    fun onShowAssignmentDialog(show: Boolean, modelType: ModelType?) {
        _transientState.update { it.copy(showAssignmentDialog = show, editingAssignmentSlot = modelType) }
    }
}
