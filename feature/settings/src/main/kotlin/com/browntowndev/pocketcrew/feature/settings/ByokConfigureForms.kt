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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningControlStyle
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsConfigurationForm(
    asset: ApiModelAssetUi,
    reusableCredentials: List<ReusableApiCredentialUi>,
    selectedReusableCredential: ReusableApiCredentialUi?,
    apiKey: String,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    isFetchingModels: Boolean,
    searchQuery: String,
    providerFilter: String?,
    sortOption: ModelSortOption,
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableCredential: (Long?) -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateProviderFilter: (String?) -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit
) {
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var reusableCredentialDropdownExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showModelSelectionSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Provider Selection
        ExposedDropdownMenuBox(
            expanded = providerDropdownExpanded,
            onExpandedChange = { providerDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = asset.provider.displayName,
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
                ApiProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            val nextBaseUrl = when {
                                asset.baseUrl == asset.provider.defaultBaseUrl() -> provider.defaultBaseUrl()
                                asset.baseUrl.isNullOrBlank() -> provider.defaultBaseUrl()
                                else -> asset.baseUrl
                            }
                            onAssetChange(
                                asset.copy(
                                    provider = provider,
                                    baseUrl = nextBaseUrl,
                                    modelId = ""
                                )
                            )
                            providerDropdownExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = asset.displayName,
            onValueChange = { onAssetChange(asset.copy(displayName = it)) },
            label = { Text("Display Name (e.g. My OpenAI)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        val canUseStoredCredential = asset.credentialsId == 0L && reusableCredentials.isNotEmpty()

        val canFetchModels = !isFetchingModels && (apiKey.isNotBlank() || asset.credentialsId != 0L || selectedReusableCredential != null)

        Box(modifier = Modifier.fillMaxWidth().clickable { showModelSelectionSheet = true }) {
            OutlinedTextField(
                value = asset.modelId,
                onValueChange = { onAssetChange(asset.copy(modelId = it)) },
                readOnly = true,
                enabled = false,
                label = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Model")
                        if (availableModels.isEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Model discovery is optional. You can always enter a model ID manually.")
                        }
                    }
                },
                placeholder = {
                    Text(
                        if (availableModels.isEmpty()) {
                            "Type a model ID or load models"
                        } else {
                            "Type a model ID or choose one below"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                trailingIcon = {
                    // Invisible spacer to reserve space for the overlaid icon
                    Spacer(modifier = Modifier.size(24.dp))
                }
            )
            
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isFetchingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (availableModels.isEmpty()) {
                    IconButton(
                        onClick = onFetchModels,
                        enabled = canFetchModels,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load Models"
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showModelSelectionSheet = true },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Model"
                        )
                    }
                }
            }
        }

        if (showModelSelectionSheet) {
            ModelSelectionBottomSheet(
                availableModels = availableModels,
                filteredModels = filteredModels,
                searchQuery = searchQuery,
                providerFilter = providerFilter,
                sortOption = sortOption,
                onUpdateSearchQuery = onUpdateSearchQuery,
                onUpdateProviderFilter = onUpdateProviderFilter,
                onUpdateSortOption = onUpdateSortOption,
                onModelSelected = { selectedModelId ->
                    onAssetChange(asset.copy(modelId = selectedModelId))
                },
                onDismissRequest = { showModelSelectionSheet = false }
            )
        }

        if (canUseStoredCredential) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { reusableCredentialDropdownExpanded = true }) {
                    Text(
                        text = if (selectedReusableCredential == null) {
                            "Use Existing"
                        } else {
                            "Using ${selectedReusableCredential.displayName}"
                        }
                    )
                }
                DropdownMenu(
                    expanded = reusableCredentialDropdownExpanded,
                    onDismissRequest = { reusableCredentialDropdownExpanded = false }
                ) {
                    if (selectedReusableCredential != null) {
                        DropdownMenuItem(
                            text = { Text("Don't reuse a saved key") },
                            onClick = {
                                onSelectReusableCredential(null)
                                reusableCredentialDropdownExpanded = false
                            }
                        )
                    }
                    reusableCredentials.forEach { credential ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(credential.displayName)
                                    Text(
                                        text = credential.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelectReusableCredential(credential.credentialsId)
                                reusableCredentialDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                if (selectedReusableCredential != null) {
                    Text("Using saved key from ${selectedReusableCredential.displayName}")
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = {
                if (selectedReusableCredential != null) {
                    Text("The stored key stays encrypted and hidden. Typing here switches back to a new key.")
                }
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        OutlinedTextField(
            value = asset.baseUrl ?: "",
            onValueChange = { onAssetChange(asset.copy(baseUrl = it)) },
            label = { Text("Base URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetConfigurationForm(
    provider: ApiProvider,
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onNavigateToCustomHeaders: () -> Unit
) {
    val reasoningPolicy = parameterSupport.reasoningPolicy
    var reasoningDropdownExpanded by remember(config.reasoningEffort, reasoningPolicy) { mutableStateOf(false) }
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasReadonlyContextWindow = selectedModelMetadata?.contextWindowTokens != null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        OutlinedTextField(
            value = config.displayName,
            onValueChange = { onConfigChange(config.copy(displayName = it)) },
            label = { Text("Preset Name (e.g. Creative)") },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Box(
            modifier = Modifier.fillMaxWidth().clickable { showSystemPromptSheet = true }
        ) {
            OutlinedTextField(
                value = config.systemPrompt.ifBlank { "Tap to edit system prompt" },
                onValueChange = {},
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("System Prompt")
                        Spacer(modifier = Modifier.width(4.dp))
                        PersistentTooltip(
                            description = "Certain pipeline slots (like Crew Mode or Vision) require specialized system prompts to function correctly. Use the Import button to load a template."
                        )
                    }
                },
                enabled = false,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 4,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
        
        if (showSystemPromptSheet) {
            JumpFreeModalBottomSheet(
                onDismissRequest = { showSystemPromptSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edit System Prompt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        var expanded by remember { mutableStateOf(false) }
                        Row {
                            TextButton(
                                onClick = { expanded = true }
                            ) {
                                Text("Import Template")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                SystemPromptTemplates.getAll().forEach { (modelType, prompt) ->
                                    DropdownMenuItem(
                                        text = { Text(modelType.displayName()) },
                                        onClick = {
                                            onConfigChange(config.copy(systemPrompt = prompt))
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = config.systemPrompt,
                        onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Enter system prompt...") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showSystemPromptSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        if (provider == ApiProvider.OPENROUTER) {
            OpenRouterRoutingCard(
                routing = config.openRouterRouting,
                onRoutingChange = { onConfigChange(config.copy(openRouterRouting = it)) }
            )
        }

        ConfigurationHeader("Context")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = config.contextWindow,
                onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
                readOnly = hasReadonlyContextWindow,
                label = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context Window")
                        Spacer(modifier = Modifier.width(4.dp))
                        PersistentTooltip(description = "Total tokens (input + output) the model can process at once.")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )

            if (parameterSupport.supportsMaxTokens) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = config.maxTokens,
                    onValueChange = { onConfigChange(config.copy(maxTokens = it)) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Max Tokens")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Maximum number of tokens the model can generate in a single response.")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
            }
        }

        ConfigurationHeader("Tuning")

        if (parameterSupport.supportsTopK) {
            OutlinedTextField(
                value = config.topK,
                onValueChange = { onConfigChange(config.copy(topK = it)) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Top K")
                        Spacer(modifier = Modifier.width(4.dp))
                        PersistentTooltip(description = "Limits the next token choice to the K most likely tokens.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )
        }

        if (parameterSupport.supportsReasoningEffort) {
            ExposedDropdownMenuBox(
                expanded = reasoningDropdownExpanded,
                onExpandedChange = { reasoningDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = reasoningSelectionLabel(config.reasoningEffort, reasoningPolicy),
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) "Agent Count" else "Reasoning")
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                                "Select agent count"
                            } else {
                                "Select reasoning level"
                            }
                        )
                    }
                )
                ExposedDropdownMenu(
                    expanded = reasoningDropdownExpanded,
                    onDismissRequest = { reasoningDropdownExpanded = false }
                ) {
                    reasoningOptions(reasoningPolicy).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onConfigChange(config.copy(reasoningEffort = option.effort))
                                reasoningDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                    "xAI multi-agent maps 4 agents to low reasoning and 16 agents to high reasoning."
                } else {
                    "Reasoning support varies by provider and model."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (parameterSupport.supportsMinP) {
            TuningSlider(
                label = "Min P",
                description = "Limits the next token choice to tokens with probability at least P times the probability of the most likely token.",
                value = config.minP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) }
            )
        }

        TuningSlider(
            label = "Temperature",
            description = "Controls randomness. Lower values are more deterministic; higher values are more creative.",
            value = config.temperature.toFloat(),
            range = 0f..2f,
            onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) }
        )

        TuningSlider(
            label = "Top P",
            description = "Limits the next token choice to a subset of tokens whose cumulative probability exceeds P.",
            value = config.topP.toFloat(),
            range = 0f..1f,
            onValueChange = { onConfigChange(config.copy(topP = it.toDouble())) }
        )

        if (parameterSupport.supportsFrequencyPenalty) {
            TuningSlider(
                label = "Frequency Penalty",
                description = "Penalizes tokens based on their frequency in the generated text so far.",
                value = config.frequencyPenalty.toFloat(),
                range = -2f..2f,
                onValueChange = { onConfigChange(config.copy(frequencyPenalty = it.toDouble())) }
            )
        }

        if (parameterSupport.supportsPresencePenalty) {
            TuningSlider(
                label = "Presence Penalty",
                description = "Penalizes tokens based on whether they have appeared at least once in the generated text.",
                value = config.presencePenalty.toFloat(),
                range = -2f..2f,
                onValueChange = { onConfigChange(config.copy(presencePenalty = it.toDouble())) }
            )
        }

        ConfigurationHeader("Headers")

        CustomHeadersList(
            customHeaders = config.customHeaders,
            onNavigateToCustomHeaders = onNavigateToCustomHeaders
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Credentials")
@Composable
fun PreviewByokConfigureCredentials() {
    PocketCrewTheme {
        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                apiProviderEditor = ApiProviderEditorUiState(
                    assetDraft = MockSettingsData.apiModels[0],
                    presetDraft = null,
                )
            ),
            onNavigateToCustomHeaders = {},
            apiKey = "sk-....",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onUpdateModelProviderFilter = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Preset Tuning")
@Composable
fun PreviewByokConfigurePreset() {
    PocketCrewTheme {
        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                apiProviderEditor = ApiProviderEditorUiState(
                    assetDraft = MockSettingsData.apiModels[0],
                    presetDraft = MockSettingsData.apiModels[0].configurations[0],
                )
            ),
            apiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onUpdateModelProviderFilter = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Preset with Headers")
@Composable
fun PreviewByokConfigurePresetWithHeaders() {
    PocketCrewTheme {
        val headers = listOf(
            CustomHeaderUi("X-Custom-Header-1", "Value 1"),
            CustomHeaderUi("X-Custom-Header-2", "Value 2"),
            CustomHeaderUi("X-Custom-Header-3", "Value 3"),
            CustomHeaderUi("X-Custom-Header-4", "Value 4")
        )
        val mockConfig = MockSettingsData.apiModels[0].configurations[0].copy(
            customHeaders = headers
        )

        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                apiProviderEditor = ApiProviderEditorUiState(
                    assetDraft = MockSettingsData.apiModels[0],
                    presetDraft = mockConfig,
                )
            ),
            apiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onUpdateModelProviderFilter = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - OpenRouter Preset")
@Composable
fun PreviewByokConfigureOpenRouterPreset() {
    PocketCrewTheme {
        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                apiProviderEditor = ApiProviderEditorUiState(
                    assetDraft = MockSettingsData.apiModels[2],
                    presetDraft = MockSettingsData.apiModels[2].configurations[0],
                )
            ),
            apiKey = "sk-or-....",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onUpdateModelProviderFilter = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
        )
    }
}
