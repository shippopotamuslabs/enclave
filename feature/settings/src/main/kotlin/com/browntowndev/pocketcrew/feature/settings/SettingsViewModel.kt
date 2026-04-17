package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.usecase.settings.PreparedModelDeletion
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class PersistedSettingsBundle(
    val settings: com.browntowndev.pocketcrew.domain.port.repository.SettingsData,
    val localAssets: List<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>,
    val apiAssets: List<com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset>,
    val defaultModels: List<com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment>,
)

private data class TransientSettingsBundle(
    val sheetVisibility: SheetVisibilityState,
    val memories: List<StoredMemory>,
    val feedbackText: String,
    val localModelsState: LocalModelsTransientState,
    val apiState: ApiProvidersTransientState,
    val searchSkillState: SearchSkillTransientState,
    val assignmentState: AssignmentDialogTransientState,
    val deletionState: DeletionTransientState,
)

private data class SheetTransientBundle(
    val sheetVisibility: SheetVisibilityState,
    val memories: List<StoredMemory>,
    val feedbackText: String,
    val localModelsState: LocalModelsTransientState,
)

private data class DialogTransientBundle(
    val searchSkillState: SearchSkillTransientState,
    val apiState: ApiProvidersTransientState,
    val assignmentState: AssignmentDialogTransientState,
    val deletionState: DeletionTransientState,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
    private val settingsUiStateFactory: SettingsUiStateFactory,
    private val localModelAssetUiMapper: LocalModelAssetUiMapper,
    private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
    private val reassignmentOptionUiMapper: ReassignmentOptionUiMapper,
    private val errorHandler: ViewModelErrorHandler,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _sheetVisibility = MutableStateFlow(SheetVisibilityState())
    private val _memories = MutableStateFlow(emptyList<StoredMemory>())
    private val _feedbackText = MutableStateFlow("")
    private val _localModelsState = MutableStateFlow(LocalModelsTransientState())
    private val _apiState = MutableStateFlow(ApiProvidersTransientState())
    private val _searchSkillState = MutableStateFlow(SearchSkillTransientState())
    private val _assignmentState = MutableStateFlow(AssignmentDialogTransientState())
    private val _deletionState = MutableStateFlow(DeletionTransientState())
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val _currentApiKey = MutableStateFlow("")
    private val _currentTavilyApiKey = MutableStateFlow("")
    val currentApiKey: StateFlow<String> = _currentApiKey
    val currentTavilyApiKey: StateFlow<String> = _currentTavilyApiKey
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val preferencesUseCases = settingsUseCases.preferences
    private val localModelUseCases = settingsUseCases.localModels
    private val apiProviderUseCases = settingsUseCases.apiProviders
    private val assignmentUseCases = settingsUseCases.assignments
    private val deletionUseCases = settingsUseCases.deletion

    private val localModelAssetsFlow = localModelUseCases.getLocalModelAssets()
    private val apiModelAssetsFlow = apiProviderUseCases.getApiModelAssets()
    private val persistedSettingsBundle = combine(
        settingsUseCases.getSettings(),
        localModelAssetsFlow,
        apiModelAssetsFlow,
        assignmentUseCases.getDefaultModels(),
    ) { settings, localAssets, apiAssets, defaultModels ->
        PersistedSettingsBundle(
            settings = settings,
            localAssets = localAssets,
            apiAssets = apiAssets,
            defaultModels = defaultModels,
        )
    }
    private val transientSheetBundle = combine(
        _sheetVisibility,
        _memories,
        _feedbackText,
        _localModelsState,
    ) { sheetVisibility, memories, feedbackText, localModelsState ->
        SheetTransientBundle(
            sheetVisibility = sheetVisibility,
            memories = memories,
            feedbackText = feedbackText,
            localModelsState = localModelsState,
        )
    }
    private val transientDialogBundle = combine(
        _searchSkillState,
        _apiState,
        _assignmentState,
        _deletionState,
    ) { searchSkillState, apiState, assignmentState, deletionState ->
        DialogTransientBundle(
            searchSkillState = searchSkillState,
            apiState = apiState,
            assignmentState = assignmentState,
            deletionState = deletionState,
        )
    }
    private val transientSettingsBundle = combine(
        transientSheetBundle,
        transientDialogBundle,
    ) { sheetBundle, dialogBundle ->
        TransientSettingsBundle(
            sheetVisibility = sheetBundle.sheetVisibility,
            memories = sheetBundle.memories,
            feedbackText = sheetBundle.feedbackText,
            localModelsState = sheetBundle.localModelsState,
            apiState = dialogBundle.apiState,
            searchSkillState = dialogBundle.searchSkillState,
            assignmentState = dialogBundle.assignmentState,
            deletionState = dialogBundle.deletionState,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        persistedSettingsBundle,
        transientSettingsBundle,
    ) { persisted, transient ->
        settingsUiStateFactory.create(
            persistedSettings = persisted.settings,
            localAssets = persisted.localAssets,
            apiAssets = persisted.apiAssets,
            defaultModels = persisted.defaultModels,
            sheetVisibility = transient.sheetVisibility,
            memories = transient.memories,
            feedbackText = transient.feedbackText,
            localModelsState = transient.localModelsState,
            apiState = transient.apiState,
            searchSkillState = transient.searchSkillState,
            assignmentsState = transient.assignmentState,
            deletionState = transient.deletionState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    /**
     * When local model assets change (e.g., a re-download completes and restores
     * a soft-deleted model), remove it from the "Available for Download" list
     * so it doesn't appear in both sections simultaneously.
     */
    init {
        viewModelScope.launch {
            localModelAssetsFlow.collect { activeAssets ->
                val activeSha256s = activeAssets.map { it.metadata.sha256 }.toSet()
                _localModelsState.update { current ->
                    val filtered = current.availableToDownloadModels.filterNot { restoredAsset ->
                        restoredAsset.metadata.sha256 in activeSha256s
                    }
                    if (filtered.size != current.availableToDownloadModels.size) {
                        current.copy(availableToDownloadModels = filtered)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun onThemeChange(theme: AppTheme) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update theme", "Failed to update theme")) {
            preferencesUseCases.updateTheme(theme)
        }
    }

    fun onHapticPressChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update haptic press setting", "Failed to update setting")) {
            preferencesUseCases.updateHapticPress(enabled)
        }
    }

    fun onHapticResponseChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update haptic response setting", "Failed to update setting")) {
            preferencesUseCases.updateHapticResponse(enabled)
        }
    }

    fun onAlwaysUseVisionModelChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update vision routing setting", "Failed to update setting")) {
            preferencesUseCases.updateAlwaysUseVisionModel(enabled)
        }
    }

    fun onCompactionProviderTypeChange(type: com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update compaction provider", "Failed to update setting")) {
            preferencesUseCases.updateCompactionProviderType(type)
        }
    }

    fun onCompactionApiModelIdChange(modelId: String?) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update compaction model", "Failed to update setting")) {
            preferencesUseCases.updateCompactionApiModelId(modelId)
        }
    }

    fun onShowCustomizationSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(customization = show) }
    }

    fun onCustomizationEnabledChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update customization setting", "Failed to update setting")) {
            preferencesUseCases.updateCustomizationEnabled(enabled)
        }
    }

    fun onPromptOptionChange(option: SystemPromptOption) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update prompt option", "Failed to update setting")) {
            preferencesUseCases.updateSelectedPromptOption(option)
        }
    }

    fun onCustomPromptTextChange(text: String) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update custom prompt", "Failed to update setting")) {
            preferencesUseCases.updateCustomPromptText(text)
        }
    }

    fun onSaveCustomization() {
        _sheetVisibility.update { it.copy(customization = false) }
    }

    fun onShowDataControlsSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(dataControls = show) }
    }

    fun onAllowMemoriesChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update memories setting", "Failed to update setting")) {
            preferencesUseCases.updateAllowMemories(enabled)
        }
    }

    fun onDeleteAllConversations() = Unit

    fun onDeleteAllMemories() {
        _memories.value = emptyList()
    }

    fun onShowMemoriesSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(memories = show) }
    }

    fun onDeleteMemory(memoryId: String) {
        _memories.update { memories -> memories.filterNot { it.id == memoryId } }
    }

    fun onShowFeedbackSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(feedback = show) }
    }

    fun onShowVisionSettingsSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(visionSettings = show) }
    }

    fun onFeedbackTextChange(text: String) {
        _feedbackText.value = text
    }

    fun onSubmitFeedback() {
        _feedbackText.value = ""
        _sheetVisibility.update { it.copy(feedback = false) }
    }

    fun onShowModelConfigSheet(show: Boolean) {
        if (!show) {
            _localModelsState.update { it.copy(isSheetOpen = false) }
            return
        }

        viewModelScope.launch {
            val softDeleted = localModelUseCases.getRestorableLocalModels()
            _localModelsState.update {
                it.copy(
                    isSheetOpen = true,
                    availableToDownloadModels = softDeleted,
                    selectedAsset = null,
                    configDraft = null,
                )
            }
        }
    }

    fun onSelectModelType(modelType: ModelType) {
        viewModelScope.launch {
            val selection = assignmentUseCases.resolveAssignedModelSelection(modelType) ?: return@launch
            selection.localAsset?.let {
                onSelectLocalModelAsset(localModelAssetUiMapper.map(it))
                onSelectLocalModelConfig(selection.localConfig?.let(localModelAssetUiMapper::mapConfig))
            }
            selection.apiAsset?.let {
                onSelectApiModelAsset(apiModelAssetUiMapper.map(it))
                onSelectApiModelConfig(selection.apiConfig?.let(apiModelAssetUiMapper::mapConfig))
            }
        }
    }

    fun onClearSelectedModel() {
        _localModelsState.update {
            it.copy(selectedAsset = null, configDraft = null)
        }
        _apiState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
            )
        }
    }

    fun onSelectLocalModelAsset(asset: LocalModelAssetUi?) {
        _localModelsState.update { it.copy(selectedAsset = asset, configDraft = null) }
    }

    fun onSelectLocalModelConfig(config: LocalModelConfigUi?) {
        _localModelsState.update { it.copy(configDraft = config ?: LocalModelConfigUi()) }
    }

    fun onClearSelectedLocalModel() {
        _localModelsState.update { it.copy(selectedAsset = null, configDraft = null) }
    }

    fun onLocalModelConfigFieldChange(config: LocalModelConfigUi) {
        if (_localModelsState.value.configDraft?.isSystemPreset == true) return
        _localModelsState.update { it.copy(configDraft = config) }
    }

    fun onSaveLocalModelConfig(onSuccess: () -> Unit) {
        val selectedAsset = _localModelsState.value.selectedAsset ?: return
        val configDraft = _localModelsState.value.configDraft ?: return
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save local model configuration", "Failed to save configuration")) {
            localModelUseCases.saveLocalModelPreset(
                localModelId = selectedAsset.metadataId,
                draft = configDraft.toDomain(),
            ).getOrThrow()
            onSuccess()
        }
    }

    fun onDeleteLocalModelConfig(id: LocalModelConfigurationId, onSuccess: () -> Unit) {
        prepareDeletion(
            target = ModelDeletionTarget.LocalModelPreset(id),
            failureMessage = "Failed to delete local model configuration",
            userMessage = "Failed to delete configuration",
            onImmediateSuccess = onSuccess,
        )
    }

    fun onDeleteLocalModelAsset(id: LocalModelId) {
        prepareDeletion(
            target = ModelDeletionTarget.LocalModelAsset(id),
            failureMessage = "Failed to delete local model asset",
            userMessage = "Failed to delete asset",
        )
    }

    fun onConfirmDeletionWithReassignment(replacementLocalConfigId: LocalModelConfigurationId?, replacementApiConfigId: ApiModelConfigurationId?) {
        val deletionState = _deletionState.value
        val pendingTarget = deletionState.pendingTarget ?: return
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to complete deletion with reassignment", "Failed to delete")) {
            deletionUseCases.executeModelDeletionWithReassignment(
                target = pendingTarget.toDomain(),
                modelTypesNeedingReassignment = deletionState.modelTypesNeedingReassignment,
                replacementLocalConfigId = replacementLocalConfigId,
                replacementApiConfigId = replacementApiConfigId,
            ).getOrThrow()
            onDismissDeletionSafety()
        }
    }

    fun onDismissDeletionSafety() {
        _deletionState.value = DeletionTransientState()
    }

    fun onShowByokSheet(show: Boolean) {
        if (!show) {
            _apiState.update { it.copy(isSheetOpen = false) }
            return
        }

        _apiState.update {
            it.copy(
                isSheetOpen = true,
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
                discoveredApiModels = emptyList(),
                discoveredApiModelScope = null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilter = null,
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
        _searchSkillState.value = SearchSkillTransientState()
    }

    fun onStartCreateApiModelAsset() {
        _currentApiKey.value = ""
        _searchSkillState.value = SearchSkillTransientState()
        _apiState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = blankApiModelAssetDraft(),
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
                discoveredApiModels = emptyList(),
                discoveredApiModelScope = null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilter = null,
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
    }

    fun onStartConfigureSearchSkill() {
        val persistedState = uiState.value.searchSkillEditor
        _currentTavilyApiKey.value = ""
        _apiState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
            )
        }
        _searchSkillState.value = SearchSkillTransientState(
            isEditing = true,
            enabled = persistedState.enabled,
        )
    }

    fun onSelectApiModelAsset(id: ApiCredentialsId?) {
        val asset = uiState.value.apiProvidersSheet.assets.find { it.credentialsId == id }
        onSelectApiModelAsset(asset)
    }

    fun onSelectApiModelAsset(asset: ApiModelAssetUi?) {
        _apiState.update { state ->
            val keepDiscoveredModels =
                state.selectedAsset.matchesDiscoveryScope(asset) || state.discoveredApiModelScope.matches(asset)
            val retainedModels = if (keepDiscoveredModels) state.discoveredApiModels else emptyList()
            val normalizedAsset = state.normalizeVisionCapability(asset, retainedModels)
            state.copy(
                selectedAsset = asset,
                assetDraft = normalizedAsset,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
                discoveredApiModels = retainedModels.mergeSelectedModel(normalizedAsset),
                discoveredApiModelScope = if (keepDiscoveredModels) state.discoveredApiModelScope else null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilter = null,
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
        maybeFetchSelectedXaiModelMetadata(asset)
    }

    fun onSelectApiModelConfig(config: ApiModelConfigUi?) {
        _apiState.update { state ->
            val activeAsset = state.assetDraft ?: state.selectedAsset
            val normalizedConfig = config?.let { draft ->
                if (draft.credentialsId.value.isEmpty() && activeAsset != null) {
                    draft.copy(credentialsId = activeAsset.credentialsId)
                } else {
                    draft
                }
            }
            state.copy(presetDraft = normalizedConfig)
        }
    }

    fun onApiModelAssetFieldChange(asset: ApiModelAssetUi) {
        _apiState.update {
            val existingDraft = it.assetDraft
            val discoveryScopeChanged =
                existingDraft == null ||
                    existingDraft.provider != asset.provider ||
                    existingDraft.credentialsId != asset.credentialsId ||
                    existingDraft.baseUrl.normalizedBaseUrl() != asset.baseUrl.normalizedBaseUrl()
            val existingModels = if (discoveryScopeChanged) emptyList() else it.discoveredApiModels
            val normalizedAsset = it.normalizeVisionCapability(asset, existingModels)
            it.copy(
                assetDraft = normalizedAsset,
                selectedReusableApiCredentialAlias = if (existingDraft?.provider != asset.provider) null else it.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (existingDraft?.provider != asset.provider) null else it.selectedReusableApiCredentialName,
                discoveredApiModels = existingModels.mergeSelectedModel(normalizedAsset),
                discoveredApiModelScope = if (discoveryScopeChanged) null else it.discoveredApiModelScope,
            )
        }
        maybeFetchSelectedXaiModelMetadata(asset)
    }

    fun onApiModelConfigFieldChange(config: ApiModelConfigUi) {
        _apiState.update { it.copy(presetDraft = config) }
    }

    fun onApiKeyChange(key: String) {
        _currentApiKey.value = key
        if (key.isNotBlank() && _apiState.value.assetDraft?.credentialsId?.value?.isEmpty() == true) {
            _apiState.update {
                it.copy(
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                )
            }
        }
    }

    fun onSearchEnabledChange(enabled: Boolean) {
        _searchSkillState.update { it.copy(enabled = enabled) }
    }

    fun onTavilyApiKeyChange(key: String) {
        _currentTavilyApiKey.value = key
        if (key.isBlank() && !uiState.value.searchSkillEditor.tavilyKeyPresent) {
            _searchSkillState.update { it.copy(enabled = false) }
        }
    }

    fun onClearTavilyApiKey() {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to clear Tavily API key", "Failed to clear search key")) {
            preferencesUseCases.clearTavilyApiKey()
            _currentTavilyApiKey.value = ""
        }
    }

    fun onSaveSearchSkillSettings(onSuccess: () -> Unit) {
        val enabled = _searchSkillState.value.enabled ?: uiState.value.searchSkillEditor.enabled
        val pendingApiKey = _currentTavilyApiKey.value.trim()
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save search skill settings", "Failed to save search settings")) {
            if (enabled && pendingApiKey.isBlank() && !uiState.value.searchSkillEditor.tavilyKeyPresent) {
                throw IllegalStateException("A Tavily API key is required to enable web search")
            }
            preferencesUseCases.updateSearchEnabled(enabled)
            if (pendingApiKey.isNotBlank()) {
                preferencesUseCases.saveTavilyApiKey(pendingApiKey)
            }
            _currentTavilyApiKey.value = ""
            _searchSkillState.value = SearchSkillTransientState()
            onSuccess()
        }
    }

    fun onSelectReusableApiCredential(id: ApiCredentialsId?) {
        val reusableCredential = uiState.value.apiProvidersSheet.assets
            .find { it.credentialsId == id }
            ?.let {
                ReusableApiCredentialUi(
                    credentialsId = it.credentialsId,
                    displayName = it.displayName,
                    modelId = it.modelId,
                    credentialAlias = it.credentialAlias,
                )
            }
        _currentApiKey.value = ""
        _apiState.update {
            it.copy(
                selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                selectedReusableApiCredentialName = reusableCredential?.displayName,
            )
        }
    }

    fun onSaveApiCredentials(onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit) {
        val assetDraft = _apiState.value.assetDraft ?: return
        val sourceCredentialAlias = if (_currentApiKey.value.isBlank()) {
            _apiState.value.selectedReusableApiCredentialAlias
        } else {
            null
        }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API credentials", "Failed to save credentials")) {
            val result = apiProviderUseCases.saveApiProviderDraft(
                ApiProviderDraft(
                    id = assetDraft.credentialsId,
                    displayName = assetDraft.displayName,
                    provider = assetDraft.provider,
                    modelId = assetDraft.modelId,
                    baseUrl = assetDraft.baseUrl,
                    isMultimodal = assetDraft.isMultimodal,
                    credentialAlias = assetDraft.credentialAlias,
                    apiKey = _currentApiKey.value,
                    sourceCredentialAlias = sourceCredentialAlias,
                    defaultReasoningEffort = apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                        provider = assetDraft.provider,
                        modelId = assetDraft.modelId,
                    ),
                )
            ).getOrThrow()

            val updatedAsset = apiModelAssetUiMapper.map(result.persistedAsset)
            val createdPreset = result.createdPreset?.let(apiModelAssetUiMapper::mapConfig)

            _currentApiKey.value = ""
            _apiState.update { state ->
                state.copy(
                    selectedAsset = updatedAsset,
                    assetDraft = updatedAsset,
                    presetDraft = createdPreset,
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                    discoveredApiModels = state.discoveredApiModels.mergeSelectedModel(updatedAsset),
                    discoveredApiModelScope = updatedAsset.toDiscoveryScope(),
                )
            }
            result.linkedExistingAssetDisplayName?.let { displayName ->
                _snackbarMessages.tryEmit("Automatically linked to \"$displayName\" API Model")
            }
            onSuccess(updatedAsset, createdPreset)
        }
    }

    fun onSaveApiModelConfig(onSuccess: () -> Unit) {
        val assetDraft = _apiState.value.assetDraft ?: _apiState.value.selectedAsset ?: return
        val presetDraft = uiState.value.apiProviderEditor.presetDraft ?: return
        val parentCredentialsId = if (presetDraft.credentialsId.value.isNotEmpty()) presetDraft.credentialsId else assetDraft.credentialsId

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save API configuration", "Failed to save configuration")) {
            apiProviderUseCases.saveApiPreset(
                provider = assetDraft.provider,
                parentCredentialsId = parentCredentialsId,
                defaultReasoningEffort = apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                    provider = assetDraft.provider,
                    modelId = assetDraft.modelId,
                ),
                draft = presetDraft.toDomain(),
            ).getOrThrow()
            onSuccess()
        }
    }

    fun onUpdateModelSearchQuery(query: String) {
        _apiState.update { it.copy(modelSearchQuery = query) }
    }

    fun onUpdateModelProviderFilter(provider: String?) {
        _apiState.update { it.copy(modelProviderFilter = provider) }
    }

    fun onUpdateModelSortOption(option: ModelSortOption) {
        _apiState.update { it.copy(modelSortOption = option) }
    }

    fun onFetchApiModels() {
        val assetDraft = _apiState.value.assetDraft ?: _apiState.value.selectedAsset ?: return
        val credentialAlias = resolveCredentialAlias(assetDraft)
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        _apiState.update { it.copy(isDiscoveringApiModels = true) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch provider models", "Failed to fetch models")) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = assetDraft.provider,
                        currentApiKey = _currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = assetDraft.baseUrl,
                        selectedModelId = assetDraft.modelId.takeIf(String::isNotBlank),
                    )
                )
                _apiState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models,
                        discoveredApiModelScope = result.scope,
                        assetDraft = state.normalizeVisionCapability(state.assetDraft, result.models),
                    )
                }
            } finally {
                _apiState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    fun onAddCustomHeader() {
        _apiState.update { state ->
            val draft = state.presetDraft ?: return@update state
            state.copy(presetDraft = draft.copy(customHeaders = draft.customHeaders + CustomHeaderUi()))
        }
    }

    fun onDeleteCustomHeader(index: Int) {
        _apiState.update { state ->
            val draft = state.presetDraft ?: return@update state
            state.copy(
                presetDraft = draft.copy(
                    customHeaders = draft.customHeaders.toMutableList().apply { removeAt(index) }
                )
            )
        }
    }

    fun onCustomHeaderChange(index: Int, header: CustomHeaderUi) {
        _apiState.update { state ->
            val draft = state.presetDraft ?: return@update state
            state.copy(
                presetDraft = draft.copy(
                    customHeaders = draft.customHeaders.toMutableList().apply { this[index] = header }
                )
            )
        }
    }

    fun onDeleteApiModelConfig(id: ApiModelConfigurationId, onSuccess: () -> Unit) {
        prepareDeletion(
            target = ModelDeletionTarget.ApiPreset(id),
            failureMessage = "Failed to delete API configuration",
            userMessage = "Failed to delete configuration",
            onImmediateSuccess = onSuccess,
        )
    }

    fun onDeleteApiModelAsset(id: ApiCredentialsId) {
        prepareDeletion(
            target = ModelDeletionTarget.ApiProvider(id),
            failureMessage = "Failed to delete API provider",
            userMessage = "Failed to delete provider",
        )
    }

    fun onBackToByokList() {
        _currentApiKey.value = ""
        _currentTavilyApiKey.value = ""
        _searchSkillState.value = SearchSkillTransientState()
        _apiState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                presetDraft = null,
            )
        }
    }

    fun onCleanupCustomHeaders() {
        _apiState.update { state ->
            val draft = state.presetDraft ?: return@update state
            state.copy(
                presetDraft = draft.copy(
                    customHeaders = draft.customHeaders.filter {
                        it.key.isNotBlank() && it.value.isNotBlank()
                    }
                )
            )
        }
    }

    fun onSetDefaultModel(modelType: ModelType, localConfigId: LocalModelConfigurationId?, apiConfigId: ApiModelConfigurationId?) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to set default model", "Failed to update default model")) {
            assignmentUseCases.setDefaultModel(modelType, localConfigId, apiConfigId)
            onShowAssignmentDialog(false, null)
        }
    }

    fun onShowAssignmentDialog(show: Boolean, modelType: ModelType?) {
        _assignmentState.update { it.copy(isOpen = show, editingSlot = modelType) }
    }

    private fun prepareDeletion(
        target: ModelDeletionTarget,
        failureMessage: String,
        userMessage: String,
        onImmediateSuccess: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            when (val preparedDeletion = deletionUseCases.prepareModelDeletion(target)) {
                PreparedModelDeletion.BlockedLastModel -> {
                    _deletionState.update { it.copy(showLastModelAlert = true) }
                }

                is PreparedModelDeletion.Ready -> {
                    if (preparedDeletion.modelTypesNeedingReassignment.isEmpty()) {
                        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, failureMessage, userMessage)) {
                            deletionUseCases.executeModelDeletionWithReassignment(
                                target = preparedDeletion.target,
                                modelTypesNeedingReassignment = emptyList(),
                                replacementLocalConfigId = null,
                                replacementApiConfigId = null,
                            ).getOrThrow()
                            onImmediateSuccess?.invoke()
                        }
                    } else {
                        _deletionState.value = DeletionTransientState(
                            pendingTarget = preparedDeletion.target.toUi(),
                            modelTypesNeedingReassignment = preparedDeletion.modelTypesNeedingReassignment,
                            reassignmentOptions = reassignmentOptionUiMapper.map(preparedDeletion.reassignmentCandidates),
                        )
                    }
                }
            }
        }
    }

    private fun maybeFetchSelectedXaiModelMetadata(asset: ApiModelAssetUi?) {
        if (asset?.provider != ApiProvider.XAI || asset.modelId.isBlank()) return
        if (_apiState.value.discoveredModelFor(asset)?.contextWindowTokens != null) return

        val credentialAlias = resolveCredentialAlias(asset)
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch xAI model details", "Failed to fetch model details")) {
            val result = apiProviderUseCases.discoverApiModels(
                ApiModelDiscoveryRequest(
                    provider = asset.provider,
                    currentApiKey = _currentApiKey.value,
                    credentialAlias = credentialAlias,
                    baseUrl = asset.baseUrl,
                    selectedModelId = asset.modelId,
                )
            )
            _apiState.update { state ->
                state.copy(
                    discoveredApiModels = result.models,
                    discoveredApiModelScope = result.scope,
                    assetDraft = state.normalizeVisionCapability(state.assetDraft, result.models),
                )
            }
        }
    }

    private fun resolveCredentialAlias(asset: ApiModelAssetUi): String? = _apiState.value.selectedReusableApiCredentialAlias
        ?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun ApiProvidersTransientState.discoveredModelFor(asset: ApiModelAssetUi?): DiscoveredApiModel? =
        discoveredApiModels.find { it.id == asset?.modelId }

    private fun ApiProvidersTransientState.normalizeVisionCapability(
        asset: ApiModelAssetUi?,
        models: List<DiscoveredApiModel> = discoveredApiModels,
    ): ApiModelAssetUi? {
        if (asset == null) return null
        val discoveredVisionCapability = models
            .find { it.id == asset.modelId }
            ?.isMultimodal
        return discoveredVisionCapability?.let { asset.copy(isMultimodal = it) } ?: asset
    }

    private fun ApiModelAssetUi?.matchesDiscoveryScope(other: ApiModelAssetUi?): Boolean {
        if (this == null || other == null) return false
        if (provider != other.provider) return false
        if (baseUrl.normalizedBaseUrl() != other.baseUrl.normalizedBaseUrl()) return false
        val currentAlias = credentialAlias.takeIf(String::isNotBlank)
        val nextAlias = other.credentialAlias.takeIf(String::isNotBlank)
        return currentAlias == null || nextAlias == null || currentAlias == nextAlias
    }

    private fun com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope?.matches(
        asset: ApiModelAssetUi?,
    ): Boolean {
        if (this == null || asset == null) return false
        if (provider != asset.provider) return false
        if (baseUrl != asset.baseUrl.normalizedBaseUrl()) return false
        val assetAlias = asset.credentialAlias.takeIf(String::isNotBlank)
        return credentialAlias == null || assetAlias == null || credentialAlias == assetAlias
    }

    private fun ApiModelAssetUi.toDiscoveryScope(): com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope =
        com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope(
            provider = provider,
            baseUrl = baseUrl.normalizedBaseUrl(),
            credentialAlias = credentialAlias.takeIf(String::isNotBlank),
        )

    private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()

    private fun List<DiscoveredApiModel>.mergeSelectedModel(asset: ApiModelAssetUi?): List<DiscoveredApiModel> = when {
        asset == null || asset.modelId.isBlank() -> this
        any { it.id == asset.modelId } -> this
        else -> listOf(DiscoveredApiModel(id = asset.modelId)) + this
    }

    private fun PendingDeletionTarget.toDomain(): ModelDeletionTarget = when (this) {
        is PendingDeletionTarget.LocalModelAsset -> ModelDeletionTarget.LocalModelAsset(id)
        is PendingDeletionTarget.LocalModelPreset -> ModelDeletionTarget.LocalModelPreset(id)
        is PendingDeletionTarget.ApiProvider -> ModelDeletionTarget.ApiProvider(id)
        is PendingDeletionTarget.ApiPreset -> ModelDeletionTarget.ApiPreset(id)
    }

    private fun LocalModelConfigUi.toDomain(): com.browntowndev.pocketcrew.domain.usecase.settings.LocalModelPresetDraft =
        com.browntowndev.pocketcrew.domain.usecase.settings.LocalModelPresetDraft(
            id = id,
            displayName = displayName,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
            temperature = temperature,
            topP = topP,
            topK = topK,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            thinkingEnabled = thinkingEnabled,
            systemPrompt = systemPrompt,
            isSystemPreset = isSystemPreset,
        )

    private fun ApiModelConfigUi.toDomain(): ApiPresetDraft = ApiPresetDraft(
        id = id,
        credentialsId = credentialsId,
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        systemPrompt = systemPrompt,
        reasoningEffort = reasoningEffort,
        customHeaders = customHeaders.map { it.key to it.value },
        openRouterRouting = openRouterRouting,
    )
}
