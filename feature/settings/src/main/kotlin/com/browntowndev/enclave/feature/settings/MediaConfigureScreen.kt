package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.neo.NeoBackScreenHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.collect

@Composable
internal fun MediaConfigureRoute(
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
        viewModel.onBackToMediaList()
        onNavigateBack()
    }

    MediaConfigureScreen(
        uiState = uiState,
        apiKey = apiKey,
        onNavigateBack = handleBack,
        onMediaAssetFieldChange = viewModel::onMediaAssetFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSelectReusableApiCredential = viewModel::onSelectReusableMediaApiCredential,
        onFetchMediaModels = viewModel::onFetchMediaModels,
        onUpdateModelSearchQuery = viewModel::onUpdateMediaModelSearchQuery,
        onToggleModelProviderFilter = viewModel::onToggleMediaModelProviderFilter,
        onClearModelProviderFilters = viewModel::onClearMediaModelProviderFilters,
        onUpdateModelSortOption = viewModel::onUpdateMediaModelSortOption,
        onSaveMediaProvider = {
            viewModel.onSaveMediaProvider(onSuccess = {
                viewModel.onBackToMediaList()
                onNavigateBack()
            })
        },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onMediaAssetFieldChange: (MediaProviderAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (String?) -> Unit,
    onFetchMediaModels: () -> Unit,
    onUpdateModelSearchQuery: (String) -> Unit,
    onToggleModelProviderFilter: (String) -> Unit,
    onClearModelProviderFilters: () -> Unit,
    onUpdateModelSortOption: (ModelSortOption) -> Unit,
    onSaveMediaProvider: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val draft = uiState.mediaProviderEditor.assetDraft ?: MediaProviderAssetUi()
    val isKeySaved = uiState.mediaProviderEditor.selectedReusableCredential != null || draft.id.value.isNotEmpty()
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var capabilityDropdownExpanded by remember { mutableStateOf(false) }

    val isSaveEnabled =
        isMediaProviderSaveEnabled(
            displayName = draft.displayName,
            modelName = draft.modelName,
            capability = draft.capability,
            tasks = draft.tasks,
            apiKeyPresent = apiKey.isNotBlank() || isKeySaved,
        )
    val discoveredTasksForModel =
        uiState.mediaProviderEditor.discovery.models
            .find { it.modelId == draft.modelName }
            ?.tasks
            .orEmpty()
    val locksTaskToggles = draft.provider.locksMediaTaskToggles(discoveredTasksForModel)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoBackScreenHeader(
                title = "Media Generation Models",
                onBackClick = onNavigateBack,
            )
        },
        bottomBar = {
            NeoPrimaryButton(
                text = "Save Media Provider",
                onClick = onSaveMediaProvider,
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
            // Provider Selection
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                NeoOutlinedTextField(
                    value = draft.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false },
                ) {
                    listOf(ApiProvider.OPENAI, ApiProvider.XAI, ApiProvider.GOOGLE, ApiProvider.FAL).forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onMediaAssetFieldChange(
                                    draft.copy(
                                        provider = provider,
                                        baseUrl = if (provider.showsMediaBaseUrlOverride()) draft.baseUrl else null,
                                    ),
                                )
                                providerDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            // Capability Selection
            ExposedDropdownMenuBox(
                expanded = capabilityDropdownExpanded,
                onExpandedChange = { capabilityDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                NeoOutlinedTextField(
                    value = draft.capability.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Capability") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capabilityDropdownExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = capabilityDropdownExpanded,
                    onDismissRequest = { capabilityDropdownExpanded = false },
                ) {
                    MediaCapability.entries.forEach { capability ->
                        DropdownMenuItem(
                            text = { Text(capability.displayName) },
                            onClick = {
                                onMediaAssetFieldChange(draft.copy(capability = capability))
                                capabilityDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            val reusableCredentials =
                reusableCredentialsForProvider(
                    provider = draft.provider,
                    apiAssets = uiState.apiProvidersSheet.assets,
                    mediaAssets = uiState.mediaProvidersSheet.assets,
                    ttsAssets = uiState.ttsProvidersSheet.assets,
                    excludeMediaProviderId = draft.id.takeIf { it.value.isNotEmpty() },
                )
            val canUseStoredCredential = draft.id.value.isEmpty() && reusableCredentials.isNotEmpty()

            ApiKeyWithReuseSection(
                reusableCredentials = reusableCredentials,
                selectedReusableCredential = uiState.mediaProviderEditor.selectedReusableCredential,
                canUseStoredCredential = canUseStoredCredential,
                apiKey = apiKey,
                keyPresent = isKeySaved,
                onApiKeyChange = onApiKeyChange,
                onSelectReusableCredential = onSelectReusableApiCredential,
            )

            // Display Name
            NeoOutlinedTextField(
                value = draft.displayName,
                onValueChange = { onMediaAssetFieldChange(draft.copy(displayName = it)) },
                label = { Text("Display Name (e.g. OpenAI Images 2)") },
                modifier = Modifier.fillMaxWidth(),
            )

            // Model ID
            val canFetchModels = !uiState.mediaProviderEditor.discovery.isLoading && (apiKey.isNotBlank() || isKeySaved)
            ModelSelectionField(
                config = ApiModelConfigUi(modelId = draft.modelName),
                discovery = uiState.mediaProviderEditor.discovery,
                canFetchModels = canFetchModels,
                onConfigChange = { updatedConfig ->
                    val discoveredModel =
                        uiState.mediaProviderEditor.discovery.models
                            .find { it.modelId == updatedConfig.modelId }
                    val tasks = discoveredModel?.tasks ?: emptySet()
                    onMediaAssetFieldChange(
                        draft.copy(
                            modelName = updatedConfig.modelId,
                            tasks = tasks,
                        ),
                    )
                },
                callbacks =
                    ModelDiscoveryFieldCallbacks(
                        onFetchModels = onFetchMediaModels,
                        onUpdateSearchQuery = onUpdateModelSearchQuery,
                        onToggleProviderFilter = onToggleModelProviderFilter,
                        onClearProviderFilters = onClearModelProviderFilters,
                        onUpdateSortOption = onUpdateModelSortOption,
                    ),
            )

            if (draft.provider.showsMediaBaseUrlOverride()) {
                NeoOutlinedTextField(
                    value = draft.baseUrl ?: "",
                    onValueChange = { onMediaAssetFieldChange(draft.copy(baseUrl = it)) },
                    label = { Text("Base URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (draft.capability.requiresMediaTaskSelection()) {
                Text(
                    text = "Supported Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val relevantTasks = MediaTask.entries.filter { it.kind == draft.capability }
                relevantTasks.forEach { task ->
                    val isChecked = task in draft.tasks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = mediaTaskDisplayLabel(task),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val newTasks = if (checked) draft.tasks + task else draft.tasks - task
                                onMediaAssetFieldChange(draft.copy(tasks = newTasks))
                            },
                            enabled = !locksTaskToggles,
                        )
                    }
                }
            }

            // Use as Default Toggle
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use as Default",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Set this as the default model for ${draft.capability.displayName.lowercase()} generation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = draft.useAsDefault,
                    onCheckedChange = { onMediaAssetFieldChange(draft.copy(useAsDefault = it)) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "Media Configure Screen")
@Composable
fun MediaConfigureScreenPreview() {
    EnclaveTheme {
        MediaConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    mediaProviderEditor =
                        MediaProviderEditorUiState(
                            assetDraft =
                                MediaProviderAssetUi(
                                    provider = ApiProvider.OPENAI,
                                    capability = MediaCapability.IMAGE,
                                ),
                        ),
                ),
            apiKey = "",
            onNavigateBack = {},
            onMediaAssetFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onFetchMediaModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
            onSaveMediaProvider = {},
        )
    }
}
