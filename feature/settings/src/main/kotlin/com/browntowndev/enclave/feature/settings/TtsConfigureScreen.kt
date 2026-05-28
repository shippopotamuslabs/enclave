package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoApiKeyField
import com.browntowndev.enclave.core.ui.component.neo.NeoBackScreenHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.neoOutlinedTextFieldColors
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.TtsVoices

private const val DEFAULT_GOOGLE_TTS_MODEL_ID = "gemini-3.1-flash-tts-preview"

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun TtsConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onTtsAssetFieldChange: (TtsProviderAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableApiCredential: (ApiCredentialsId?) -> Unit,
    onSaveTtsProvider: () -> Unit,
    onFetchApiModels: () -> Unit,
    onUpdateModelSearchQuery: (String) -> Unit,
    onToggleModelProviderFilter: (String) -> Unit,
    onClearModelProviderFilters: () -> Unit,
    onUpdateModelSortOption: (ModelSortOption) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val draft = uiState.ttsProviderEditor.assetDraft ?: TtsProviderAssetUi()
    val isKeySaved = uiState.ttsProviderEditor.selectedReusableCredential != null || draft.id.value.isNotEmpty()
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    var showModelSelectionSheet by remember { mutableStateOf(false) }

    val isSaveEnabled =
        draft.displayName.isNotBlank() &&
            draft.voiceName.isNotBlank() &&
            (apiKey.isNotBlank() || isKeySaved)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoBackScreenHeader(
                title = "TTS Provider",
                onBackClick = onNavigateBack,
            )
        },
        bottomBar = {
            NeoPrimaryButton(
                text = "Save TTS Provider",
                onClick = onSaveTtsProvider,
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
                    listOf(ApiProvider.OPENAI, ApiProvider.XAI, ApiProvider.GOOGLE).forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onTtsAssetFieldChange(draft.copy(provider = provider, voiceName = ""))
                                providerDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            // Reusable Key Dropdown
            val reusableCredentials =
                uiState.apiProvidersSheet.assets
                    .filter { it.provider == draft.provider }
                    .map {
                        ReusableApiCredentialUi(
                            credentialsId = it.credentialsId,
                            displayName = it.displayName,
                            providerLabel = it.provider.displayName,
                            credentialAlias = it.credentialAlias,
                        )
                    }

            if (draft.id.value.isEmpty() && reusableCredentials.isNotEmpty()) {
                ReusableCredentialDropdown(
                    reusableCredentials = reusableCredentials,
                    selectedReusableCredential = uiState.ttsProviderEditor.selectedReusableCredential,
                    onSelectReusableCredential = onSelectReusableApiCredential,
                )
            }

            NeoApiKeyField(
                label = "API Key",
                value = apiKey,
                onValueChange = onApiKeyChange,
                keyPresent = isKeySaved,
                modifier = Modifier.fillMaxWidth(),
            )

            // Display Name
            NeoOutlinedTextField(
                value = draft.displayName,
                onValueChange = { onTtsAssetFieldChange(draft.copy(displayName = it)) },
                label = { Text("Display Name (e.g. My OpenAI TTS)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.provider == ApiProvider.GOOGLE) {
                val discovery = uiState.apiProviderEditor.discovery
                val hasModels = discovery.models.isNotEmpty()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modelHint =
                        when {
                            discovery.isLoading -> "Fetching models..."
                            hasModels -> "${discovery.models.size} models discovered"
                            else -> "Discover models or enter a model ID manually"
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (hasModels) Modifier.clickable { showModelSelectionSheet = true } else Modifier),
                    ) {
                        NeoOutlinedTextField(
                            value = draft.modelName ?: DEFAULT_GOOGLE_TTS_MODEL_ID,
                            onValueChange = { onTtsAssetFieldChange(draft.copy(modelName = it)) },
                            readOnly = hasModels,
                            enabled = !hasModels,
                            label = { Text("Model ID") },
                            trailingIcon =
                                if (hasModels) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select model",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                            modifier = Modifier.fillMaxWidth(),
                            colors = neoOutlinedTextFieldColors(),
                        )
                    }
                    Text(
                        text = modelHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                    )

                    TextButton(
                        onClick = onFetchApiModels,
                        enabled = !uiState.apiProviderEditor.discovery.isLoading && (apiKey.isNotBlank() || isKeySaved),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (uiState.apiProviderEditor.discovery.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading models\u2026")
                        } else {
                            Icon(
                                imageVector = if (hasModels) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (hasModels) "Refresh Models" else "Discover Models")
                        }
                    }
                }
            }

            // Base URL (for proxies)
            if (draft.provider == ApiProvider.OPENAI) {
                NeoOutlinedTextField(
                    value = draft.baseUrl ?: "",
                    onValueChange = { onTtsAssetFieldChange(draft.copy(baseUrl = it)) },
                    label = { Text("Base URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Voice Selection
            val voices = TtsVoices.getVoicesForProvider(draft.provider)
            ExposedDropdownMenuBox(
                expanded = voiceDropdownExpanded,
                onExpandedChange = { voiceDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                NeoOutlinedTextField(
                    value = voices.find { it.id == draft.voiceName }?.displayName ?: "Select a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = voiceDropdownExpanded,
                    onDismissRequest = { voiceDropdownExpanded = false },
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                onTtsAssetFieldChange(draft.copy(voiceName = voice.id))
                                voiceDropdownExpanded = false
                            },
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
                        text = "Set this as the default voice model for synthesized responses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = draft.useAsDefault,
                    onCheckedChange = { onTtsAssetFieldChange(draft.copy(useAsDefault = it)) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showModelSelectionSheet) {
        val discovery = uiState.apiProviderEditor.discovery
        ModelSelectionBottomSheet(
            availableModels = discovery.models,
            filteredModels = discovery.filteredModels,
            searchQuery = discovery.searchQuery,
            providerFilters = discovery.providerFilters,
            sortOption = discovery.sortOption,
            onUpdateSearchQuery = onUpdateModelSearchQuery,
            onToggleProviderFilter = onToggleModelProviderFilter,
            onClearProviderFilters = onClearModelProviderFilters,
            onUpdateSortOption = onUpdateModelSortOption,
            onModelSelected = { selectedModelId ->
                onTtsAssetFieldChange(draft.copy(modelName = selectedModelId))
            },
            onDismissRequest = { showModelSelectionSheet = false },
        )
    }
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "TTS Configure Screen")
@Composable
fun TtsConfigureScreenPreview() {
    EnclaveTheme {
        TtsConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    ttsProviderEditor =
                        TtsProviderEditorUiState(
                            assetDraft =
                                TtsProviderAssetUi(
                                    displayName = "My Google TTS",
                                    provider = ApiProvider.GOOGLE,
                                    voiceName = "Puck",
                                    modelName = "gemini-3.1-flash-tts-preview",
                                ),
                        ),
                ),
            apiKey = "test-key",
            onNavigateBack = {},
            onTtsAssetFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onSaveTtsProvider = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
        )
    }
}
