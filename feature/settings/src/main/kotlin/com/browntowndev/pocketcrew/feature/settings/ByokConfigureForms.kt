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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material3.Switch
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
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningControlStyle
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates

@Composable
fun SearchSkillConfigurationForm(
    state: SearchSkillEditorUiState,
    apiKey: String,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onClearSavedKey: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val showSavedIndicator = state.tavilyKeyPresent && apiKey.isEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Web Search",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Supported models can call the Tavily-backed search skill when this is on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isToggleActionable = apiKey.isNotBlank() || state.tavilyKeyPresent
            Switch(
                checked = state.enabled && isToggleActionable,
                onCheckedChange = onEnabledChange,
                enabled = isToggleActionable,
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tavily API Key")
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
            supportingText = {
                Text(
                    if (state.tavilyKeyPresent) {
                        if (apiKey.isEmpty()) {
                            "The stored key stays encrypted and hidden. Typing here switches back to a new key."
                        } else {
                            "You are entering a new key. The previously stored key will be replaced upon saving."
                        }
                    } else {
                        "Save a Tavily key before enabling live web search."
                    }
                )
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide Tavily API key" else "Show Tavily API key",
                    )
                }
            }
        )

        if (state.tavilyKeyPresent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClearSavedKey) {
                    Text("Clear Saved Key")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionField(
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit
) {
    var providerDropdownExpanded by remember { mutableStateOf(false) }

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionField(
    asset: ApiModelAssetUi,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    isFetchingModels: Boolean,
    canFetchModels: Boolean,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit
) {
    var showModelSelectionSheet by remember { mutableStateOf(false) }
    val hasModels = availableModels.isNotEmpty()

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasModels) Modifier.clickable { showModelSelectionSheet = true } else Modifier)
        ) {
            OutlinedTextField(
                value = asset.modelId,
                onValueChange = { onAssetChange(asset.copy(modelId = it)) },
                readOnly = true,
                enabled = false,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Model")
                        if (!hasModels) {
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Model discovery is optional. You can always enter a model ID manually.")
                        }
                    }
                },
                placeholder = {
                    Text(
                        if (!hasModels) {
                            "Type a model ID or load models"
                        } else {
                            "Type a model ID or choose one below"
                        }
                    )
                },
                supportingText = {
                    Text(
                        text = if (isFetchingModels) {
                            "Fetching models..."
                        } else if (hasModels) {
                            "${availableModels.size} models discovered"
                        } else {
                            "Load models for better configuration"
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
                trailingIcon = if (hasModels) ({
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }) else null
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onFetchModels,
                enabled = !isFetchingModels && canFetchModels,
            ) {
                if (isFetchingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary // Keep it primary or use contentColor
                    )
                    Text("Loading models\u2026")
                } else {
                    Icon(
                        imageVector = if (hasModels) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp)
                    )
                    Text(if (!hasModels) "Discover Models" else "Refresh Models")
                }
            }
        }
    }

    if (showModelSelectionSheet) {
        ModelSelectionBottomSheet(
            availableModels = availableModels,
            filteredModels = filteredModels,
            searchQuery = searchQuery,
            providerFilters = providerFilters,
            sortOption = sortOption,
            onUpdateSearchQuery = onUpdateSearchQuery,
            onToggleProviderFilter = onToggleProviderFilter,
            onClearProviderFilters = onClearProviderFilters,
            onUpdateSortOption = onUpdateSortOption,
            onModelSelected = { selectedModelId ->
                onAssetChange(asset.copy(modelId = selectedModelId))
            },
            onDismissRequest = { showModelSelectionSheet = false }
        )
    }
}

@Composable
fun VisionCapabilitySwitch(
    discoveredVisionCapability: Boolean?,
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Vision Enabled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (discoveredVisionCapability) {
                    true -> "This model reports image input support, so vision is enabled automatically."
                    false -> "This model reports text-only input, so vision is disabled automatically."
                    null -> "Turn this on when the selected model can accept image input."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = discoveredVisionCapability ?: asset.isMultimodal,
            onCheckedChange = { onAssetChange(asset.copy(isMultimodal = it)) },
            enabled = discoveredVisionCapability == null,
        )
    }
}

