package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.TtsVoices

private const val DEFAULT_GOOGLE_TTS_MODEL_ID = "gemini-3.1-flash-tts-preview"

@OptIn(ExperimentalMaterial3Api::class)
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
    val showSavedIndicator = isKeySaved && apiKey.isEmpty()
    var passwordVisible by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    var showModelSelectionSheet by remember { mutableStateOf(false) }

    val isSaveEnabled = draft.displayName.isNotBlank() &&
            draft.voiceName.isNotBlank() &&
            (apiKey.isNotBlank() || isKeySaved)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("TTS Provider", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onSaveTtsProvider,
                    enabled = isSaveEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save TTS Provider", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Provider Selection
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false }
                ) {
                    listOf(ApiProvider.OPENAI, ApiProvider.XAI, ApiProvider.GOOGLE).forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onTtsAssetFieldChange(draft.copy(provider = provider, voiceName = ""))
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Reusable Key Dropdown
            val reusableCredentials = uiState.apiProvidersSheet.assets
                .filter { it.provider == draft.provider }
                .map { ReusableApiCredentialUi(it.credentialsId, it.displayName, it.modelId, it.credentialAlias) }
            
            if (draft.id.value.isEmpty() && reusableCredentials.isNotEmpty()) {
                ReusableCredentialDropdown(
                    reusableCredentials = reusableCredentials,
                    selectedReusableCredential = uiState.ttsProviderEditor.selectedReusableCredential,
                    onSelectReusableCredential = onSelectReusableApiCredential
                )
            }

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("API Key")
                        if (showSavedIndicator) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Key saved",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    if (showSavedIndicator) {
                        Text("Saved securely")
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    if (isKeySaved) {
                        Text(
                            text = if (apiKey.isEmpty()) {
                                "The stored key stays encrypted and hidden. Typing here switches back to a new key."
                            } else {
                                "You are entering a new key. The previously stored key will be replaced upon saving."
                            }
                        )
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Toggle password visibility" else "Toggle password visibility"
                        )
                    }
                }
            )

            // Display Name
            OutlinedTextField(
                value = draft.displayName,
                onValueChange = { onTtsAssetFieldChange(draft.copy(displayName = it)) },
                label = { Text("Display Name (e.g. My OpenAI TTS)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (draft.provider == ApiProvider.GOOGLE) {
                val discovery = uiState.apiProviderEditor.discovery
                val hasModels = discovery.models.isNotEmpty()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (hasModels) Modifier.clickable { showModelSelectionSheet = true } else Modifier)
                    ) {
                        OutlinedTextField(
                            value = draft.modelName ?: DEFAULT_GOOGLE_TTS_MODEL_ID,
                            onValueChange = { onTtsAssetFieldChange(draft.copy(modelName = it)) },
                            readOnly = hasModels,
                            enabled = !hasModels,
                            label = { Text("Model ID") },
                            supportingText = {
                                Text(
                                    text = when {
                                        discovery.isLoading -> "Fetching models..."
                                        hasModels -> "${discovery.models.size} models discovered"
                                        else -> "Discover models or enter a model ID manually"
                                    }
                                )
                            },
                            trailingIcon = if (hasModels) {
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
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }

                    TextButton(
                        onClick = onFetchApiModels,
                        enabled = !uiState.apiProviderEditor.discovery.isLoading && (apiKey.isNotBlank() || isKeySaved),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (uiState.apiProviderEditor.discovery.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (hasModels) "Refresh Models" else "Discover Models")
                    }
                }
            }

            // Base URL (for proxies)
            if (draft.provider == ApiProvider.OPENAI) {
                OutlinedTextField(
                    value = draft.baseUrl ?: "",
                    onValueChange = { onTtsAssetFieldChange(draft.copy(baseUrl = it)) },
                    label = { Text("Base URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Voice Selection
            val voices = TtsVoices.getVoicesForProvider(draft.provider)
            ExposedDropdownMenuBox(
                expanded = voiceDropdownExpanded,
                onExpandedChange = { voiceDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = voices.find { it.id == draft.voiceName }?.displayName ?: "Select a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = voiceDropdownExpanded,
                    onDismissRequest = { voiceDropdownExpanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                onTtsAssetFieldChange(draft.copy(voiceName = voice.id))
                                voiceDropdownExpanded = false
                            }
                        )
                    }
                }
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
    PocketCrewTheme {
        TtsConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                ttsProviderEditor = TtsProviderEditorUiState(
                    assetDraft = TtsProviderAssetUi(
                        displayName = "My Google TTS",
                        provider = ApiProvider.GOOGLE,
                        voiceName = "Puck",
                        modelName = "gemini-3.1-flash-tts-preview"
                    )
                )
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
