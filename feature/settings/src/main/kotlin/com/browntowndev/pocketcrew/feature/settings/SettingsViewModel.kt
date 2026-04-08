package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelsUseCase
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
    val apiCredentialDraft: ApiModelAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
    val selectedApiModelConfig: ApiModelConfigUi? = null,
    val discoveredApiModels: List<DiscoveredApiModel> = emptyList(),
    val discoveredApiModelScope: ApiModelDiscoveryScope? = null,
    val isDiscoveringApiModels: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilter: String? = null,
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
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

private data class ApiModelDiscoveryScope(
    val provider: ApiProvider,
    val baseUrl: String,
    val credentialAlias: String?,
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
    private val fetchApiProviderModelDetailUseCase: FetchApiProviderModelDetailUseCase,
    private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase,
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
        val selectedApiModelAsset = transientState.selectedApiModelAsset?.let { selected ->
            if (selected.credentialsId != 0L) {
                val dbAsset = apiAssets.find { it.credentials.id == selected.credentialsId }?.toUi()
                if (dbAsset != null) {
                    selected.copy(configurations = dbAsset.configurations)
                } else {
                    selected
                }
            } else {
                selected
            }
        }
        val apiCredentialDraft = transientState.apiCredentialDraft
        val activeApiAsset = apiCredentialDraft ?: selectedApiModelAsset
        val selectedApiModelParameterSupport = activeApiAsset?.let {
            ApiProviderModelPolicy.parameterSupport(
                provider = it.provider,
                modelId = it.modelId,
            )
        } ?: ApiModelParameterSupport.DEFAULT
        val selectedReusableApiCredential = transientState.selectedReusableApiCredentialAlias?.let { alias ->
            apiAssets
                .find { it.credentials.credentialAlias == alias }
                ?.toReusableUi()
                ?: transientState.selectedReusableApiCredentialName?.let { displayName ->
                    ReusableApiCredentialUi(
                        credentialsId = 0L,
                        displayName = displayName,
                        modelId = "",
                        credentialAlias = alias,
                    )
                }
        }
        val selectedDiscoveredApiModel = transientState.discoveredApiModels.find {
            it.id == activeApiAsset?.modelId
        }
        val selectedApiModelConfig = transientState.selectedApiModelConfig
            ?.withProviderDefaults(activeApiAsset)
            ?.withDiscoveredModelMetadata(selectedDiscoveredApiModel)

        val discoveredApiModelsUi = transientState.discoveredApiModels.map(DiscoveredApiModel::toUi)
        val filteredDiscoveredApiModels = discoveredApiModelsUi
            .filter { model ->
                val query = transientState.modelSearchQuery
                if (query.isNotBlank()) {
                    model.name?.contains(query, ignoreCase = true) == true ||
                    model.modelId.contains(query, ignoreCase = true)
                } else true
            }
            .filter { model ->
                val provider = transientState.modelProviderFilter
                if (provider != null) {
                    model.providerName == provider
                } else true
            }
            .sortedWith { a, b ->
                when (transientState.modelSortOption) {
                    ModelSortOption.NEWEST -> {
                        val aCreated = a.created ?: 0L
                        val bCreated = b.created ?: 0L
                        if (aCreated != bCreated) bCreated.compareTo(aCreated)
                        else (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                    }
                    ModelSortOption.PRICE_LOW_TO_HIGH -> {
                        val aHasPrice = a.promptPrice != null || a.completionPrice != null
                        val bHasPrice = b.promptPrice != null || b.completionPrice != null
                        
                        if (aHasPrice && bHasPrice) {
                            val aPrice = (a.promptPrice ?: 0.0) + (a.completionPrice ?: 0.0)
                            val bPrice = (b.promptPrice ?: 0.0) + (b.completionPrice ?: 0.0)
                            if (aPrice != bPrice) aPrice.compareTo(bPrice)
                            else (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                        } else if (aHasPrice) {
                            -1 // a comes first
                        } else if (bHasPrice) {
                            1 // b comes first
                        } else {
                            (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                        }
                    }
                    ModelSortOption.A_TO_Z -> {
                        (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                    }
                }
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
            localModels = localAssets.map { it.toUi() },
            availableToDownloadModels = transientState.availableToDownloadModels.map { it.toUi() },
            selectedLocalModelAsset = transientState.selectedLocalModelAsset?.let { selected ->
                if (selected.metadataId != 0L) {
                    val dbAsset = localAssets.find { it.metadata.id == selected.metadataId }?.toUi()
                    if (dbAsset != null) {
                        selected.copy(configurations = dbAsset.configurations)
                    } else {
                        selected
                    }
                } else {
                    selected
                }
            },
            selectedLocalModelConfig = transientState.selectedLocalModelConfig,
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
            selectedApiModelAsset = selectedApiModelAsset,
            apiCredentialDraft = apiCredentialDraft,
            selectedReusableApiCredential = selectedReusableApiCredential,
            selectedApiModelConfig = selectedApiModelConfig,
            selectedApiModelParameterSupport = selectedApiModelParameterSupport,
            discoveredApiModels = discoveredApiModelsUi,
            filteredDiscoveredApiModels = filteredDiscoveredApiModels,
            isDiscoveringApiModels = transientState.isDiscoveringApiModels,
            modelSearchQuery = transientState.modelSearchQuery,
            modelProviderFilter = transientState.modelProviderFilter,
            modelSortOption = transientState.modelSortOption,
            // Default model assignments
            defaultAssignments = defaultModels.map { def ->
                DefaultModelAssignmentUi(
                    modelType = def.modelType,
                    source = if (def.apiConfigId != null) ModelSource.API else ModelSource.ON_DEVICE,
                    currentModelName = def.displayName ?: "Unknown",
                    displayLabel = def.modelType.displayLabel,
                    providerName = def.providerName,
                    presetName = def.presetName
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
        baseUrl = credentials.baseUrl ?: credentials.provider.defaultBaseUrl(),
        isVision = credentials.isVision,
        credentialAlias = credentials.credentialAlias,
        configurations = configurations.map { it.toUi() }
    )

    private fun ApiModelAsset.toReusableUi() = ReusableApiCredentialUi(
        credentialsId = credentials.id,
        displayName = credentials.displayName,
        modelId = credentials.modelId,
        credentialAlias = credentials.credentialAlias,
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
        openRouterRouting = openRouterRouting,
        thinkingEnabled = false,
        systemPrompt = systemPrompt,
        reasoningEffort = reasoningEffort
    )

    private fun LocalModelAsset.toUi(): LocalModelAssetUi {
        val rawName = metadata.huggingFaceModelName
        val parts = rawName.split("/")
        val providerName = if (parts.size >= 2) parts[0] else "Unknown"
        val modelNamePart = if (parts.size >= 2) parts.drop(1).joinToString("/") else rawName
        
        val format = when (metadata.modelFileFormat) {
            ModelFileFormat.GGUF -> "GGUF"
            ModelFileFormat.LITERTLM -> "LiteRT"
            ModelFileFormat.TASK -> "Task"
        }
        
        val cleanName = modelNamePart
            .replace(Regex("-GGUF$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.gguf$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.litertlm$", RegexOption.IGNORE_CASE), "")
            .replace("-", " ")
        
        return LocalModelAssetUi(
            metadataId = metadata.id,
            huggingFaceModelName = rawName,
            friendlyName = cleanName.ifBlank { rawName },
            providerName = providerName,
            format = format,
            remoteFileName = metadata.remoteFileName,
            sizeInBytes = metadata.sizeInBytes,
            configurations = configurations.map { it.toUi() },
            visionCapable = metadata.visionCapable
        )
    }

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
        thinkingEnabled = thinkingEnabled,
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
                apiCredentialDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
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
            thinkingEnabled = configUi.thinkingEnabled,
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
                    apiCredentialDraft = null,
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                    discoveredApiModels = emptyList(),
                    discoveredApiModelScope = null,
                    isDiscoveringApiModels = false,
                    modelSearchQuery = "",
                    modelProviderFilter = null,
                    modelSortOption = ModelSortOption.A_TO_Z,
                    selectedApiModelConfig = null
                )
            } else {
                it.copy(showByokSheet = false)
            }
        }
    }

    fun onStartCreateApiModelAsset() {
        _currentApiKey.value = ""
        _transientState.update {
            it.copy(
                selectedApiModelAsset = null,
                apiCredentialDraft = blankApiModelAssetDraft(),
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                selectedApiModelConfig = null,
                discoveredApiModels = emptyList(),
                discoveredApiModelScope = null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilter = null,
                modelSortOption = ModelSortOption.A_TO_Z
            )
        }
    }

    fun onSelectApiModelAsset(id: Long?) {
        val asset = uiState.value.apiModels.find { it.credentialsId == id }
        onSelectApiModelAsset(asset)
    }

    fun onSelectApiModelAsset(asset: ApiModelAssetUi?) {
        _transientState.update { state ->
            val keepDiscoveredModels =
                state.selectedApiModelAsset.matchesDiscoveryScope(asset) ||
                    state.discoveredApiModelScope.matches(asset)
            val retainedModels = if (keepDiscoveredModels) {
                state.discoveredApiModels
            } else {
                emptyList()
            }
            val mergedModels = retainedModels.mergeSelectedModel(asset)
            state.copy(
                selectedApiModelAsset = asset,
                apiCredentialDraft = asset,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                selectedApiModelConfig = null,
                discoveredApiModels = mergedModels,
                discoveredApiModelScope = if (keepDiscoveredModels) state.discoveredApiModelScope else null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilter = null,
                modelSortOption = ModelSortOption.A_TO_Z
            )
        }
        maybeFetchSelectedXaiModelMetadata(asset)
    }

    fun onSelectApiModelConfig(config: ApiModelConfigUi?) {
        _transientState.update { state ->
            val asset = state.apiCredentialDraft ?: state.selectedApiModelAsset
            val normalizedConfig = config
                ?.let { draft ->
                    if (draft.credentialsId == 0L && asset != null) {
                        draft.copy(credentialsId = asset.credentialsId)
                    } else {
                        draft
                    }
                }
                ?.withProviderDefaults(asset)
                ?.withDiscoveredModelMetadata(
                    state.discoveredModelFor(asset)
                )
            state.copy(selectedApiModelConfig = normalizedConfig)
        }
    }

    fun onApiModelAssetFieldChange(asset: ApiModelAssetUi) {
        _transientState.update {
            val existingDraft = it.apiCredentialDraft
            val previousProvider = existingDraft?.provider
            val previousCredentialsId = existingDraft?.credentialsId
            val previousBaseUrl = existingDraft?.baseUrl?.trim().orEmpty()
            val discoveryScopeChanged =
                previousProvider != asset.provider ||
                    previousCredentialsId != asset.credentialsId ||
                    previousBaseUrl != asset.baseUrl?.trim().orEmpty()
            val existingModels = if (discoveryScopeChanged) {
                emptyList()
            } else {
                it.discoveredApiModels
            }
            val updatedModels = existingModels.mergeSelectedModel(asset)
            it.copy(
                apiCredentialDraft = asset,
                selectedReusableApiCredentialAlias = if (previousProvider != asset.provider) null else it.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (previousProvider != asset.provider) null else it.selectedReusableApiCredentialName,
                discoveredApiModels = updatedModels,
                discoveredApiModelScope = if (discoveryScopeChanged) null else it.discoveredApiModelScope
            )
        }
        maybeFetchSelectedXaiModelMetadata(asset)
    }

    fun onApiModelConfigFieldChange(config: ApiModelConfigUi) {
        _transientState.update { state ->
            val asset = state.apiCredentialDraft ?: state.selectedApiModelAsset
            val normalizedConfig = config
                .withProviderDefaults(asset)
                .withDiscoveredModelMetadata(state.discoveredModelFor(asset))
            state.copy(selectedApiModelConfig = normalizedConfig)
        }
    }

    fun onApiKeyChange(key: String) {
        _currentApiKey.value = key
        if (
            key.isNotBlank() &&
            _transientState.value.apiCredentialDraft?.credentialsId == 0L
        ) {
            _transientState.update {
                it.copy(
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                )
            }
        }
    }

    fun onSelectReusableApiCredential(id: Long?) {
        val reusableCredential = uiState.value.apiModels
            .find { it.credentialsId == id }
            ?.let { asset ->
                ReusableApiCredentialUi(
                    credentialsId = asset.credentialsId,
                    displayName = asset.displayName,
                    modelId = asset.modelId,
                    credentialAlias = asset.credentialAlias,
                )
            }
        _currentApiKey.value = ""
        _transientState.update {
            it.copy(
                selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                selectedReusableApiCredentialName = reusableCredential?.displayName,
            )
        }
    }

    fun onSaveApiCredentials(onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit) {
        val transientState = _transientState.value
        val assetUi = transientState.apiCredentialDraft ?: return
        val apiKeyToSave = _currentApiKey.value
        val isNewAsset = assetUi.credentialsId == 0L
        val defaultReasoningEffort = assetUi.defaultReasoningEffort()
        val sourceCredentialAlias = if (apiKeyToSave.isBlank()) {
            transientState.selectedReusableApiCredentialAlias
        } else {
            null
        }
        
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API credentials", "Failed to save credentials")) {
            val finalAlias = if (isNewAsset) {
                generateUniqueAlias(assetUi.provider.name, assetUi.modelId)
            } else {
                assetUi.credentialAlias
            }

            val credentials = com.browntowndev.pocketcrew.domain.model.config.ApiCredentials(
                id = assetUi.credentialsId,
                displayName = assetUi.displayName,
                provider = assetUi.provider,
                modelId = assetUi.modelId,
                baseUrl = assetUi.baseUrl.takeIf { !it.isNullOrBlank() } ?: assetUi.provider.defaultBaseUrl(),
                isVision = assetUi.isVision,
                credentialAlias = finalAlias
            )

            val id = saveApiCredentialsUseCase(
                credentials = credentials,
                apiKey = apiKeyToSave,
                sourceCredentialAlias = sourceCredentialAlias,
            )

            val persistedAsset = apiModelAssetsFlow
                .first { assets -> assets.any { it.credentials.id == id } }
                .first { it.credentials.id == id }
            var configUi: ApiModelConfigUi? = null
            if (isNewAsset) {
                val presetName = generateUniqueApiPresetName(persistedAsset.configurations)
                val defaultConfig = ApiModelConfiguration(
                    apiCredentialsId = id,
                    displayName = presetName,
                    reasoningEffort = defaultReasoningEffort,
                )
                val configId = saveApiModelConfigurationUseCase(defaultConfig).getOrThrow()
                val persistedAssetWithConfig = apiModelAssetsFlow
                    .first { assets ->
                        assets.any { apiAsset ->
                            apiAsset.credentials.id == id &&
                                apiAsset.configurations.any { it.id == configId }
                        }
                    }
                    .first { it.credentials.id == id }
                configUi = persistedAssetWithConfig.configurations
                    .firstOrNull { it.id == configId }
                    ?.toUi()
                    ?: defaultConfig.copy(id = configId).toUi()
            }

            val updatedAsset = apiModelAssetsFlow
                .first { assets -> assets.any { it.credentials.id == id } }
                .first { it.credentials.id == id }
                .toUi()

            _currentApiKey.value = ""
            _transientState.update { state ->
                state.copy(
                    selectedApiModelAsset = updatedAsset,
                    apiCredentialDraft = updatedAsset,
                    selectedApiModelConfig = configUi,
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                    discoveredApiModels = state.discoveredApiModels.mergeSelectedModel(updatedAsset),
                    discoveredApiModelScope = updatedAsset.toDiscoveryScope(),
                )
            }
            onSuccess(updatedAsset, configUi)
        }
    }

    private suspend fun generateUniqueAlias(provider: String, modelId: String): String {
        val baseSlug = "${provider.lowercase()}-${modelId.lowercase()}"
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        
        val existingAliases = apiModelAssetsFlow.first().map { it.credentials.credentialAlias }.toSet()
        
        if (baseSlug !in existingAliases) return baseSlug
        
        var counter = 2
        while ("$baseSlug-$counter" in existingAliases) {
            counter++
        }
        return "$baseSlug-$counter"
    }

    private fun generateUniqueApiPresetName(
        existingConfigs: List<ApiModelConfiguration>
    ): String {
        val existingNames = existingConfigs.map(ApiModelConfiguration::displayName).toSet()
        val baseName = "Default Preset"
        if (baseName !in existingNames) return baseName

        var counter = 2
        while ("$baseName $counter" in existingNames) {
            counter++
        }
        return "$baseName $counter"
    }

    fun onSaveApiModelConfig(onSuccess: () -> Unit) {
        val assetUi = _transientState.value.apiCredentialDraft ?: _transientState.value.selectedApiModelAsset ?: return
        val configUi = (_transientState.value.selectedApiModelConfig ?: return)
            .withProviderDefaults(assetUi)
            .withDiscoveredModelMetadata(_transientState.value.discoveredModelFor(assetUi))
        val defaultReasoningEffort = assetUi.defaultReasoningEffort()
        val parentCredentialsId = configUi.credentialsId.takeIf { it != 0L } ?: assetUi.credentialsId

        val config = ApiModelConfiguration(
            id = configUi.id,
            apiCredentialsId = parentCredentialsId,
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
            reasoningEffort = configUi.reasoningEffort ?: defaultReasoningEffort,
            customHeaders = configUi.customHeaders
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .associate { it.key to it.value },
            openRouterRouting = if (assetUi.provider == ApiProvider.OPENROUTER) {
                configUi.openRouterRouting
            } else {
                OpenRouterRoutingConfiguration()
            }
        )

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API configuration", "Failed to save configuration")) {
            saveApiModelConfigurationUseCase(config).getOrThrow()
            onSuccess()
        }
    }

    fun onUpdateModelSearchQuery(query: String) {
        _transientState.update { it.copy(modelSearchQuery = query) }
    }

    fun onUpdateModelProviderFilter(provider: String?) {
        _transientState.update { it.copy(modelProviderFilter = provider) }
    }

    fun onUpdateModelSortOption(option: ModelSortOption) {
        _transientState.update { it.copy(modelSortOption = option) }
    }

    fun onFetchApiModels() {
        val assetUi = _transientState.value.apiCredentialDraft ?: _transientState.value.selectedApiModelAsset ?: return
        val credentialAlias = _transientState.value.selectedReusableApiCredentialAlias
            ?.takeIf { it.isNotBlank() }
            ?: assetUi.credentialAlias.takeIf { it.isNotBlank() }
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        _transientState.update { it.copy(isDiscoveringApiModels = true) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch provider models", "Failed to fetch models")) {
            try {
                val models = fetchApiProviderModelsUseCase(
                    provider = assetUi.provider,
                    currentApiKey = _currentApiKey.value,
                    credentialAlias = credentialAlias,
                    baseUrl = assetUi.baseUrl
                )
                val selectedModelId = assetUi.modelId.takeIf(String::isNotBlank)
                val selectedModelDetail = if (
                    assetUi.provider == ApiProvider.XAI &&
                    selectedModelId != null &&
                    models.none { it.id == selectedModelId && it.contextWindowTokens != null }
                ) {
                    fetchApiProviderModelDetailUseCase(
                        provider = assetUi.provider,
                        modelId = selectedModelId,
                        currentApiKey = _currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = assetUi.baseUrl,
                    )
                } else {
                    null
                }

                _transientState.update { state ->
                    val selectedAsset = state.apiCredentialDraft ?: state.selectedApiModelAsset
                    val selectedModelIdInState = selectedAsset?.modelId
                    val mergedModels = buildList {
                        selectedModelDetail?.let(::add)
                        if (
                            !selectedModelIdInState.isNullOrBlank() &&
                            models.none { it.id == selectedModelIdInState } &&
                            selectedModelDetail?.id != selectedModelIdInState
                        ) {
                            add(DiscoveredApiModel(id = selectedModelIdInState))
                        }
                        addAll(models)
                    }
                        .distinctBy(DiscoveredApiModel::id)

                    state.copy(
                        discoveredApiModels = mergedModels,
                        discoveredApiModelScope = selectedAsset?.toDiscoveryScope(),
                        selectedApiModelConfig = state.selectedApiModelConfig
                            ?.withDiscoveredModelMetadata(mergedModels.find { it.id == selectedModelIdInState })
                    )
                }
            } finally {
                _transientState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    private fun ApiModelAssetUi.defaultReasoningEffort(): ApiReasoningEffort? {
        val parameterSupport = ApiProviderModelPolicy.parameterSupport(
            provider = provider,
            modelId = modelId,
        )
        return if (parameterSupport.supportsReasoningEffort) {
            parameterSupport.reasoningPolicy.defaultEffort
        } else {
            null
        }
    }

    private fun ApiModelConfigUi.withProviderDefaults(asset: ApiModelAssetUi?): ApiModelConfigUi {
        if (asset == null) {
            return this
        }
        val parameterSupport = ApiProviderModelPolicy.parameterSupport(
            provider = asset.provider,
            modelId = asset.modelId,
        )
        if (!parameterSupport.supportsReasoningEffort) {
            return copy(reasoningEffort = null)
        }
        if (reasoningEffort != null) {
            return this
        }
        return copy(
            reasoningEffort = parameterSupport.reasoningPolicy.defaultEffort
        )
    }

    private fun ApiModelConfigUi.withDiscoveredModelMetadata(
        discoveredModel: DiscoveredApiModel?
    ): ApiModelConfigUi {
        val currentMaxTokens = maxTokens.toIntOrNull()
        val currentContextWindow = contextWindow.toIntOrNull()

        var updated = this
        discoveredModel?.contextWindowTokens?.let { contextWindowTokens ->
            updated = updated.copy(contextWindow = contextWindowTokens.toString())
        }

        val suggestedMaxTokens = discoveredModel?.suggestedDefaultMaxTokens()
        if (
            suggestedMaxTokens != null &&
            shouldAdoptSuggestedMaxTokens(
                currentMaxTokens = currentMaxTokens,
                currentContextWindow = currentContextWindow,
                discoveredModel = discoveredModel,
            )
        ) {
            updated = updated.copy(maxTokens = suggestedMaxTokens.toString())
        }
        return updated
    }

    private fun shouldAdoptSuggestedMaxTokens(
        currentMaxTokens: Int?,
        currentContextWindow: Int?,
        discoveredModel: DiscoveredApiModel,
    ): Boolean {
        if (currentMaxTokens == null || currentMaxTokens <= 0) {
            return true
        }
        if (currentMaxTokens == 4_096) {
            return true
        }
        if (currentContextWindow != null && currentMaxTokens == currentContextWindow) {
            return true
        }
        if (
            discoveredModel.maxOutputTokens != null &&
            currentMaxTokens == discoveredModel.maxOutputTokens
        ) {
            return true
        }
        if (
            discoveredModel.contextWindowTokens?.let { discoveredContextWindow ->
                currentMaxTokens >= discoveredContextWindow
            } == true
        ) {
            return true
        }
        return false
    }

    private fun DiscoveredApiModel.suggestedDefaultMaxTokens(): Int? {
        val upperBound = listOfNotNull(maxOutputTokens, contextWindowTokens)
            .minOrNull()
            ?: return null
        return maxOf(1, upperBound / 4)
    }

    private fun TransientState.discoveredModelFor(asset: ApiModelAssetUi?): DiscoveredApiModel? =
        discoveredApiModels.find { it.id == asset?.modelId }

    private fun ApiModelAssetUi?.matchesDiscoveryScope(other: ApiModelAssetUi?): Boolean {
        if (this == null || other == null) {
            return false
        }
        if (provider != other.provider) {
            return false
        }
        if (baseUrl?.trim().orEmpty() != other.baseUrl?.trim().orEmpty()) {
            return false
        }
        val currentAlias = credentialAlias.takeIf(String::isNotBlank)
        val nextAlias = other.credentialAlias.takeIf(String::isNotBlank)
        return currentAlias == null || nextAlias == null || currentAlias == nextAlias
    }

    private fun ApiModelDiscoveryScope?.matches(asset: ApiModelAssetUi?): Boolean {
        if (this == null || asset == null) {
            return false
        }
        if (provider != asset.provider) {
            return false
        }
        if (baseUrl != asset.baseUrl.normalizedBaseUrl()) {
            return false
        }
        val assetAlias = asset.credentialAlias.takeIf(String::isNotBlank)
        return credentialAlias == null || assetAlias == null || credentialAlias == assetAlias
    }

    private fun ApiModelAssetUi.toDiscoveryScope(): ApiModelDiscoveryScope =
        ApiModelDiscoveryScope(
            provider = provider,
            baseUrl = baseUrl.normalizedBaseUrl(),
            credentialAlias = credentialAlias.takeIf(String::isNotBlank),
        )

    private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()

    private fun blankApiModelAssetDraft(): ApiModelAssetUi = ApiModelAssetUi(
        credentialsId = 0L,
        displayName = "",
        provider = ApiProvider.ANTHROPIC,
        modelId = "",
        baseUrl = ApiProvider.ANTHROPIC.defaultBaseUrl(),
        isVision = false,
        credentialAlias = "",
        configurations = emptyList()
    )

    private fun maybeFetchSelectedXaiModelMetadata(asset: ApiModelAssetUi?) {
        if (asset?.provider != ApiProvider.XAI || asset.modelId.isBlank()) {
            return
        }
        val currentState = _transientState.value
        if (currentState.discoveredModelFor(asset)?.contextWindowTokens != null) {
            return
        }
        val credentialAlias = currentState.selectedReusableApiCredentialAlias
            ?.takeIf { it.isNotBlank() }
            ?: asset.credentialAlias.takeIf { it.isNotBlank() }
        if (_currentApiKey.value.isBlank() && credentialAlias == null) {
            return
        }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch xAI model details", "Failed to fetch model details")) {
            val modelDetail = fetchApiProviderModelDetailUseCase(
                provider = asset.provider,
                modelId = asset.modelId,
                currentApiKey = _currentApiKey.value,
                credentialAlias = credentialAlias,
                baseUrl = asset.baseUrl,
            ) ?: return@launch

            _transientState.update { state ->
                val mergedModels = listOf(modelDetail) + state.discoveredApiModels
                val dedupedModels = mergedModels.distinctBy(DiscoveredApiModel::id)
                val activeAsset = state.apiCredentialDraft ?: state.selectedApiModelAsset
                state.copy(
                    discoveredApiModels = dedupedModels,
                    discoveredApiModelScope = activeAsset?.toDiscoveryScope()
                        ?: state.discoveredApiModelScope,
                    selectedApiModelConfig = state.selectedApiModelConfig
                        ?.withDiscoveredModelMetadata(
                            dedupedModels.find { it.id == activeAsset?.modelId }
                        )
                )
            }
        }
    }

    private fun List<DiscoveredApiModel>.mergeSelectedModel(
        asset: ApiModelAssetUi?
    ): List<DiscoveredApiModel> = when {
        asset == null || asset.modelId.isBlank() -> this
        any { it.id == asset.modelId } -> this
        else -> listOf(DiscoveredApiModel(id = asset.modelId)) + this
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
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeApiConfigId = id,
                    requireVisionCompatibility = false
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
                
                val options = buildReassignmentOptions(
                    localAssets = localAssets,
                    apiAssets = apiAssets,
                    excludeApiCredentialsId = id,
                    requireVisionCompatibility = false
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
        _currentApiKey.value = ""
        _transientState.update {
            it.copy(
                selectedApiModelAsset = null,
                apiCredentialDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                selectedApiModelConfig = null,
            )
        }
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
