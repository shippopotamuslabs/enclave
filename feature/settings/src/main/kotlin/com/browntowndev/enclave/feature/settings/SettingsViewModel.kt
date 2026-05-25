package com.browntowndev.enclave.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveUsageSpendSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
internal class SettingsViewModel
    @Inject
    constructor(
        private val generalDelegate: SettingsGeneralDelegate,
        private val memoriesDelegate: SettingsMemoriesDelegate,
        private val dataControlsDelegate: SettingsDataControlsDelegate,
        private val localModelsDelegate: SettingsLocalModelsDelegate,
        private val apiProvidersDelegate: SettingsApiProvidersDelegate,
        private val searchDelegate: SettingsSearchDelegate,
        private val assignmentsDelegate: SettingsAssignmentsDelegate,
        private val deletionHelper: SettingsDeletionHelper,
        settingsUseCases: SettingsUseCases,
        private val settingsUiStateFactory: SettingsUiStateFactory,
        private val observeUsageSpendSummaryUseCase: ObserveUsageSpendSummaryUseCase,
        private val errorHandler: ViewModelErrorHandler,
        private val embeddingProgressPort: EmbeddingProgressPort,
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
        private val _dataControlsState = MutableStateFlow(DataControlsTransientState())
        private val _xSearchState = MutableStateFlow(XSearchTransientState())
        private val _vaultReauthSheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
        private val _usageSpendPeriod = MutableStateFlow(UsagePeriodUi.LIFETIME)
        private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

        private val _currentApiKey = MutableStateFlow("")
        private val _currentTavilyApiKey = MutableStateFlow("")
        private val _currentExaApiKey = MutableStateFlow("")
        private val _currentXApiKey = MutableStateFlow("")

        val currentApiKey: StateFlow<String> = _currentApiKey
        val currentTavilyApiKey: StateFlow<String> = _currentTavilyApiKey
        val currentXApiKey: StateFlow<String> = _currentXApiKey
        val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

        private val localModelUseCases = settingsUseCases.localModels
        private val assignmentUseCases = settingsUseCases.assignments
        private val memoriesUseCases = settingsUseCases.memories

        private val localModelAssetsFlow = localModelUseCases.getLocalModelAssets()
        private val memoriesFlow =
            memoriesUseCases.getAllMemoriesFlow().map { memories ->
                memories.map { StoredMemory(it.id, it.content, it.category) }
            }

        private val usageSpendSummaryFlow =
            _usageSpendPeriod.flatMapLatest { period ->
                observeUsageSpendSummaryUseCase(period.toDomain())
            }

        val uiState: StateFlow<SettingsUiState> =
            settingsUiStateFactory
                .observeUiState(
                    SettingsUiStateSources(
                        persistedSettings = settingsUseCases.preferences.getSettings(),
                        localAssets = localModelAssetsFlow,
                        apiAssets = settingsUseCases.apiProviders.getApiModelAssets(),
                        ttsAssets = settingsUseCases.tts.getTtsProviders(),
                        mediaAssets = settingsUseCases.media.getMediaProviders(),
                        defaultModels = assignmentUseCases.getDefaultModels(),
                        authState = settingsUseCases.auth.getAuthState(),
                        sheetVisibility = _sheetVisibility,
                        memories = memoriesFlow,
                        feedbackText = _feedbackText,
                        localModelsState = _localModelsState,
                        apiState = _apiState,
                        ttsState = _ttsState,
                        mediaState = _mediaState,
                        searchSkillState = _searchSkillState,
                        assignmentsState = _assignmentState,
                        deletionState = _deletionState,
                        memoriesState = _memoriesState,
                        dataControlsState = _dataControlsState,
                        currentTavilyApiKey = _currentTavilyApiKey,
                        currentExaApiKey = _currentExaApiKey,
                        currentXApiKey = _currentXApiKey,
                        xSearchState = _xSearchState,
                        isReindexing = embeddingProgressPort.isReindexing,
                        reindexProgress = embeddingProgressPort.progress,
                        vaultReauthSheet = _vaultReauthSheet,
                        usageSpendSummary = usageSpendSummaryFlow,
                    ),
                ).stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = SettingsUiState(),
                )

        init {
            localModelsDelegate.observeActiveLocalModels(viewModelScope, localModelAssetsFlow, _localModelsState)
        }

        // --- General / Auth ---
        fun onThemeChange(theme: AppTheme) = generalDelegate.onThemeChange(viewModelScope, TAG, theme)

        fun onHapticResponseChange(enabled: Boolean) = generalDelegate.onHapticResponseChange(viewModelScope, TAG, enabled)

        fun onBackgroundInferenceChange(enabled: Boolean) = generalDelegate.onBackgroundInferenceChange(viewModelScope, TAG, enabled)

        fun onLoginWithGoogle() = generalDelegate.onLoginWithGoogle(viewModelScope, TAG, _vaultReauthSheet)

        fun onLogout() = generalDelegate.onLogout(viewModelScope, TAG)

        /**
         * Settings re-auth sheet handlers — the post-Settings-sign-in flow that mirrors the
         * cold-start vault unlock + reconciliation in `MainViewModel`.
         */
        fun onSubmitVaultReauthPassword(password: String) =
            generalDelegate.onSubmitVaultReauthPassword(viewModelScope, TAG, _vaultReauthSheet, password)

        fun onConfirmAdoptCloudFromSettings() = generalDelegate.onConfirmAdoptCloud(viewModelScope, TAG, _vaultReauthSheet)

        fun onCancelVaultReauth() = generalDelegate.onCancelVaultReauth(_vaultReauthSheet)

        fun onShowCustomizationSheet(show: Boolean) = generalDelegate.onShowCustomizationSheet(_sheetVisibility, show)

        fun onCustomizationEnabledChange(enabled: Boolean) = generalDelegate.onCustomizationEnabledChange(viewModelScope, TAG, enabled)

        fun onPromptOptionChange(option: SystemPromptOption) = generalDelegate.onPromptOptionChange(viewModelScope, TAG, option)

        fun onCustomPromptTextChange(text: String) = generalDelegate.onCustomPromptTextChange(viewModelScope, TAG, text)

        fun onSaveCustomization() = generalDelegate.onSaveCustomization(_sheetVisibility)

        fun onShowTosSheet(show: Boolean) = generalDelegate.onShowTosSheet(_sheetVisibility, show)

        fun onShowFeedbackSheet(show: Boolean) = generalDelegate.onShowFeedbackSheet(_sheetVisibility, show)

        fun onShowUsageSpendSheet(show: Boolean) = generalDelegate.onShowUsageSpendSheet(_sheetVisibility, show)

        fun onUsageSpendPeriodChange(period: UsagePeriodUi) {
            _usageSpendPeriod.value = period
        }

        fun onShowOssLicensesSheet(show: Boolean) = generalDelegate.onShowOssLicensesSheet(_sheetVisibility, show)

        fun onShowVisionSettingsSheet(show: Boolean) = generalDelegate.onShowVisionSettingsSheet(_sheetVisibility, show)

        fun onFeedbackTextChange(text: String) = generalDelegate.onFeedbackTextChange(_feedbackText, text)

        fun onSubmitFeedback() = generalDelegate.onSubmitFeedback(_feedbackText, _sheetVisibility)

        // --- Memories ---
        fun onShowDataControlsSheet(show: Boolean) =
            dataControlsDelegate.onShowDataControlsSheet(_sheetVisibility, _dataControlsState, show)

        fun onRequestDeleteAllChats() = dataControlsDelegate.onRequestDeleteAllChats(_dataControlsState)

        fun onRequestDeleteAllMedia() = dataControlsDelegate.onRequestDeleteAllMedia(_dataControlsState)

        fun onRequestDeleteAllMemories() = dataControlsDelegate.onRequestDeleteAllMemories(_dataControlsState)

        fun onRequestDeleteAllArtifacts() = dataControlsDelegate.onRequestDeleteAllArtifacts(_dataControlsState)

        fun onRequestDeleteAccount() = dataControlsDelegate.onRequestDeleteAccount(_dataControlsState)

        fun onDismissDataControlsConfirmation() = dataControlsDelegate.onDismissDataControlsConfirmation(_dataControlsState)

        fun onConfirmDeleteAllChats() =
            dataControlsDelegate.onConfirmDeleteAllChats(viewModelScope, TAG, _dataControlsState) { _snackbarMessages.tryEmit(it) }

        fun onConfirmDeleteAllMedia() =
            dataControlsDelegate.onConfirmDeleteAllMedia(viewModelScope, TAG, _dataControlsState) { _snackbarMessages.tryEmit(it) }

        fun onConfirmDeleteAllMemories() =
            dataControlsDelegate.onConfirmDeleteAllMemories(viewModelScope, TAG, _dataControlsState) { _snackbarMessages.tryEmit(it) }

        fun onConfirmDeleteAllArtifacts() =
            dataControlsDelegate.onConfirmDeleteAllArtifacts(viewModelScope, TAG, _dataControlsState) { _snackbarMessages.tryEmit(it) }

        fun onConfirmDeleteAccount() = dataControlsDelegate.onConfirmDeleteAccount(viewModelScope, TAG, _dataControlsState)

        fun onShowMemoriesSheet(show: Boolean) = memoriesDelegate.onShowMemoriesSheet(_sheetVisibility, _memoriesState, show)

        fun onAddMemory() = memoriesDelegate.onAddMemory(_memoriesState)

        fun onEditMemory(memory: StoredMemory) = memoriesDelegate.onEditMemory(_memoriesState, memory)

        fun onUpdateMemoryDraft(
            text: String,
            category: MemoryCategory,
        ) = memoriesDelegate.onUpdateMemoryDraft(_memoriesState, text, category)

        fun onSaveMemory() = memoriesDelegate.onSaveMemory(viewModelScope, TAG, _memoriesState)

        fun onCancelMemoryEdit() = memoriesDelegate.onCancelMemoryEdit(_memoriesState)

        fun onDeleteMemory(memoryId: String) = memoriesDelegate.onDeleteMemory(viewModelScope, TAG, memoryId)

        fun onAllowMemoriesChange(enabled: Boolean) = memoriesDelegate.onAllowMemoriesChange(viewModelScope, TAG, enabled)

        fun onDeleteAllMemories(memories: List<StoredMemory>) = memoriesDelegate.onDeleteAllMemories(viewModelScope, TAG, memories)

        // --- Local Models ---
        fun onShowModelConfigSheet(show: Boolean) = localModelsDelegate.onShowModelConfigSheet(_localModelsState, show)

        fun onSelectLocalModelAsset(asset: LocalModelAssetUi?) = localModelsDelegate.onSelectLocalModelAsset(_localModelsState, asset)

        fun onSelectLocalModelConfig(config: LocalModelConfigUi?) = localModelsDelegate.onSelectLocalModelConfig(_localModelsState, config)

        fun onLocalModelConfigFieldChange(config: LocalModelConfigUi) =
            localModelsDelegate.onModelConfigFieldChange(_localModelsState, config)

        fun onSaveLocalModelConfig(onSuccess: () -> Unit) =
            localModelsDelegate.onSaveLocalModelConfig(viewModelScope, TAG, _localModelsState, onSuccess)

        fun onDeleteLocalModelConfig(
            id: LocalModelConfigurationId,
            onSuccess: () -> Unit,
        ) = localModelsDelegate.onDeleteLocalModelConfig(viewModelScope, _deletionState, TAG, id, onSuccess)

        fun onDeleteLocalModelAsset(id: com.browntowndev.enclave.domain.model.config.LocalModelId) =
            localModelsDelegate.onDeleteLocalModelAsset(viewModelScope, _deletionState, TAG, id)

        fun onReDownloadLocalModel(id: com.browntowndev.enclave.domain.model.config.LocalModelId) =
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

        fun onTestApiProviderConnection() =
            apiProvidersDelegate.onTestApiProviderConnection(viewModelScope, _apiState, _currentApiKey) {
                _snackbarMessages.tryEmit(it)
            }

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
                viewModelScope,
                TAG,
                _ttsState,
                _apiState,
                _currentApiKey,
                assignmentsUseCases = assignmentUseCases,
                asset = asset,
            )

        fun onSaveTtsProvider(onSuccess: () -> Unit) =
            apiProvidersDelegate.onSaveTtsProvider(
                viewModelScope,
                TAG,
                _ttsState,
                _currentApiKey,
                assignmentsUseCases = assignmentUseCases,
                onSuccess = onSuccess,
            )

        fun onSelectTtsProviderAsset(asset: TtsProviderAssetUi?) =
            apiProvidersDelegate.onSelectTtsProviderAsset(viewModelScope, TAG, _ttsState, _apiState, _currentApiKey, asset)

        fun onShowMediaProvidersSheet(show: Boolean) = apiProvidersDelegate.onShowMediaProvidersSheet(_mediaState, show)

        fun onStartCreateMediaProviderAsset() = apiProvidersDelegate.onStartCreateMediaProviderAsset(_mediaState)

        fun onMediaAssetFieldChange(asset: MediaProviderAssetUi) =
            apiProvidersDelegate.onMediaAssetFieldChange(
                viewModelScope,
                TAG,
                _mediaState,
                _apiState,
                _currentApiKey,
                assignmentsUseCases = assignmentUseCases,
                asset = asset,
            )

        fun onFetchMediaModels() = apiProvidersDelegate.onFetchMediaModels(viewModelScope, TAG, _mediaState, _currentApiKey)

        fun onSelectMediaProviderAsset(asset: MediaProviderAssetUi?) =
            apiProvidersDelegate.onSelectMediaProviderAsset(viewModelScope, TAG, _mediaState, _apiState, _currentApiKey, asset)

        fun onSaveMediaProvider(onSuccess: () -> Unit) =
            apiProvidersDelegate.onSaveMediaProvider(
                viewModelScope,
                TAG,
                _mediaState,
                _currentApiKey,
                assignmentsUseCases = assignmentUseCases,
                onSuccess = onSuccess,
            )

        fun onDeleteApiModelAsset(id: ApiCredentialsId) =
            apiProvidersDelegate.onDeleteApiModelAsset(viewModelScope, _deletionState, TAG, id)

        fun onDeleteApiModelConfig(
            id: ApiModelConfigurationId,
            onSuccess: () -> Unit,
        ) = apiProvidersDelegate.onDeleteApiModelConfig(viewModelScope, _deletionState, TAG, id, onSuccess)

        fun onDeleteTtsProviderAsset(id: TtsProviderId) = apiProvidersDelegate.onDeleteTtsProviderAsset(viewModelScope, TAG, id)

        fun onDeleteMediaProviderAsset(id: MediaProviderId) = apiProvidersDelegate.onDeleteMediaProviderAsset(viewModelScope, TAG, id)

        fun onApiKeyChange(key: String) {
            apiProvidersDelegate.onApiKeyChange(_apiState, _currentApiKey, key)
        }

        fun onSaveApiModelConfig(onSuccess: () -> Unit) =
            apiProvidersDelegate.onSaveApiModelConfig(viewModelScope, TAG, _apiState, onSuccess)

        fun onSelectReusableApiCredential(id: ApiCredentialsId?) {
            val reusableCredential =
                uiState.value.apiProvidersSheet.assets
                    .find { it.credentialsId == id }
                    ?.let {
                        ReusableApiCredentialUi(
                            credentialsId = it.credentialsId,
                            displayName = it.displayName,
                            providerLabel = it.provider.displayName,
                            credentialAlias = it.credentialAlias,
                        )
                    }
            _currentApiKey.value = ""
            _apiState.value =
                _apiState.value.copy(
                    selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                    selectedReusableApiCredentialName = reusableCredential?.displayName,
                    connectionTestStatus = ApiConnectionTestStatus.IDLE,
                )
        }

        fun onSelectReusableTtsApiCredential(id: ApiCredentialsId?) {
            val reusableCredential =
                uiState.value.apiProvidersSheet.assets
                    .find { it.credentialsId == id }
                    ?.let {
                        ReusableApiCredentialUi(
                            credentialsId = it.credentialsId,
                            displayName = it.displayName,
                            providerLabel = it.provider.displayName,
                            credentialAlias = it.credentialAlias,
                        )
                    }
            _currentApiKey.value = ""
            _apiState.value =
                _apiState.value.copy(
                    discoveredApiModels = emptyList(),
                    discoveredApiModelScope = null,
                    isDiscoveringApiModels = false,
                    modelSearchQuery = "",
                    modelProviderFilters = emptySet(),
                    modelSortOption = ModelSortOption.A_TO_Z,
                )
            _ttsState.value =
                _ttsState.value.copy(
                    selectedReusableApiCredentialAlias = reusableCredential?.credentialAlias,
                    selectedReusableApiCredentialName = reusableCredential?.displayName,
                )
        }

        fun onSelectReusableMediaApiCredential(id: ApiCredentialsId?) {
            val reusableCredential =
                uiState.value.apiProvidersSheet.assets
                    .find { it.credentialsId == id }
                    ?.let {
                        ReusableApiCredentialUi(
                            credentialsId = it.credentialsId,
                            displayName = it.displayName,
                            providerLabel = it.provider.displayName,
                            credentialAlias = it.credentialAlias,
                        )
                    }
            _currentApiKey.value = ""
            _mediaState.value =
                _mediaState.value.copy(
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
            _apiState.value =
                _apiState.value.copy(
                    modelProviderFilters = if (filters.contains(provider)) filters - provider else filters + provider,
                )
        }

        fun onToggleMediaModelProviderFilter(provider: String) {
            val filters = _mediaState.value.modelProviderFilters
            _mediaState.value =
                _mediaState.value.copy(
                    modelProviderFilters = if (filters.contains(provider)) filters - provider else filters + provider,
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
            _apiState.value =
                _apiState.value.copy(
                    presetDraft = draft.copy(customHeaders = draft.customHeaders.toMutableList().apply { removeAt(index) }),
                )
        }

        fun onCustomHeaderChange(
            index: Int,
            header: CustomHeaderUi,
        ) {
            val draft = _apiState.value.presetDraft ?: return
            _apiState.value =
                _apiState.value.copy(
                    presetDraft = draft.copy(customHeaders = draft.customHeaders.toMutableList().apply { this[index] = header }),
                )
        }

        fun onCleanupCustomHeaders() {
            val draft = _apiState.value.presetDraft ?: return
            _apiState.value =
                _apiState.value.copy(
                    presetDraft = draft.copy(customHeaders = draft.customHeaders.filter { it.key.isNotBlank() && it.value.isNotBlank() }),
                )
        }

        fun onBackToMediaList() {
            _currentApiKey.value = ""
            _mediaState.value =
                _mediaState.value.copy(
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
            searchDelegate.onTavilyApiKeyChange(
                _currentTavilyApiKey,
                _searchSkillState,
                uiState.value.searchSkillEditor.tavilyKeyPresent,
                key,
            )

        fun onClearTavilyApiKey() = searchDelegate.onClearTavilyApiKey(viewModelScope, TAG, _currentTavilyApiKey)

        fun onExaApiKeyChange(key: String) =
            searchDelegate.onExaApiKeyChange(_currentExaApiKey, _searchSkillState, uiState.value.searchSkillEditor.exaKeyPresent, key)

        fun onClearExaApiKey() = searchDelegate.onClearExaApiKey(viewModelScope, TAG, _currentExaApiKey)

        fun onSearchProviderChange(provider: SearchProvider) = searchDelegate.onSearchProviderChange(_searchSkillState, provider)

        fun onUpdateShowSearchProviderSwitchAlert(show: Boolean) =
            searchDelegate.onUpdateShowSearchProviderSwitchAlert(viewModelScope, TAG, show)

        fun onSaveSearchSkillSettings(onSuccess: () -> Unit) =
            searchDelegate.onSaveSearchSkillSettings(
                viewModelScope,
                TAG,
                uiState.value.searchSkillEditor,
                _searchSkillState,
                _currentTavilyApiKey,
                _currentExaApiKey,
                onSuccess,
            )

        fun onXSearchEnabledChange(enabled: Boolean) = searchDelegate.onXSearchEnabledChange(_xSearchState, enabled)

        fun onXApiKeyChange(key: String) = searchDelegate.onXApiKeyChange(_currentXApiKey, key)

        fun onClearXApiKey() = searchDelegate.onClearXApiKey(viewModelScope, TAG, _currentXApiKey)

        fun onSaveXSearchSettings(onSuccess: () -> Unit) =
            searchDelegate.onSaveXSearchSettings(
                viewModelScope,
                TAG,
                uiState.value.xSearchEditor,
                _xSearchState,
                _currentXApiKey,
                onSuccess,
            )

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
            mediaProviderId: MediaProviderId? = null,
        ) = assignmentsDelegate.onSetDefaultModel(
            viewModelScope,
            TAG,
            _assignmentState,
            modelType,
            localConfigId,
            apiConfigId,
            ttsProviderId,
            mediaProviderId,
        )

        fun onShowAssignmentDialog(
            show: Boolean,
            modelType: ModelType?,
        ) = assignmentsDelegate.onShowAssignmentDialog(_assignmentState, show, modelType)

        // --- Deletion Safety ---
        fun onConfirmDeletionWithReassignment(
            replacementLocalConfigId: LocalModelConfigurationId?,
            replacementApiConfigId: ApiModelConfigurationId?,
        ) = deletionHelper.onConfirmDeletionWithReassignment(
            viewModelScope,
            TAG,
            _deletionState,
            replacementLocalConfigId,
            replacementApiConfigId,
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
        }

        fun onBackToTtsList() {
            _currentApiKey.value = ""
            _ttsState.value = TtsProvidersTransientState()
        }
    }

private fun UsagePeriodUi.toDomain(): UsagePeriod =
    when (this) {
        UsagePeriodUi.LIFETIME -> UsagePeriod.LIFETIME
        UsagePeriodUi.CURRENT_MONTH -> UsagePeriod.CURRENT_MONTH
    }
