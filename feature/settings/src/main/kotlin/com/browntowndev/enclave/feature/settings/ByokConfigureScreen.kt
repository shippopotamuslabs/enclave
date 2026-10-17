package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveBackScreenHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.collect

@Composable
internal fun LiveVoiceConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.currentApiKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val handleBack: () -> Unit = {
        viewModel.onBackToLiveVoiceList()
        onNavigateBack()
    }

    LiveVoiceConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onLiveVoiceAssetFieldChange = viewModel::onLiveVoiceAssetFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableLiveVoiceApiCredential,
        onSaveLiveVoiceProvider = {
            viewModel.onSaveLiveVoiceProvider(onSuccess = {
                viewModel.onBackToLiveVoiceList()
                onNavigateBack()
            })
        },
        onFetchLiveVoiceModels = viewModel::onFetchLiveVoiceModels,
        onUpdateModelSearchQuery = viewModel::onUpdateLiveVoiceModelSearchQuery,
        onToggleModelProviderFilter = viewModel::onToggleLiveVoiceModelProviderFilter,
        onClearModelProviderFilters = viewModel::onClearLiveVoiceModelProviderFilters,
        onUpdateModelSortOption = viewModel::onUpdateLiveVoiceModelSortOption,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun TtsConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.currentApiKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val handleBack: () -> Unit = {
        viewModel.onBackToTtsList()
        onNavigateBack()
    }

    TtsConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onTtsAssetFieldChange = viewModel::onTtsAssetFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableTtsApiCredential,
        onSaveTtsProvider = {
            viewModel.onSaveTtsProvider(onSuccess = {
                viewModel.onBackToTtsList()
                onNavigateBack()
            })
        },
        onFetchApiModels = viewModel::onFetchTtsModels,
        onUpdateModelSearchQuery = viewModel::onUpdateTtsModelSearchQuery,
        onToggleModelProviderFilter = viewModel::onToggleTtsModelProviderFilter,
        onClearModelProviderFilters = viewModel::onClearTtsModelProviderFilters,
        onUpdateModelSortOption = viewModel::onUpdateTtsModelSortOption,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun ByokConfigureRoute(
    onNavigateBack: () -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.currentApiKey.collectAsStateWithLifecycle()
    val tavilyApiKey by viewModel.currentTavilyApiKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val handleBack: () -> Unit = {
        viewModel.onBackToByokList()
        onNavigateBack()
    }

    ByokConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        searchApiKey = tavilyApiKey,
        onNavigateBack = handleBack,
        onNavigateToCustomHeaders = onNavigateToCustomHeaders,
        onApiModelAssetFieldChange = viewModel::onApiModelAssetFieldChange,
        onApiModelConfigFieldChange = viewModel::onApiModelConfigFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSearchEnabledChange = viewModel::onSearchEnabledChange,
        onSearchApiKeyChange = viewModel::onTavilyApiKeyChange,
        onClearSearchApiKey = viewModel::onClearTavilyApiKey,
        onSelectReusableApiCredential = viewModel::onSelectReusableApiCredential,
        onFetchApiModels = viewModel::onFetchApiModels,
        onTestApiProviderConnection = viewModel::onTestApiProviderConnection,
        onUpdateModelSearchQuery = viewModel::onUpdateModelSearchQuery,
        onToggleModelProviderFilter = viewModel::onToggleModelProviderFilter,
        onClearModelProviderFilters = viewModel::onClearModelProviderFilters,
        onUpdateModelSortOption = viewModel::onUpdateModelSortOption,
        onShowOpenRouterProvidersSheet = { modelId, credentialAlias, baseUrl ->
            viewModel.onShowOpenRouterProvidersSheet(
                show = true,
                modelId = modelId,
                currentApiKey = apiKey,
                credentialAlias = credentialAlias,
                baseUrl = baseUrl,
            )
        },
        onDismissOpenRouterProvidersSheet = {
            viewModel.onShowOpenRouterProvidersSheet(show = false)
        },
        onSaveApiCredentials = {
            viewModel.onSaveApiCredentials(onSuccess = { _, createdPreset ->
                if (createdPreset == null) {
                    viewModel.onBackToByokList()
                    onNavigateBack()
                }
            })
        },
        onSaveApiModelConfig = {
            viewModel.onSaveApiModelConfig(onSuccess = {
                viewModel.onBackToByokList()
                onNavigateBack()
            })
        },
        onSaveSearchSettings = {
            viewModel.onSaveSearchSkillSettings(onSuccess = {
                viewModel.onBackToByokList()
                onNavigateBack()
            })
        },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
fun ByokConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    searchApiKey: String,
    onNavigateBack: () -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    onApiModelAssetFieldChange: (ApiModelAssetUi) -> Unit,
    onApiModelConfigFieldChange: (ApiModelConfigUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSearchApiKeyChange: (String) -> Unit,
    onClearSearchApiKey: () -> Unit,
    onSelectReusableApiCredential: (String?) -> Unit,
    onFetchApiModels: () -> Unit,
    onTestApiProviderConnection: () -> Unit,
    onUpdateModelSearchQuery: (String) -> Unit,
    onToggleModelProviderFilter: (String) -> Unit,
    onClearModelProviderFilters: () -> Unit,
    onUpdateModelSortOption: (ModelSortOption) -> Unit,
    onShowOpenRouterProvidersSheet: (modelId: String, credentialAlias: String?, baseUrl: String?) -> Unit = { _, _, _ -> },
    onDismissOpenRouterProvidersSheet: () -> Unit = {},
    onSaveApiCredentials: () -> Unit,
    onSaveApiModelConfig: () -> Unit,
    onSaveSearchSettings: () -> Unit,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
) {
    val isSearchMode = uiState.searchSkillEditor.isEditing
    val isPresetMode = uiState.apiProviderEditor.presetDraft != null
    val selectedConfig = uiState.apiProviderEditor.presetDraft
    val title =
        when {
            isSearchMode -> "Web Search"
            isPresetMode -> "Configure Preset"
            else -> "Provider Credentials"
        }
    val draftAsset =
        uiState.apiProviderEditor.assetDraft ?: ApiModelAssetUi(
            credentialsId = ApiCredentialsId(""),
            displayName = "",
            provider = ApiProvider.ANTHROPIC,
            baseUrl = ApiProvider.ANTHROPIC.defaultBaseUrl(),
            credentialAlias = "",
            configurations = emptyList(),
        )

    val isSaveEnabled =
        if (isSearchMode) {
            !uiState.searchSkillEditor.enabled ||
                uiState.searchSkillEditor.tavilyKeyPresent ||
                searchApiKey.isNotBlank()
        } else if (selectedConfig != null) {
            val parameterSupport = uiState.apiProviderEditor.parameterSupport
            val maxTokensValid = !parameterSupport.supportsMaxTokens || selectedConfig.maxTokens.isNotBlank()
            val topKValid = !parameterSupport.supportsTopK || selectedConfig.topK.isNotBlank()
            selectedConfig.displayName.isNotBlank() &&
                selectedConfig.modelId.isNotBlank() &&
                maxTokensValid &&
                selectedConfig.contextWindow.isNotBlank() &&
                topKValid
        } else {
            draftAsset.displayName.isNotBlank() &&
                (
                    apiKey.isNotBlank() ||
                        uiState.apiProviderEditor.selectedReusableCredential != null ||
                        draftAsset.credentialsId.value.isNotEmpty()
                )
        }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EnclaveBackScreenHeader(
                title = title,
                onBackClick = onNavigateBack,
            )
        },
        bottomBar = {
            EnclavePrimaryButton(
                text =
                    when {
                        isSearchMode -> "Save Search Settings"
                        isPresetMode -> "Save Preset"
                        else -> "Save Credentials"
                    },
                onClick =
                    when {
                        isSearchMode -> onSaveSearchSettings
                        isPresetMode -> onSaveApiModelConfig
                        else -> onSaveApiCredentials
                    },
                enabled = isSaveEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                        .padding(bottom = 20.dp),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            if (isSearchMode) {
                SearchSkillConfigurationForm(
                    state = uiState.searchSkillEditor,
                    apiKey = searchApiKey,
                    onEnabledChange = onSearchEnabledChange,
                    onApiKeyChange = onSearchApiKeyChange,
                    onClearSavedKey = onClearSearchApiKey,
                )
            } else if (selectedConfig != null) {
                PresetConfigurationForm(
                    provider = draftAsset.provider.toUi(),
                    parameterSupport = uiState.apiProviderEditor.parameterSupport,
                    config = selectedConfig,
                    selectedModelMetadata =
                        uiState.apiProviderEditor.discovery.models.find {
                            it.modelId == selectedConfig.modelId
                        },
                    discovery = uiState.apiProviderEditor.discovery,
                    canFetchModels =
                        !uiState.apiProviderEditor.discovery.isLoading &&
                            (
                                apiKey.isNotBlank() ||
                                    draftAsset.credentialsId.value.isNotEmpty() ||
                                    uiState.apiProviderEditor.selectedReusableCredential != null
                            ),
                    onConfigChange = onApiModelConfigFieldChange,
                    onNavigateToCustomHeaders = onNavigateToCustomHeaders,
                    discoveryCallbacks =
                        ModelDiscoveryFieldCallbacks(
                            onFetchModels = onFetchApiModels,
                            onUpdateSearchQuery = onUpdateModelSearchQuery,
                            onToggleProviderFilter = onToggleModelProviderFilter,
                            onClearProviderFilters = onClearModelProviderFilters,
                            onUpdateSortOption = onUpdateModelSortOption,
                        ),
                    onShowOpenRouterProvidersSheet = {
                        if (selectedConfig.modelId.isBlank()) {
                            return@PresetConfigurationForm
                        }
                        val credentialAlias =
                            uiState.apiProviderEditor.selectedReusableCredential?.credentialAlias
                                ?: draftAsset.credentialAlias.takeIf { it.isNotBlank() }
                        onShowOpenRouterProvidersSheet(
                            selectedConfig.modelId,
                            credentialAlias,
                            draftAsset.baseUrl,
                        )
                    },
                )
            } else {
                CredentialsConfigurationForm(
                    asset = draftAsset,
                    reusableCredentials =
                        reusableCredentialsForProvider(
                            provider = draftAsset.provider,
                            apiAssets = uiState.apiProvidersSheet.assets,
                            mediaAssets = uiState.mediaProvidersSheet.assets,
                            ttsAssets = uiState.ttsProvidersSheet.assets,
                            excludeApiCredentialsId = draftAsset.credentialsId.takeIf { it.value.isNotEmpty() },
                        ),
                    selectedReusableCredential = uiState.apiProviderEditor.selectedReusableCredential,
                    apiKey = apiKey,
                    connectionTestStatus = uiState.apiProviderEditor.connectionTestStatus,
                    onAssetChange = onApiModelAssetFieldChange,
                    onApiKeyChange = onApiKeyChange,
                    onSelectReusableCredential = onSelectReusableApiCredential,
                    onTestConnection = onTestApiProviderConnection,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.apiProviderEditor.isOpenRouterProvidersSheetOpen && selectedConfig != null) {
        OpenRouterProvidersBottomSheet(
            openRouterEndpointsState = uiState.apiProviderEditor.openRouterEndpointsState,
            selectedPreferredProvider = selectedConfig.openRouterRouting.preferredProvider,
            onSelectPreferredProvider = { providerSlug ->
                val openRouterRouting =
                    if (providerSlug.isNullOrBlank()) {
                        selectedConfig.openRouterRouting.copy(
                            preferredProvider = null,
                            allowFallbacks = true,
                        )
                    } else {
                        selectedConfig.openRouterRouting.copy(
                            preferredProvider = providerSlug,
                            allowFallbacks = false,
                        )
                    }
                onApiModelConfigFieldChange(
                    selectedConfig.copy(openRouterRouting = openRouterRouting),
                )
            },
            onDismissRequest = onDismissOpenRouterProvidersSheet,
        )
    }
}
