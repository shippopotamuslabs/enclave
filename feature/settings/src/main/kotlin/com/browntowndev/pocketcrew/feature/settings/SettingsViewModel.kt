@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.EmbeddingProgressPort
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val generalDelegate: SettingsGeneralDelegate,
    private val memoriesDelegate: SettingsMemoriesDelegate,
    private val localModelsDelegate: SettingsLocalModelsDelegate,
    private val apiProvidersDelegate: SettingsApiProvidersDelegate,
    private val searchDelegate: SettingsSearchDelegate,
    private val assignmentsDelegate: SettingsAssignmentsDelegate,
    private val deletionHelper: SettingsDeletionHelper,
    settingsUseCases: SettingsUseCases,
    private val settingsUiStateFactory: SettingsUiStateFactory,
    private val errorHandler: ViewModelErrorHandler,
    private val embeddingProgressPort: EmbeddingProgressPort,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"

        private const val ARG_PERSISTED_SETTINGS = 0
        private const val ARG_LOCAL_ASSETS = 1
        private const val ARG_API_ASSETS = 2
        private const val ARG_TTS_ASSETS = 3
        private const val ARG_MEDIA_ASSETS = 4
        private const val ARG_DEFAULT_MODELS = 5
        private const val ARG_AUTH_STATE = 6
        private const val ARG_SHEET_VISIBILITY = 7
        private const val ARG_MEMORIES = 8
        private const val ARG_FEEDBACK_TEXT = 9
        private const val ARG_LOCAL_MODELS_STATE = 10
        private const val ARG_API_STATE = 11
        private const val ARG_TTS_STATE = 12
        private const val ARG_MEDIA_STATE = 13
        private const val ARG_SEARCH_SKILL_STATE = 14
        private const val ARG_ASSIGNMENTS_STATE = 15
        private const val ARG_DELETION_STATE = 16
        private const val ARG_MEMORIES_STATE = 17
        private const val ARG_CURRENT_TAVILY_API_KEY = 18
        private const val ARG_CURRENT_EXA_API_KEY = 19
        private const val ARG_CURRENT_X_API_KEY = 20
        private const val ARG_X_SEARCH_STATE = 21
        private const val ARG_IS_REINDEXING = 22
        private const val ARG_REINDEX_PROGRESS = 23
        private const val ARG_VAULT_REAUTH_SHEET = 24
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
    private val _xSearchState = MutableStateFlow(XSearchTransientState())
    private val _vaultReauthSheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val _currentApiKey = MutableStateFlow("")
    private val _currentTavilyApiKey = MutableStateFlow("")
    private val _currentExaApiKey = MutableStateFlow("")
    private val _currentXApiKey = MutableStateFlow("")

    val currentApiKey: StateFlow<String> = _currentApiKey
    val currentTavilyApiKey: StateFlow<String> = _currentTavilyApiKey
    val currentXApiKey: StateFlow<String> = _currentXApiKey
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val preferencesUseCases = settingsUseCases.preferences
    private val localModelUseCases = settingsUseCases.localModels
    private val apiProviderUseCases = settingsUseCases.apiProviders
    private val ttsUseCases = settingsUseCases.tts
    private val mediaUseCases = settingsUseCases.media
    private val assignmentUseCases = settingsUseCases.assignments
    private val memoriesUseCases = settingsUseCases.memories
    private val authUseCases = settingsUseCases.auth

    private val localModelAssetsFlow = localModelUseCases.getLocalModelAssets()
    private val apiModelAssetsFlow = apiProviderUseCases.getApiModelAssets()
    private val ttsAssetsFlow = ttsUseCases.getTtsProviders()
    private val mediaAssetsFlow = mediaUseCases.getMediaProviders()
    private val memoriesFlow = memoriesUseCases.getAllMemoriesFlow().map { memories ->
        memories.map { StoredMemory(it.id, it.content, it.category) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesUseCases.getSettings(),
        localModelAssetsFlow,
        apiModelAssetsFlow,
        ttsAssetsFlow,
        mediaAssetsFlow,
        assignmentUseCases.getDefaultModels(),
        authUseCases.getAuthState(),
        _sheetVisibility,
        memoriesFlow,
        _feedbackText,
        _localModelsState,
        _apiState,
        _ttsState,
        _mediaState,
        _searchSkillState,
        _assignmentState,
        _deletionState,
        _memoriesState,
        _currentTavilyApiKey,
        _currentExaApiKey,
        _currentXApiKey,
        _xSearchState,
        embeddingProgressPort.isReindexing,
        embeddingProgressPort.progress,
        _vaultReauthSheet,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        settingsUiStateFactory.create(
            persistedSettings = args[ARG_PERSISTED_SETTINGS] as com.browntowndev.pocketcrew.domain.port.repository.SettingsData,
            localAssets = args[ARG_LOCAL_ASSETS] as List<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>,
            apiAssets = args[ARG_API_ASSETS] as List<com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset>,
            ttsAssets = args[ARG_TTS_ASSETS] as List<com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset>,
            mediaAssets = args[ARG_MEDIA_ASSETS] as List<com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset>,
            defaultModels = args[ARG_DEFAULT_MODELS] as List<com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment>,
            authState = args[ARG_AUTH_STATE] as com.browntowndev.pocketcrew.domain.port.auth.AuthState,
            sheetVisibility = args[ARG_SHEET_VISIBILITY] as SheetVisibilityState,
            memories = args[ARG_MEMORIES] as List<StoredMemory>,
            feedbackText = args[ARG_FEEDBACK_TEXT] as String,
            localModelsState = args[ARG_LOCAL_MODELS_STATE] as LocalModelsTransientState,
            apiState = args[ARG_API_STATE] as ApiProvidersTransientState,
            ttsState = args[ARG_TTS_STATE] as TtsProvidersTransientState,
            mediaState = args[ARG_MEDIA_STATE] as MediaProvidersTransientState,
            searchSkillState = args[ARG_SEARCH_SKILL_STATE] as SearchSkillTransientState,
            assignmentsState = args[ARG_ASSIGNMENTS_STATE] as AssignmentDialogTransientState,
            deletionState = args[ARG_DELETION_STATE] as DeletionTransientState,
            memoriesState = args[ARG_MEMORIES_STATE] as MemoriesTransientState,
            currentTavilyApiKey = args[ARG_CURRENT_TAVILY_API_KEY] as String,
            currentExaApiKey = args[ARG_CURRENT_EXA_API_KEY] as String,
            currentXApiKey = args[ARG_CURRENT_X_API_KEY] as String,
            xSearchState = args[ARG_X_SEARCH_STATE] as XSearchTransientState,
            isReindexing = args[ARG_IS_REINDEXING] as Boolean,
            reindexProgress = args[ARG_REINDEX_PROGRESS] as Float,
            vaultReauthSheet = args[ARG_VAULT_REAUTH_SHEET] as VaultReauthSheetState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    init {
        localModelsDelegate.observeActiveLocalModels(viewModelScope, localModelAssetsFlow, _localModelsState)
    }

    // --- General / Auth ---
    fun onThemeChange(theme: AppTheme) = generalDelegate.onThemeChange(viewModelScope, TAG, theme)
    fun onHapticPressChange(enabled: Boolean) = generalDelegate.onHapticPressChange(viewModelScope, TAG, enabled)
    fun onHapticResponseChange(enabled: Boolean) = generalDelegate.onHapticResponseChange(viewModelScope, enabled)
    fun onBackgroundInferenceChange(enabled: Boolean) = generalDelegate.onBackgroundInferenceChange(viewModelScope, TAG, enabled)
    fun onLoginWithGoogle() = generalDelegate.onLoginWithGoogle(viewModelScope, TAG, _vaultReauthSheet)
    fun onLogout() = generalDelegate.onLogout(viewModelScope, TAG)

    /**
     * Settings re-auth sheet handlers — the post-Settings-sign-in flow that mirrors the
     * cold-start vault unlock + reconciliation in `MainViewModel`.
     */
    fun onSubmitVaultReauthPassword(password: String) =
        generalDelegate.onSubmitVaultReauthPassword(viewModelScope, TAG, _vaultReauthSheet, password)

    fun onConfirmAdoptCloudFromSettings() =
        generalDelegate.onConfirmAdoptCloud(viewModelScope, TAG, _vaultReauthSheet)

    fun onCancelVaultReauth() = generalDelegate.onCancelVaultReauth(_vaultReauthSheet)
    fun onShowCustomizationSheet(show: Boolean) = generalDelegate.onShowCustomizationSheet(_sheetVisibility, show)
    fun onCustomizationEnabledChange(enabled: Boolean) = generalDelegate.onCustomizationEnabledChange(viewModelScope, TAG, enabled)
    fun onPromptOptionChange(option: SystemPromptOption) = generalDelegate.onPromptOptionChange(viewModelScope, TAG, option)
    fun onCustomPromptTextChange(text: String) = generalDelegate.onCustomPromptTextChange(viewModelScope, TAG, text)
    fun onSaveCustomization() = generalDelegate.onSaveCustomization(_sheetVisibility)
    fun onShowFeedbackSheet(show: Boolean) = generalDelegate.onShowFeedbackSheet(_sheetVisibility, show)
    fun onShowVisionSettingsSheet(show: Boolean) = generalDelegate.onShowVisionSettingsSheet(_sheetVisibility, show)
    fun onFeedbackTextChange(text: String) = generalDelegate.onFeedbackTextChange(_feedbackText, text)
    fun onSubmitFeedback() = generalDelegate.onSubmitFeedback(_feedbackText, _sheetVisibility)

    // --- Memories ---
    fun onShowDataControlsSheet(show: Boolean) = memoriesDelegate.onShowDataControlsSheet(_sheetVisibility, show)
    fun onShowMemoriesSheet(show: Boolean) = memoriesDelegate.onShowMemoriesSheet(_sheetVisibility, _memoriesState, show)
    fun onAddMemory() = memoriesDelegate.onAddMemory(_memoriesState)
    fun onEditMemory(memory: StoredMemory) = memoriesDelegate.onEditMemory(_memoriesState, memory)
    fun onUpdateMemoryDraft(text: String, category: MemoryCategory) = memoriesDelegate.onUpdateMemoryDraft(_memoriesState, text, category)
    fun onSaveMemory() = memoriesDelegate.onSaveMemory(viewModelScope, TAG, _memoriesState)
    fun onCancelMemoryEdit() = memoriesDelegate.onCancelMemoryEdit(_memoriesState)
    fun onDeleteMemory(memoryId: String) = memoriesDelegate.onDeleteMemory(viewModelScope, TAG, memoryId)
    fun onAllowMemoriesChange(enabled: Boolean) = memoriesDelegate.onAllowMemoriesChange(viewModelScope, TAG, enabled)
    fun onDeleteAllMemories(memories: List<StoredMemory>) = memoriesDelegate.onDeleteAllMemories(viewModelScope, TAG, memories)

    // --- Local Models ---
    fun onShowModelConfigSheet(show: Boolean) = localModelsDelegate.onShowModelConfigSheet(_localModelsState, show)
    fun onSelectLocalModelAsset(asset: LocalModelAssetUi?) = localModelsDelegate.onSelectLocalModelAsset(_localModelsState, asset)
    fun onSelectLocalModelConfig(config: LocalModelConfigUi?) = localModelsDelegate.onSelectLocalModelConfig(_localModelsState, config)
    fun onLocalModelConfigFieldChange(config: LocalModelConfigUi) = localModelsDelegate.onModelConfigFieldChange(_localModelsState, config)
    fun onSaveLocalModelConfig(onSuccess: () -> Unit) =
        localModelsDelegate.onSaveLocalModelConfig(viewModelScope, TAG, _localModelsState, onSuccess)

    fun onDeleteLocalModelConfig(id: LocalModelConfigurationId, onSuccess: () -> Unit) =
        localModelsDelegate.onDeleteLocalModelConfig(viewModelScope, _deletionState, TAG, id, onSuccess)

    fun onDeleteLocalModelAsset(id: com.browntowndev.pocketcrew.domain.model.config.LocalModelId) =
        localModelsDelegate.onDeleteLocalModelAsset(viewModelScope, _deletionState, TAG, id)

    fun onReDownloadLocalModel(id: com.browntowndev.pocketcrew.domain.model.config.LocalModelId) =
        localModelsDelegate.onReDownloadLocalModel(viewModelScope, TAG, id)

    fun onClearSelectedLocalModel() {
        _localModelsState.value = _localModelsState.value.copy(selectedAsset = null, configDraft = null)
    }

    // --- API Providers (BYOK) ---
    fun onShowByokSheet(show: Boolean) = apiProvidersDelegate.onShowByokSheet(_apiState, show)
    fun onSelectApiModelAsset(asset: ApiModelAssetUi?) =
        apiProvidersDelegate.onSelectApiProviderAsset(viewModelScope, _apiState, _currentApiKey, asset)

    fun onApiProviderAssetDraftChange(asset: ApiModelAssetUi) =
        apiProvidersDelegate.onApiProviderAssetDraftChange(viewModelScope, _apiState, _currentApiKey, asset)

    fun onApiModelAssetFieldChange(asset: ApiModelAssetUi) =
        apiProvidersDelegate.onApiProviderAssetDraftChange(viewModelScope, _apiState, _currentApiKey, asset)

    fun onApiModelConfigFieldChange(config: ApiModelConfigUi) = apiProvidersDelegate.onApiModelConfigFieldChange(_apiState, config)
    fun onSelectApiModelConfig(config: ApiModelConfigUi?) = apiProvidersDelegate.onSelectApiModelConfig(_apiState, config)
    fun onFetchApiModels() = apiProvidersDelegate.onFetchApiModels(viewModelScope, TAG, _apiState, _currentApiKey)
    fun onSaveApiCredentials(onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit) =
        apiProvidersDelegate.onSaveApiCredentials(viewModelScope, TAG, _apiState, _currentApiKey, onSuccess) {
            _snackbarMessages.tryEmit(it)
        }

    fun onStartCreateApiModelAsset() = apiProvidersDelegate.onStartCreateApiModelAsset(_apiState)
    fun onShowTtsProvidersSheet(show: Boolean) = apiProvidersDelegate.onShowTtsProvidersSheet(_ttsState, show)
    fun onFetchTtsModels() = apiProvidersDelegate.onFetchTtsModels(viewModelScope, TAG, _ttsState, _apiState, _currentApiKey)
    fun onStartCreateTtsProviderAsset() = apiProvidersDelegate.onStartCreateTtsProviderAsset(_ttsState)
    fun onTtsAssetFieldChange(asset: TtsProviderAssetUi) =
        apiProvidersDelegate.onTtsAssetFieldChange(
            viewModelScope, TAG, _ttsState, _apiState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, asset = asset
        )

    fun onSaveTtsProvider(onSuccess: () -> Unit) =
        apiProvidersDelegate.onSaveTtsProvider(
            viewModelScope, TAG, _ttsState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, onSuccess = onSuccess
        )

    fun onSelectTtsProviderAsset(asset: TtsProviderAssetUi?) =
        apiProvidersDelegate.onSelectTtsProviderAsset(viewModelScope, TAG, _ttsState, _apiState, _currentApiKey, asset)

    fun onShowMediaProvidersSheet(show: Boolean) = apiProvidersDelegate.onShowMediaProvidersSheet(_mediaState, show)
    fun onStartCreateMediaProviderAsset() = apiProvidersDelegate.onStartCreateMediaProviderAsset(_mediaState)
    fun onMediaAssetFieldChange(asset: MediaProviderAssetUi) =
        apiProvidersDelegate.onMediaAssetFieldChange(
            viewModelScope, TAG, _mediaState, _apiState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, asset = asset
        )

    fun onFetchMediaModels() = apiProvidersDelegate.onFetchMediaModels(viewModelScope, TAG, _mediaState, _currentApiKey)
    fun onSelectMediaProviderAsset(asset: MediaProviderAssetUi?) =
        apiProvidersDelegate.onSelectMediaProviderAsset(viewModelScope, TAG, _mediaState, _apiState, _currentApiKey, asset)

    fun onSaveMediaProvider(onSuccess: () -> Unit) =
        apiProvidersDelegate.onSaveMediaProvider(
            viewModelScope, TAG, _mediaState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, onSuccess = onSuccess
        )

    fun onDeleteApiModelAsset(id: ApiCredentialsId) = apiProvidersDelegate.onDeleteApiModelAsset(viewModelScope, _deletionState, TAG, id)
    fun onDeleteApiModelConfig(id: ApiModelConfigurationId, onSuccess: () -> Unit) =
        apiProvidersDelegate.onDeleteApiModelConfig(viewModelScope, _deletionState, TAG, id, onSuccess)

    fun onDeleteTtsProviderAsset(id: TtsProviderId) = apiProvidersDelegate.onDeleteTtsProviderAsset(viewModelScope, TAG, id)
    fun onDeleteMediaProviderAsset(id: MediaProviderId) = apiProvidersDelegate.onDeleteMediaProviderAsset(viewModelScope, TAG, id)
    fun onApiKeyChange(key: String) { _currentApiKey.value = key }
    fun onSaveApiModelConfig(onSuccess: () -> Unit) = apiProvidersDelegate.onSaveApiModelConfig(viewModelScope, TAG, _apiState, onSuccess)

    fun onSelectReusableApiCredential(id: ApiCredentialsId?) {
        val reusableCredential = uiState.value.apiProvidersSheet.assets
            .find { it.credentialsId == id }
            ?.let { ReusableApiCredentialUi(it.credentialsId, it.displayName, it.modelId, it.credentialAlias) }
        _currentApiKey.value = ""
        _apiState.value = _apiState.value.copy(
            selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
            selectedReusableApiCredentialName = reusableCredential?.displayName,
        )
    }

    fun onSelectReusableTtsApiCredential(id: ApiCredentialsId?) {
        val reusableCredential = uiState.value.apiProvidersSheet.assets
            .find { it.credentialsId == id }
            ?.let { ReusableApiCredentialUi(it.credentialsId, it.displayName, it.modelId, it.credentialAlias) }
        _currentApiKey.value = ""
        _apiState.value = _apiState.value.copy(
            discoveredApiModels = emptyList(),
            discoveredApiModelScope = null,
            isDiscoveringApiModels = false,
            modelSearchQuery = "",
            modelProviderFilters = emptySet(),
            modelSortOption = ModelSortOption.A_TO_Z,
        )
        _ttsState.value = _ttsState.value.copy(
            selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
            selectedReusableApiCredentialName = reusableCredential?.displayName,
        )
    }

    fun onSelectReusableMediaApiCredential(id: ApiCredentialsId?) {
        val reusableCredential = uiState.value.apiProvidersSheet.assets
            .find { it.credentialsId == id }
            ?.let { ReusableApiCredentialUi(it.credentialsId, it.displayName, it.modelId, it.credentialAlias) }
        _currentApiKey.value = ""
        _mediaState.value = _mediaState.value.copy(
            selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
            selectedReusableApiCredentialName = reusableCredential?.displayName,
        )
    }

    fun onUpdateModelSearchQuery(query: String) {
        _apiState.value = _apiState.value.copy(modelSearchQuery = query)
    }

    fun onUpdateMediaModelSearchQuery(query: String) {
        _mediaState.value = _mediaState.value.copy(modelSearchQuery = query)
    }

    fun onToggleModelProviderFilter(provider: String) {
        val filters = _apiState.value.modelProviderFilters
        _apiState.value = _apiState.value.copy(
            modelProviderFilters = if (filters.contains(provider)) filters - provider else filters + provider
        )
    }

    fun onToggleMediaModelProviderFilter(provider: String) {
        val filters = _mediaState.value.modelProviderFilters
        _mediaState.value = _mediaState.value.copy(
            modelProviderFilters = if (filters.contains(provider)) filters - provider else filters + provider
        )
    }

    fun onClearModelProviderFilters() {
        _apiState.value = _apiState.value.copy(modelProviderFilters = emptySet())
    }

    fun onClearMediaModelProviderFilters() {
        _mediaState.value = _mediaState.value.copy(modelProviderFilters = emptySet())
    }

    fun onUpdateModelSortOption(option: ModelSortOption) {
        _apiState.value = _apiState.value.copy(modelSortOption = option)
    }

    fun onUpdateMediaModelSortOption(option: ModelSortOption) {
        _mediaState.value = _mediaState.value.copy(modelSortOption = option)
    }

    fun onAddCustomHeader() {
        val draft = _apiState.value.presetDraft ?: return
        _apiState.value = _apiState.value.copy(presetDraft = draft.copy(customHeaders = draft.customHeaders + CustomHeaderUi()))
    }

    fun onDeleteCustomHeader(index: Int) {
        val draft = _apiState.value.presetDraft ?: return
        _apiState.value = _apiState.value.copy(
            presetDraft = draft.copy(customHeaders = draft.customHeaders.toMutableList().apply { removeAt(index) })
        )
    }

    fun onCustomHeaderChange(index: Int, header: CustomHeaderUi) {
        val draft = _apiState.value.presetDraft ?: return
        _apiState.value = _apiState.value.copy(
            presetDraft = draft.copy(customHeaders = draft.customHeaders.toMutableList().apply { this[index] = header })
        )
    }

    fun onCleanupCustomHeaders() {
        val draft = _apiState.value.presetDraft ?: return
        _apiState.value = _apiState.value.copy(
            presetDraft = draft.copy(customHeaders = draft.customHeaders.filter { it.key.isNotBlank() && it.value.isNotBlank() })
        )
    }

    fun onBackToMediaList() {
        _currentApiKey.value = ""
        _mediaState.value = _mediaState.value.copy(
            selectedAsset = null,
            assetDraft = null,
            selectedReusableApiCredentialAlias = null,
            selectedReusableApiCredentialName = null,
            isDiscoveringApiModels = false,
            discoveredApiModels = emptyList(),
        )
    }

    // --- Search ---
    fun onShowSearchSkillSheet(show: Boolean) = generalDelegate.onShowSearchSkillSheet(_sheetVisibility, show)
    fun onSearchSkillTabChange(index: Int) = generalDelegate.onSearchSkillTabChange(_sheetVisibility, index)
    fun onSearchEnabledChange(enabled: Boolean) = searchDelegate.onSearchEnabledChange(_searchSkillState, enabled)
    fun onTavilyApiKeyChange(key: String) =
        searchDelegate.onTavilyApiKeyChange(_currentTavilyApiKey, _searchSkillState, uiState.value.searchSkillEditor.tavilyKeyPresent, key)

    fun onClearTavilyApiKey() = searchDelegate.onClearTavilyApiKey(viewModelScope, TAG, _currentTavilyApiKey)
    fun onExaApiKeyChange(key: String) =
        searchDelegate.onExaApiKeyChange(_currentExaApiKey, _searchSkillState, uiState.value.searchSkillEditor.exaKeyPresent, key)

    fun onClearExaApiKey() = searchDelegate.onClearExaApiKey(viewModelScope, TAG, _currentExaApiKey)
    fun onSearchProviderChange(provider: SearchProvider) = searchDelegate.onSearchProviderChange(_searchSkillState, provider)
    fun onUpdateShowSearchProviderSwitchAlert(show: Boolean) =
        searchDelegate.onUpdateShowSearchProviderSwitchAlert(viewModelScope, TAG, show)

    fun onSaveSearchSkillSettings(onSuccess: () -> Unit) =
        searchDelegate.onSaveSearchSkillSettings(
            viewModelScope, TAG, uiState.value.searchSkillEditor,
            _searchSkillState, _currentTavilyApiKey, _currentExaApiKey, onSuccess
        )

    fun onXSearchEnabledChange(enabled: Boolean) = searchDelegate.onXSearchEnabledChange(_xSearchState, enabled)
    fun onXApiKeyChange(key: String) = searchDelegate.onXApiKeyChange(_currentXApiKey, key)
    fun onClearXApiKey() = searchDelegate.onClearXApiKey(viewModelScope, TAG, _currentXApiKey)
    fun onSaveXSearchSettings(onSuccess: () -> Unit) =
        searchDelegate.onSaveXSearchSettings(viewModelScope, TAG, uiState.value.xSearchEditor, _xSearchState, _currentXApiKey, onSuccess)

    // --- Assignments ---
    fun onSelectModelType(modelType: ModelType) {
        assignmentsDelegate.onShowAssignmentDialog(_assignmentState, true, modelType)
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to resolve model selection", "")) {
            val selection = assignmentUseCases.resolveAssignedModelSelection(modelType) ?: return@launch
            selection.apiAsset?.let { apiProvidersDelegate.onSelectApiAsset(viewModelScope, _apiState, _currentApiKey, it) }
        }
    }

    fun onSetDefaultModel(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId? = null,
        mediaProviderId: MediaProviderId? = null
    ) = assignmentsDelegate.onSetDefaultModel(
        viewModelScope, TAG, _assignmentState, modelType,
        localConfigId, apiConfigId, ttsProviderId, mediaProviderId
    )

    fun onShowAssignmentDialog(show: Boolean, modelType: ModelType?) =
        assignmentsDelegate.onShowAssignmentDialog(_assignmentState, show, modelType)

    // --- Deletion Safety ---
    fun onConfirmDeletionWithReassignment(
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?
    ) = deletionHelper.onConfirmDeletionWithReassignment(
        viewModelScope, TAG, _deletionState,
        replacementLocalConfigId, replacementApiConfigId
    )

    fun onDismissDeletionSafety() = deletionHelper.onDismissDeletionSafety(_deletionState)

    // --- Navigation / Multi-State Resets ---
    fun onClearSelectedModel() {
        localModelsDelegate.onSelectLocalModelAsset(_localModelsState, null)
        apiProvidersDelegate.onSelectApiProviderAsset(viewModelScope, _apiState, _currentApiKey, null)
    }

    fun onBackToByokList() {
        _currentApiKey.value = ""
        _currentTavilyApiKey.value = ""
        _currentExaApiKey.value = ""
        _searchSkillState.value = SearchSkillTransientState()
        apiProvidersDelegate.onShowByokSheet(_apiState, true)
    }
}
