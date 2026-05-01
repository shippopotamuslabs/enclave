package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.usecase.settings.PreparedModelDeletion
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.feature.settings.ApiModelConfigUi
import com.browntowndev.pocketcrew.feature.settings.LocalModelAssetUiMapper
import com.browntowndev.pocketcrew.feature.settings.ApiModelAssetUiMapper
import com.browntowndev.pocketcrew.feature.settings.TtsProviderAssetUiMapper
import com.browntowndev.pocketcrew.feature.settings.ReassignmentOptionUiMapper
import com.browntowndev.pocketcrew.feature.settings.SettingsUiStateFactory
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class PersistedSettingsBundle(
    val settings: com.browntowndev.pocketcrew.domain.port.repository.SettingsData,
    val localAssets: List<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>,
    val apiAssets: List<com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset>,
    val ttsAssets: List<com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset>,
    val mediaAssets: List<com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset>,
    val defaultModels: List<com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment>,
)

private data class TransientSettingsBundle(
    val sheetVisibility: SheetVisibilityState,
    val memories: List<StoredMemory>,
    val feedbackText: String,
    val localModelsState: LocalModelsTransientState,
    val apiState: ApiProvidersTransientState,
    val ttsState: TtsProvidersTransientState,
    val mediaState: MediaProvidersTransientState,
    val searchSkillState: SearchSkillTransientState,
    val assignmentState: AssignmentDialogTransientState,
    val deletionState: DeletionTransientState,
    val memoriesState: MemoriesTransientState,
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
    val ttsState: TtsProvidersTransientState,
    val assignmentState: AssignmentDialogTransientState,
    val deletionState: DeletionTransientState,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
    private val memoriesRepository: MemoriesRepository,
    private val settingsUiStateFactory: SettingsUiStateFactory,
    private val localModelAssetUiMapper: LocalModelAssetUiMapper,
    private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
    private val ttsProviderAssetUiMapper: TtsProviderAssetUiMapper,
    private val reassignmentOptionUiMapper: ReassignmentOptionUiMapper,
    private val errorHandler: ViewModelErrorHandler,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _sheetVisibility = MutableStateFlow(SheetVisibilityState())
    private val _memoriesState = MutableStateFlow(MemoriesTransientState())
    private val _feedbackText = MutableStateFlow("")
    private val _localModelsState = MutableStateFlow(LocalModelsTransientState())
    private val _apiState = MutableStateFlow(ApiProvidersTransientState())
    private val _searchSkillState = MutableStateFlow(SearchSkillTransientState())
    private val _ttsState = MutableStateFlow(TtsProvidersTransientState())
    private val _mediaState = MutableStateFlow(MediaProvidersTransientState())
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
    private val ttsUseCases = settingsUseCases.tts
    private val mediaUseCases = settingsUseCases.media
    private val assignmentUseCases = settingsUseCases.assignments
    private val deletionUseCases = settingsUseCases.deletion

    private val localModelAssetsFlow = localModelUseCases.getLocalModelAssets()
    private val apiModelAssetsFlow = apiProviderUseCases.getApiModelAssets()
    private val ttsAssetsFlow = ttsUseCases.getTtsProviders()
    private val mediaAssetsFlow = mediaUseCases.getMediaProviders()
    private val memoriesFlow = memoriesRepository.getAllMemoriesFlow().map { memories ->
        memories.map { StoredMemory(it.id, it.content, it.category) }
    }
    // Combine(6) isn't available as a typed overload — nest to stay within the 5-flow limit.
    private val ttsAndMediaBundle = combine(
        ttsAssetsFlow,
        mediaAssetsFlow,
    ) { tts, media -> Pair(tts, media) }
    private val persistedSettingsBundle = combine(
        settingsUseCases.getSettings(),
        localModelAssetsFlow,
        apiModelAssetsFlow,
        ttsAndMediaBundle,
        assignmentUseCases.getDefaultModels(),
    ) { settings, localAssets, apiAssets, ttsAndMedia, defaultModels ->
        PersistedSettingsBundle(
            settings = settings,
            localAssets = localAssets,
            apiAssets = apiAssets,
            ttsAssets = ttsAndMedia.first,
            mediaAssets = ttsAndMedia.second,
            defaultModels = defaultModels,
        )
    }
    private val sheetAndMemoriesBundle = combine(
        _sheetVisibility,
        memoriesFlow,
        _feedbackText,
        _memoriesState
    ) { sheetVisibility, memories, feedbackText, memoriesState ->
        object {
            val sheetVisibility = sheetVisibility
            val memories = memories
            val feedbackText = feedbackText
            val memoriesState = memoriesState
        }
    }

    private val modelAndToolsBundle = combine(
        _localModelsState,
        _apiState,
        _searchSkillState,
        _ttsState,
        _mediaState,
    ) { localModelsState, apiState, searchSkillState, ttsState, mediaState ->
        object {
            val localModelsState = localModelsState
            val apiState = apiState
            val searchSkillState = searchSkillState
            val ttsState = ttsState
            val mediaState = mediaState
        }
    }

    private val transientSettingsBundle = combine(
        sheetAndMemoriesBundle,
        modelAndToolsBundle,
        _assignmentState,
        _deletionState
    ) { smBundle, mtBundle, assignmentState, deletionState ->
        TransientSettingsBundle(
            sheetVisibility = smBundle.sheetVisibility,
            memories = smBundle.memories,
            feedbackText = smBundle.feedbackText,
            localModelsState = mtBundle.localModelsState,
            apiState = mtBundle.apiState,
            ttsState = mtBundle.ttsState,
            mediaState = mtBundle.mediaState,
            searchSkillState = mtBundle.searchSkillState,
            assignmentState = assignmentState,
            deletionState = deletionState,
            memoriesState = smBundle.memoriesState,
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
            ttsAssets = persisted.ttsAssets,
            mediaAssets = persisted.mediaAssets,
            defaultModels = persisted.defaultModels,
            sheetVisibility = transient.sheetVisibility,
            memories = transient.memories,
            feedbackText = transient.feedbackText,
            localModelsState = transient.localModelsState,
            apiState = transient.apiState,
            ttsState = transient.ttsState,
            mediaState = transient.mediaState,
            searchSkillState = transient.searchSkillState,
            assignmentsState = transient.assignmentState,
            deletionState = transient.deletionState,
            memoriesState = transient.memoriesState,
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
        viewModelScope.launch {
            preferencesUseCases.updateHapticResponse(enabled)
        }
    }

    fun onBackgroundInferenceChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update background inference setting", "Failed to update setting")) {
            preferencesUseCases.updateBackgroundInferenceEnabled(enabled)
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

    fun onShowMemoriesSheet(show: Boolean) {
        _sheetVisibility.update { it.copy(memories = show) }
        if (!show) {
            _memoriesState.update { MemoriesTransientState() }
        }
    }

    fun onAddMemory() {
        _memoriesState.update {
            it.copy(
                memoryDraft = StoredMemory(category = MemoryCategory.PREFERENCES),
                isEditing = true
            )
        }
    }

    fun onEditMemory(memory: StoredMemory) {
        _memoriesState.update {
            it.copy(
                memoryDraft = memory,
                isEditing = true
            )
        }
    }

    fun onUpdateMemoryDraft(text: String, category: MemoryCategory) {
        _memoriesState.update {
            it.copy(
                memoryDraft = it.memoryDraft?.copy(text = text, category = category)
                    ?: StoredMemory(text = text, category = category)
            )
        }
    }

    fun onSaveMemory() {
        val draft = _memoriesState.value.memoryDraft ?: return
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save memory", "Failed to save memory")) {
            if (draft.id.isEmpty()) {
                memoriesRepository.insertMemory(draft.category, draft.text)
            } else {
                memoriesRepository.updateMemory(draft.id, draft.text, draft.category)
            }
            _memoriesState.update { MemoriesTransientState() }
        }
    }

    fun onCancelMemoryEdit() {
        _memoriesState.update { MemoriesTransientState() }
    }

    fun onDeleteMemory(memoryId: String) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete memory", "Failed to delete memory")) {
            memoriesRepository.deleteMemory(memoryId)
        }
    }

    fun onAllowMemoriesChange(enabled: Boolean) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update memories setting", "Failed to update setting")) {
            preferencesUseCases.updateAllowMemories(enabled)
        }
    }

    fun onDeleteAllConversations() = Unit

    fun onDeleteAllMemories() {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete all memories", "Failed to delete all memories")) {
            // Add a use case for this if needed, or loop delete
            // For now, let's just use repository directly
            uiState.value.memories.memories.forEach { 
                memoriesRepository.deleteMemory(it.id)
            }
        }
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
            selection.ttsAsset?.let {
                _ttsState.update { state ->
                    state.copy(
                        selectedAsset = ttsProviderAssetUiMapper.map(it, isDefault = true),
                        isSheetOpen = true
                    )
                }
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

    fun onSelectTtsProviderAsset(asset: TtsProviderAssetUi?) {
        _ttsState.update { it.copy(selectedAsset = asset, assetDraft = asset) }
        resetModelDiscovery()
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
                modelProviderFilters = emptySet(),
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
        _searchSkillState.value = SearchSkillTransientState()
    }

    fun onShowTtsProvidersSheet(show: Boolean) {
        if (!show) {
            _ttsState.update { it.copy(isSheetOpen = false) }
            return
        }

        _ttsState.update {
            it.copy(
                isSheetOpen = true,
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
            )
        }
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
                modelProviderFilters = emptySet(),
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
    }

    fun onStartCreateTtsProviderAsset() {
        _currentApiKey.value = ""
        resetModelDiscovery()
        _ttsState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = TtsProviderAssetUi(
                    displayName = "New TTS Provider",
                    provider = ApiProvider.OPENAI,
                ),
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
            )
        }
    }

    fun onTtsAssetFieldChange(asset: TtsProviderAssetUi) {
        val existingDraft = _ttsState.value.assetDraft
        val discoveryScopeChanged =
            existingDraft == null ||
                existingDraft.provider != asset.provider ||
                existingDraft.baseUrl.normalizedBaseUrl() != asset.baseUrl.normalizedBaseUrl()
        if (discoveryScopeChanged) {
            resetModelDiscovery()
        }

        if (asset.id.value.isNotEmpty() && asset.useAsDefault != existingDraft?.useAsDefault) {
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update default model", "Failed to update default model")) {
                if (asset.useAsDefault) {
                    assignmentUseCases.setDefaultModel(
                        modelType = ModelType.TTS,
                        localConfigId = null,
                        apiConfigId = null,
                        ttsProviderId = asset.id
                    )
                } else {
                    assignmentUseCases.clearDefaultModel(ModelType.TTS)
                }
            }
        }

        _ttsState.update { state ->
            state.copy(
                assetDraft = asset,
                selectedReusableApiCredentialAlias = if (existingDraft?.provider != asset.provider) {
                    null
                } else {
                    state.selectedReusableApiCredentialAlias
                },
                selectedReusableApiCredentialName = if (existingDraft?.provider != asset.provider) {
                    null
                } else {
                    state.selectedReusableApiCredentialName
                },
            )
        }
    }

    fun onSelectReusableTtsApiCredential(id: ApiCredentialsId?) {
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
        resetModelDiscovery()
        _ttsState.update {
            it.copy(
                selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                selectedReusableApiCredentialName = reusableCredential?.displayName,
            )
        }
    }

    fun onSaveTtsProvider(onSuccess: () -> Unit) {
        val draft = _ttsState.value.assetDraft ?: return
        val reusedAlias = _ttsState.value.selectedReusableApiCredentialAlias
        val apiKey = _currentApiKey.value.ifBlank { "" }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save TTS provider", "Failed to save provider")) {
            val ttsId = ttsUseCases.saveTtsProvider(
                com.browntowndev.pocketcrew.domain.usecase.settings.TtsProviderDraft(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    voiceName = draft.voiceName,
                    modelName = draft.modelName,
                    baseUrl = draft.baseUrl,
                    credentialAlias = reusedAlias ?: draft.credentialAlias.ifBlank {
                        "tts-${draft.provider.name.lowercase()}-${draft.voiceName.lowercase()}-${java.util.UUID.randomUUID().toString().take(8)}"
                    },
                    apiKey = apiKey,
                )
            ).getOrThrow()

            if (draft.useAsDefault) {
                assignmentUseCases.setDefaultModel(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId
                )
            }

            onSuccess()
        }
    }

    fun onShowMediaProvidersSheet(show: Boolean) {
        if (!show) {
            _mediaState.update { it.copy(isSheetOpen = false) }
            return
        }

        _mediaState.update {
            it.copy(
                isSheetOpen = true,
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                isDiscoveringApiModels = false,
                discoveredApiModels = emptyList(),
            )
        }
    }

    fun onStartCreateMediaProviderAsset() {
        _currentApiKey.value = ""
        _mediaState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = MediaProviderAssetUi(
                    displayName = "New Media Provider",
                    provider = ApiProvider.OPENAI,
                ),
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                isDiscoveringApiModels = false,
                discoveredApiModels = emptyList(),
            )
        }
    }

    fun onSelectMediaProviderAsset(asset: MediaProviderAssetUi?) {
        _currentApiKey.value = ""
        _mediaState.update {
            it.copy(
                selectedAsset = asset,
                assetDraft = asset,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                isDiscoveringApiModels = false,
                discoveredApiModels = emptyList(),
            )
        }
    }

    fun onMediaAssetFieldChange(asset: MediaProviderAssetUi) {
        val existingDraft = _mediaState.value.assetDraft
        val discoveryScopeChanged =
            existingDraft == null ||
                existingDraft.provider != asset.provider ||
                existingDraft.capability != asset.capability ||
                existingDraft.baseUrl.normalizedBaseUrl() != asset.baseUrl.normalizedBaseUrl()

        if (asset.id.value.isNotEmpty() && asset.useAsDefault != existingDraft?.useAsDefault) {
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to update default model", "Failed to update default model")) {
                val modelType = when (asset.capability) {
                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                }
                assignmentUseCases.setDefaultModel(
                    modelType = modelType,
                    localConfigId = null,
                    apiConfigId = null,
                    mediaProviderId = asset.id
                )
            }
        }

        _mediaState.update { state ->
            state.copy(
                assetDraft = asset,
                selectedReusableApiCredentialAlias = if (existingDraft?.provider != asset.provider) null else state.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (existingDraft?.provider != asset.provider) null else state.selectedReusableApiCredentialName,
                discoveredApiModels = if (discoveryScopeChanged) emptyList() else state.discoveredApiModels,
                isDiscoveringApiModels = if (discoveryScopeChanged) false else state.isDiscoveringApiModels,
            )
        }
    }

    fun onSelectReusableMediaApiCredential(id: ApiCredentialsId?) {
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
        _mediaState.update {
            it.copy(
                selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                selectedReusableApiCredentialName = reusableCredential?.displayName,
                discoveredApiModels = emptyList(),
                isDiscoveringApiModels = false,
            )
        }
    }

    fun onFetchMediaModels() {
        val draft = _mediaState.value.assetDraft ?: return
        val credentialAlias = resolveMediaCredentialAlias(draft)
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        _mediaState.update { it.copy(isDiscoveringApiModels = true) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch media models", "Failed to fetch models")) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = draft.provider,
                        currentApiKey = _currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = draft.baseUrl,
                        selectedModelId = draft.modelName.takeIf(String::isNotBlank),
                    )
                )
                _mediaState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models.filterMediaModels(draft.capability),
                    )
                }
            } finally {
                _mediaState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    fun onSaveMediaProvider(onSuccess: () -> Unit) {
        val draft = _mediaState.value.assetDraft ?: return
        val reusedAlias = _mediaState.value.selectedReusableApiCredentialAlias
        val apiKey = _currentApiKey.value.ifBlank { "" }

        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to save Media provider", "Failed to save provider")) {
            val mediaId = mediaUseCases.saveMediaProvider(
                com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    capability = draft.capability,
                    modelName = draft.modelName,
                    baseUrl = draft.baseUrl,
                    credentialAlias = reusedAlias ?: draft.credentialAlias.ifBlank {
                        "media-${draft.provider.name.lowercase()}-${draft.capability.name.lowercase()}-${java.util.UUID.randomUUID().toString().take(8)}"
                    }
                ),
                apiKey = apiKey
            )

            if (draft.useAsDefault) {
                val modelType = when (draft.capability) {
                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                }
                assignmentUseCases.setDefaultModel(
                    modelType = modelType,
                    localConfigId = null,
                    apiConfigId = null,
                    mediaProviderId = mediaId
                )
            }

            onSuccess()
        }
    }

    fun onDeleteMediaProviderAsset(id: MediaProviderId) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete Media provider", "Failed to delete provider")) {
            mediaUseCases.deleteMediaProvider(id)
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
                modelProviderFilters = emptySet(),
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
        maybeFetchSelectedXaiModelMetadata(asset)
        maybeFetchModelsOnReEntry(asset)
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

    fun onToggleModelProviderFilter(provider: String) {
        _apiState.update { state -> 
            val currentFilters = state.modelProviderFilters
            val newFilters = if (currentFilters.contains(provider)) {
                currentFilters - provider
            } else {
                currentFilters + provider
            }
            state.copy(modelProviderFilters = newFilters)
        }
    }

    fun onClearModelProviderFilters() {
        _apiState.update { it.copy(modelProviderFilters = emptySet()) }
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

    fun onFetchTtsModels() {
        val draft = _ttsState.value.assetDraft ?: _ttsState.value.selectedAsset ?: return
        val credentialAlias = resolveTtsCredentialAlias(draft)
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        _apiState.update { it.copy(isDiscoveringApiModels = true) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch TTS provider models", "Failed to fetch models")) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = draft.provider,
                        currentApiKey = _currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = draft.baseUrl,
                        selectedModelId = draft.modelName
                            ?.takeIf(String::isNotBlank)
                            ?.takeIf { it.isGoogleTtsModelCandidate() },
                    )
                )
                _apiState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models.filterGoogleTtsModels(),
                        discoveredApiModelScope = result.scope,
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

    fun onDeleteTtsProviderAsset(id: com.browntowndev.pocketcrew.domain.model.config.TtsProviderId) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to delete TTS provider", "Failed to delete provider")) {
            ttsUseCases.deleteTtsProvider(id)
        }
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

    fun onBackToMediaList() {
        _currentApiKey.value = ""
        _mediaState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                isDiscoveringApiModels = false,
                discoveredApiModels = emptyList(),
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

    fun onSetDefaultModel(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null,
    ) {
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to set default model", "Failed to update default model")) {
            assignmentUseCases.setDefaultModel(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
            )
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

    /**
     * Silently fills in missing [contextWindowTokens] for the selected xAI model when the full
     * model list is already loaded. This is xAI-specific because xAI returns per-model context
     * window data via the models endpoint. Only runs if [discoveredApiModels] is non-empty (the
     * re-entry / no-models case is handled by [maybeFetchModelsOnReEntry]).
     */
    private fun maybeFetchSelectedXaiModelMetadata(asset: ApiModelAssetUi?) {
        if (asset?.provider != ApiProvider.XAI || asset.modelId.isBlank()) return
        // Only fill the metadata gap — if models aren't loaded yet, maybeFetchModelsOnReEntry
        // handles that to avoid a concurrent double-fetch.
        if (_apiState.value.discoveredApiModels.isEmpty()) return
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

    /**
     * Auto-fetches the model list on re-entry to the configure screen for any provider that has
     * persisted credentials (stored alias or a live key already typed). Handles all providers
     * uniformly, including xAI — the no-models case is not xAI-specific.
     */
    private fun maybeFetchModelsOnReEntry(asset: ApiModelAssetUi?) {
        if (asset == null) return
        val state = _apiState.value
        // Skip if models are already present for this scope, or a fetch is already in progress.
        if (state.discoveredApiModels.isNotEmpty()) return
        if (state.isDiscoveringApiModels) return

        // Resolve a credential: prefer a live key being typed, then stored alias on the asset.
        val credentialAlias = resolveCredentialAlias(asset)
        if (_currentApiKey.value.isBlank() && credentialAlias == null) return

        _apiState.update { it.copy(isDiscoveringApiModels = true) }
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to fetch provider models on re-entry", "Failed to fetch models")) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = asset.provider,
                        currentApiKey = _currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = asset.baseUrl,
                        selectedModelId = asset.modelId.takeIf(String::isNotBlank),
                    )
                )
                _apiState.update { s ->
                    s.copy(
                        discoveredApiModels = result.models,
                        discoveredApiModelScope = result.scope,
                        assetDraft = s.normalizeVisionCapability(s.assetDraft, result.models),
                    )
                }
            } finally {
                _apiState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    private fun resolveCredentialAlias(asset: ApiModelAssetUi): String? = _apiState.value.selectedReusableApiCredentialAlias
        ?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun resetModelDiscovery() {
        _apiState.update {
            it.copy(
                discoveredApiModels = emptyList(),
                discoveredApiModelScope = null,
                isDiscoveringApiModels = false,
                modelSearchQuery = "",
                modelProviderFilters = emptySet(),
                modelSortOption = ModelSortOption.A_TO_Z,
            )
        }
    }

    private fun resolveTtsCredentialAlias(asset: TtsProviderAssetUi): String? = _ttsState.value.selectedReusableApiCredentialAlias
        ?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun resolveMediaCredentialAlias(asset: MediaProviderAssetUi): String? = _mediaState.value.selectedReusableApiCredentialAlias
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
        if (this.provider != other.provider) return false
        if (this.baseUrl.normalizedBaseUrl() != other.baseUrl.normalizedBaseUrl()) return false
        val currentAlias = this.credentialAlias.takeIf(String::isNotBlank)
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

    private fun List<DiscoveredApiModel>.filterGoogleTtsModels(): List<DiscoveredApiModel> =
        filter { it.id.isGoogleTtsModelCandidate() }

    private fun List<DiscoveredApiModel>.filterMediaModels(capability: MediaCapability): List<DiscoveredApiModel> {
        return filter { model ->
            when (capability) {
                MediaCapability.IMAGE -> {
                    (model.id.contains("gpt-image-", ignoreCase = true) ||
                            model.id.contains("gemini-3.1", ignoreCase = true) ||
                            model.id.contains("dall-e-3", ignoreCase = true)) &&
                            !model.id.contains("dall-e-2", ignoreCase = true)
                }
                MediaCapability.VIDEO -> {
                    model.id.contains("veo-3", ignoreCase = true)
                }
                MediaCapability.MUSIC -> {
                    model.id.contains("lyria-3", ignoreCase = true)
                }
            }
        }
    }

    private fun String.isGoogleTtsModelCandidate(): Boolean =
        endsWith("-tts") || endsWith("-tts-preview")

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
