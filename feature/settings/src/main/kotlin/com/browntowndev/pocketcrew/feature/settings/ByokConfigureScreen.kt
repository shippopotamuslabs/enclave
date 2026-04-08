package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

@Composable
fun ByokConfigureRoute(
    onNavigateBack: () -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.currentApiKey.collectAsStateWithLifecycle()

    val handleBack: () -> Unit = {
        viewModel.onBackToByokList()
        onNavigateBack()
    }

    ByokConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onNavigateToCustomHeaders = onNavigateToCustomHeaders,
        onApiModelAssetFieldChange = viewModel::onApiModelAssetFieldChange,
        onApiModelConfigFieldChange = viewModel::onApiModelConfigFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableApiCredential,
        onFetchApiModels = viewModel::onFetchApiModels,
        onUpdateModelSearchQuery = viewModel::onUpdateModelSearchQuery,
        onUpdateModelProviderFilter = viewModel::onUpdateModelProviderFilter,
        onUpdateModelSortOption = viewModel::onUpdateModelSortOption,
        onSaveApiCredentials = {
            viewModel.onSaveApiCredentials(onSuccess = { _, configUi ->
                if (configUi == null) {
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    onApiModelAssetFieldChange: (ApiModelAssetUi) -> Unit,
    onApiModelConfigFieldChange: (ApiModelConfigUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (Long?) -> Unit,
    onFetchApiModels: () -> Unit,
    onUpdateModelSearchQuery: (String) -> Unit,
    onUpdateModelProviderFilter: (String?) -> Unit,
    onUpdateModelSortOption: (ModelSortOption) -> Unit,
    onSaveApiCredentials: () -> Unit,
    onSaveApiModelConfig: () -> Unit
) {
    val isPresetMode = uiState.selectedApiModelConfig != null
    val title = if (isPresetMode) "Configure Preset" else "Provider Credentials"
    val draftAsset = uiState.apiCredentialDraft ?: ApiModelAssetUi(
        credentialsId = 0,
        displayName = "",
        provider = ApiProvider.ANTHROPIC,
        modelId = "",
        baseUrl = ApiProvider.ANTHROPIC.defaultBaseUrl(),
        isVision = false,
        credentialAlias = "",
        configurations = emptyList()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val selectedConfig = uiState.selectedApiModelConfig
            if (selectedConfig != null) {
                PresetConfigurationForm(
                    provider = draftAsset.provider,
                    parameterSupport = uiState.selectedApiModelParameterSupport,
                    config = selectedConfig,
                    selectedModelMetadata = uiState.discoveredApiModels.find {
                        it.modelId == draftAsset.modelId
                    },
                    onConfigChange = onApiModelConfigFieldChange,
                    onNavigateToCustomHeaders = onNavigateToCustomHeaders,
                    onSave = onSaveApiModelConfig
                )
            } else {
                CredentialsConfigurationForm(
                    asset = draftAsset,
                    reusableCredentials = uiState.apiModels
                        .filter { existing ->
                            existing.provider == draftAsset.provider &&
                                existing.credentialsId != draftAsset.credentialsId
                        }
                        .map { existing ->
                            ReusableApiCredentialUi(
                                credentialsId = existing.credentialsId,
                                displayName = existing.displayName,
                                modelId = existing.modelId,
                                credentialAlias = existing.credentialAlias,
                            )
                        },
                    selectedReusableCredential = uiState.selectedReusableApiCredential,
                    apiKey = apiKey,
                    availableModels = uiState.discoveredApiModels,
                    filteredModels = uiState.filteredDiscoveredApiModels,
                    isFetchingModels = uiState.isDiscoveringApiModels,
                    searchQuery = uiState.modelSearchQuery,
                    providerFilter = uiState.modelProviderFilter,
                    sortOption = uiState.modelSortOption,
                    onAssetChange = onApiModelAssetFieldChange,
                    onApiKeyChange = onApiKeyChange,
                    onSelectReusableCredential = onSelectReusableApiCredential,
                    onFetchModels = onFetchApiModels,
                    onUpdateSearchQuery = onUpdateModelSearchQuery,
                    onUpdateProviderFilter = onUpdateModelProviderFilter,
                    onUpdateSortOption = onUpdateModelSortOption,
                    onSave = onSaveApiCredentials
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