@Composable
fun ReusableCredentialDropdown(
    reusableCredentials: List<ReusableApiCredentialUi>,
    selectedReusableCredential: ReusableApiCredentialUi?,
    onSelectReusableCredential: (ApiCredentialsId?) -> Unit
) {
    var reusableCredentialDropdownExpanded by remember { mutableStateOf(false) }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsConfigurationForm(
    asset: ApiModelAssetUi,
    reusableCredentials: List<ReusableApiCredentialUi>,
    selectedReusableCredential: ReusableApiCredentialUi?,
    apiKey: String,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    selectedModelMetadata: DiscoveredApiModelUi?,
    isFetchingModels: Boolean,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableCredential: (ApiCredentialsId?) -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val discoveredVisionCapability = selectedModelMetadata?.isMultimodal
    val isKeySaved = selectedReusableCredential != null || asset.credentialsId.value.isNotEmpty()
    val showSavedIndicator = isKeySaved && apiKey.isEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProviderSelectionField(
            asset = asset,
            onAssetChange = onAssetChange
        )

        OutlinedTextField(
            value = asset.baseUrl ?: "",
            onValueChange = { onAssetChange(asset.copy(baseUrl = it)) },
            label = { Text("Base URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        val canUseStoredCredential = asset.credentialsId.value.isEmpty() && reusableCredentials.isNotEmpty()
        if (canUseStoredCredential) {
            ReusableCredentialDropdown(
                reusableCredentials = reusableCredentials,
                selectedReusableCredential = selectedReusableCredential,
                onSelectReusableCredential = onSelectReusableCredential
            )
        }

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
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        OutlinedTextField(
            value = asset.displayName,
            onValueChange = { onAssetChange(asset.copy(displayName = it)) },
            label = { Text("Display Name (e.g. My OpenAI)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        val canFetchModels = !isFetchingModels && (apiKey.isNotBlank() || asset.credentialsId.value.isNotEmpty() || selectedReusableCredential != null)
        ModelSelectionField(
            asset = asset,
            availableModels = availableModels,
            filteredModels = filteredModels,
            isFetchingModels = isFetchingModels,
            canFetchModels = canFetchModels,
            searchQuery = searchQuery,
            providerFilters = providerFilters,
            sortOption = sortOption,
            onAssetChange = onAssetChange,
            onFetchModels = onFetchModels,
            onUpdateSearchQuery = onUpdateSearchQuery,
            onToggleProviderFilter = onToggleProviderFilter,
            onClearProviderFilters = onClearProviderFilters,
            onUpdateSortOption = onUpdateSortOption
        )

        VisionCapabilitySwitch(
            discoveredVisionCapability = discoveredVisionCapability,
            asset = asset,
            onAssetChange = onAssetChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptConfiguration(
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit
) {
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
}
@Composable
fun ContextConfiguration(
    config: ApiModelConfigUi,
    hasReadonlyContextWindow: Boolean,
    supportsMaxTokens: Boolean,
    onConfigChange: (ApiModelConfigUi) -> Unit
) {
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

        if (supportsMaxTokens) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningConfiguration(
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit
) {
    val reasoningPolicy = parameterSupport.reasoningPolicy
    var reasoningDropdownExpanded by remember(config.reasoningEffort, reasoningPolicy) { mutableStateOf(false) }

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
}

@Composable
fun PresetConfigurationForm(
    provider: ApiProvider,
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onNavigateToCustomHeaders: () -> Unit
) {
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

        SystemPromptConfiguration(
            config = config,
            onConfigChange = onConfigChange
        )

        if (provider == ApiProvider.OPENROUTER) {
            OpenRouterRoutingCard(
                routing = config.openRouterRouting,
                onRoutingChange = { onConfigChange(config.copy(openRouterRouting = it)) }
            )
        }

        ConfigurationHeader("Context")

        ContextConfiguration(
            config = config,
            hasReadonlyContextWindow = hasReadonlyContextWindow,
            supportsMaxTokens = parameterSupport.supportsMaxTokens,
            onConfigChange = onConfigChange
        )

        ConfigurationHeader("Tuning")

        TuningConfiguration(
            parameterSupport = parameterSupport,
            config = config,
            onConfigChange = onConfigChange
        )

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
            searchApiKey = "",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSearchEnabledChange = {},
            onSearchApiKeyChange = {},
            onClearSearchApiKey = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onSaveSearchSettings = {},
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
            searchApiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSearchEnabledChange = {},
            onSearchApiKeyChange = {},
            onClearSearchApiKey = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onSaveSearchSettings = {},
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
            searchApiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSearchEnabledChange = {},
            onSearchApiKeyChange = {},
            onClearSearchApiKey = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onSaveSearchSettings = {},
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
            searchApiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSearchEnabledChange = {},
            onSearchApiKeyChange = {},
            onClearSearchApiKey = {},
            onSelectReusableApiCredential = {},
            onFetchApiModels = {},
            onUpdateModelSearchQuery = {},
            onToggleModelProviderFilter = {},
            onClearModelProviderFilters = {},
            onUpdateModelSortOption = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onSaveSearchSettings = {},
        )
    }
}
