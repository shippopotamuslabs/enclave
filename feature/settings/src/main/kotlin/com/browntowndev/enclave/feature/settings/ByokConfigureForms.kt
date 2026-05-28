package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.PersistentTooltip
import com.browntowndev.enclave.core.ui.component.neo.NeoApiKeyField
import com.browntowndev.enclave.core.ui.component.neo.NeoFilterChip
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.neoOutlinedTextFieldColors
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningControlStyle

@Composable
fun SearchSkillConfigurationForm(
    state: SearchSkillEditorUiState,
    apiKey: String,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onClearSavedKey: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier =
                Modifier
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

        NeoApiKeyField(
            label = "Tavily API Key",
            value = apiKey,
            onValueChange = onApiKeyChange,
            keyPresent = state.tavilyKeyPresent,
            onClearKey = onClearSavedKey,
            hintWhenEmpty = "Save a Tavily key before enabling live web search.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionField(
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit,
) {
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = providerDropdownExpanded,
        onExpandedChange = { providerDropdownExpanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        NeoOutlinedTextField(
            value = asset.provider.displayName,
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
            ApiProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        val nextBaseUrl =
                            when {
                                asset.baseUrl == asset.provider.defaultBaseUrl() -> provider.defaultBaseUrl()
                                asset.baseUrl.isNullOrBlank() -> provider.defaultBaseUrl()
                                else -> asset.baseUrl
                            }
                        onAssetChange(
                            asset.copy(
                                provider = provider,
                                baseUrl = nextBaseUrl,
                            ),
                        )
                        providerDropdownExpanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionField(
    config: ApiModelConfigUi,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    isFetchingModels: Boolean,
    canFetchModels: Boolean,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
) {
    var showModelSelectionSheet by remember { mutableStateOf(false) }
    val hasModels = availableModels.isNotEmpty()

    Column {
        val modelHint =
            when {
                isFetchingModels -> "Fetching models..."
                hasModels -> "${availableModels.size} models discovered"
                else -> "Load models for better configuration"
            }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (hasModels) Modifier.clickable { showModelSelectionSheet = true } else Modifier),
        ) {
            NeoOutlinedTextField(
                value = config.modelId,
                onValueChange = { onConfigChange(config.copy(modelId = it)) },
                readOnly = hasModels,
                enabled = !hasModels,
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
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = neoOutlinedTextFieldColors(),
                trailingIcon =
                    if (hasModels) {
                        {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        null
                    },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onFetchModels,
                enabled = !isFetchingModels && canFetchModels,
            ) {
                if (isFetchingModels) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary, // Keep it primary or use contentColor
                    )
                    Text("Loading models\u2026")
                } else {
                    Icon(
                        imageVector = if (hasModels) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(end = 4.dp)
                                .size(16.dp),
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
                onConfigChange(config.copy(modelId = selectedModelId))
            },
            onDismissRequest = { showModelSelectionSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderModelSelectionField(
    modelId: String,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    isFetchingModels: Boolean,
    canFetchModels: Boolean,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onModelIdChange: (String) -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
) {
    val presetConfig = ApiModelConfigUi(modelId = modelId)
    ModelSelectionField(
        config = presetConfig,
        availableModels = availableModels,
        filteredModels = filteredModels,
        isFetchingModels = isFetchingModels,
        canFetchModels = canFetchModels,
        searchQuery = searchQuery,
        providerFilters = providerFilters,
        sortOption = sortOption,
        onConfigChange = { onModelIdChange(it.modelId) },
        onFetchModels = onFetchModels,
        onUpdateSearchQuery = onUpdateSearchQuery,
        onToggleProviderFilter = onToggleProviderFilter,
        onClearProviderFilters = onClearProviderFilters,
        onUpdateSortOption = onUpdateSortOption,
    )
}

@Composable
fun VisionCapabilitySwitch(
    discoveredVisionCapability: Boolean?,
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
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
                text =
                    when (discoveredVisionCapability) {
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
            checked = discoveredVisionCapability ?: config.isMultimodal,
            onCheckedChange = { onConfigChange(config.copy(isMultimodal = it)) },
            enabled = discoveredVisionCapability == null,
        )
    }
}

@Composable
fun ReusableCredentialDropdown(
    reusableCredentials: List<ReusableApiCredentialUi>,
    selectedReusableCredential: ReusableApiCredentialUi?,
    onSelectReusableCredential: (ApiCredentialsId?) -> Unit,
) {
    var reusableCredentialDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = { reusableCredentialDropdownExpanded = true }) {
            Text(
                text =
                    if (selectedReusableCredential == null) {
                        "Use Existing"
                    } else {
                        "Using ${selectedReusableCredential.displayName}"
                    },
            )
        }
        DropdownMenu(
            expanded = reusableCredentialDropdownExpanded,
            onDismissRequest = { reusableCredentialDropdownExpanded = false },
        ) {
            if (selectedReusableCredential != null) {
                DropdownMenuItem(
                    text = { Text("Don't reuse a saved key") },
                    onClick = {
                        onSelectReusableCredential(null)
                        reusableCredentialDropdownExpanded = false
                    },
                )
            }
            reusableCredentials.forEach { credential ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(credential.displayName)
                            Text(
                                text = credential.providerLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelectReusableCredential(credential.credentialsId)
                        reusableCredentialDropdownExpanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun CredentialsConfigurationForm(
    asset: ApiModelAssetUi,
    reusableCredentials: List<ReusableApiCredentialUi>,
    selectedReusableCredential: ReusableApiCredentialUi?,
    apiKey: String,
    connectionTestStatus: ApiConnectionTestStatus,
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectReusableCredential: (ApiCredentialsId?) -> Unit,
    onTestConnection: () -> Unit,
) {
    val isKeySaved = selectedReusableCredential != null || asset.credentialsId.value.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProviderSelectionField(
            asset = asset,
            onAssetChange = onAssetChange,
        )

        NeoOutlinedTextField(
            value = asset.baseUrl ?: "",
            onValueChange = { onAssetChange(asset.copy(baseUrl = it)) },
            label = { Text("Base URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        val canUseStoredCredential = asset.credentialsId.value.isEmpty() && reusableCredentials.isNotEmpty()
        if (canUseStoredCredential) {
            ReusableCredentialDropdown(
                reusableCredentials = reusableCredentials,
                selectedReusableCredential = selectedReusableCredential,
                onSelectReusableCredential = onSelectReusableCredential,
            )
        }

        NeoApiKeyField(
            label = "API Key",
            value = apiKey,
            onValueChange = onApiKeyChange,
            keyPresent = isKeySaved,
            modifier = Modifier.fillMaxWidth(),
        )

        val canTestConnection =
            apiKey.isNotBlank() ||
                selectedReusableCredential != null ||
                asset.credentialsId.value.isNotEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onTestConnection,
                enabled = canTestConnection && connectionTestStatus != ApiConnectionTestStatus.TESTING,
            ) {
                when (connectionTestStatus) {
                    ApiConnectionTestStatus.TESTING -> {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Testing…")
                    }
                    ApiConnectionTestStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .padding(end = 4.dp)
                                    .size(16.dp),
                        )
                        Text("Success")
                    }
                    ApiConnectionTestStatus.IDLE -> Text("Test")
                }
            }
        }

        NeoOutlinedTextField(
            value = asset.displayName,
            onValueChange = { onAssetChange(asset.copy(displayName = it)) },
            label = { Text("Display Name (e.g. My OpenAI)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptConfiguration(
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier.fillMaxWidth().clickable { showSystemPromptSheet = true },
    ) {
        NeoOutlinedTextField(
            value = config.systemPrompt.ifBlank { "Tap to edit system prompt" },
            onValueChange = {},
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("System Prompt")
                    Spacer(modifier = Modifier.width(4.dp))
                    PersistentTooltip(
                        description =
                            "Certain pipeline slots (like Crew Mode or Vision) require " +
                                "specialized system prompts to function correctly. " +
                                "Use the Import button to load a template.",
                    )
                }
            },
            enabled = false,
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 4,
            maxLines = 4,
            colors = neoOutlinedTextFieldColors(),
        )
    }

    SystemPromptEditSheet(
        visible = showSystemPromptSheet,
        prompt = config.systemPrompt,
        onPromptChange = { onConfigChange(config.copy(systemPrompt = it)) },
        onDismiss = { showSystemPromptSheet = false },
        sheetState = sheetState,
    )
}

@Composable
fun ContextConfiguration(
    config: ApiModelConfigUi,
    hasReadonlyContextWindow: Boolean,
    supportsMaxTokens: Boolean,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NeoOutlinedTextField(
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
                modifier = Modifier.fillMaxWidth().testTag("ApiContextWindowField"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }

        if (supportsMaxTokens) {
            Box(modifier = Modifier.weight(1f)) {
                NeoOutlinedTextField(
                    value = config.maxTokens,
                    onValueChange = { onConfigChange(config.copy(maxTokens = it)) },
                    enabled = !config.maxTokensDisabled,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Max Tokens")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Maximum number of tokens the model can generate in a single response.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("ApiMaxTokensField"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ParameterExclusionSection(
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigurationHeader("Advanced: Exclude Parameters")
        Text(
            text =
                "If a provider rejects specific parameters (like o1/o3 and temperature), " +
                    "you can explicitly exclude them from the API request here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (parameterSupport.supportsMaxTokens) {
                ParameterExclusionChip(
                    label = "Max Tokens",
                    selected = config.maxTokensDisabled,
                    onSelectedChange = { onConfigChange(config.copy(maxTokensDisabled = it)) },
                )
            }
            if (parameterSupport.supportsTemperature) {
                ParameterExclusionChip(
                    label = "Temperature",
                    selected = config.temperatureDisabled,
                    onSelectedChange = { onConfigChange(config.copy(temperatureDisabled = it)) },
                )
            }
            if (parameterSupport.supportsTopP) {
                ParameterExclusionChip(
                    label = "Top P",
                    selected = config.topPDisabled,
                    onSelectedChange = { onConfigChange(config.copy(topPDisabled = it)) },
                )
            }
            if (parameterSupport.supportsTopK) {
                ParameterExclusionChip(
                    label = "Top K",
                    selected = config.topKDisabled,
                    onSelectedChange = { onConfigChange(config.copy(topKDisabled = it)) },
                )
            }
            if (parameterSupport.supportsMinP) {
                ParameterExclusionChip(
                    label = "Min P",
                    selected = config.minPDisabled,
                    onSelectedChange = { onConfigChange(config.copy(minPDisabled = it)) },
                )
            }
            if (parameterSupport.supportsFrequencyPenalty) {
                ParameterExclusionChip(
                    label = "Freq. Penalty",
                    selected = config.frequencyPenaltyDisabled,
                    onSelectedChange = { onConfigChange(config.copy(frequencyPenaltyDisabled = it)) },
                )
            }
            if (parameterSupport.supportsPresencePenalty) {
                ParameterExclusionChip(
                    label = "Pres. Penalty",
                    selected = config.presencePenaltyDisabled,
                    onSelectedChange = { onConfigChange(config.copy(presencePenaltyDisabled = it)) },
                )
            }
        }
    }
}

@Composable
private fun ParameterExclusionChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    NeoFilterChip(
        label = label,
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        showClearIconWhenSelected = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningConfiguration(
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    val reasoningPolicy = parameterSupport.reasoningPolicy
    var reasoningDropdownExpanded by remember(config.reasoningEffort, reasoningPolicy) { mutableStateOf(false) }

    if (parameterSupport.supportsTopK) {
        NeoOutlinedTextField(
            value = config.topK,
            onValueChange = { onConfigChange(config.copy(topK = it)) },
            enabled = !config.topKDisabled,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Top K")
                    Spacer(modifier = Modifier.width(4.dp))
                    PersistentTooltip(description = "Limits the next token choice to the K most likely tokens.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
    }

    if (parameterSupport.supportsReasoningEffort) {
        ExposedDropdownMenuBox(
            expanded = reasoningDropdownExpanded,
            onExpandedChange = { reasoningDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeoOutlinedTextField(
                value = reasoningSelectionLabel(config.reasoningEffort, reasoningPolicy),
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) "Agent Count" else "Reasoning")
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningDropdownExpanded) },
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                placeholder = {
                    Text(
                        if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                            "Select agent count"
                        } else {
                            "Select reasoning level"
                        },
                    )
                },
            )
            ExposedDropdownMenu(
                expanded = reasoningDropdownExpanded,
                onDismissRequest = { reasoningDropdownExpanded = false },
            ) {
                reasoningOptions(reasoningPolicy).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onConfigChange(config.copy(reasoningEffort = option.effort))
                            reasoningDropdownExpanded = false
                        },
                    )
                }
            }
        }

        Text(
            text =
                if (reasoningPolicy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                    "xAI multi-agent maps 4 agents to low reasoning and 16 agents to high reasoning."
                } else {
                    "Reasoning support varies by provider and model."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (parameterSupport.supportsMinP) {
        TuningSlider(
            label = "Min P",
            description =
                "Limits the next token choice to tokens with probability " +
                    "at least P times the probability of the most likely token.",
            value = config.minP.toFloat(),
            range = 0f..1f,
            onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) },
            enabled = !config.minPDisabled && parameterSupport.supportsMinP,
        )
    }

    if (parameterSupport.supportsTemperature) {
        TuningSlider(
            label = "Temperature",
            description = "Controls randomness. Lower values are more deterministic; higher values are more creative.",
            value = config.temperature.toFloat(),
            range = 0f..2f,
            onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) },
            enabled = !config.temperatureDisabled && parameterSupport.supportsTemperature,
        )
    }

    if (parameterSupport.supportsTopP) {
        TuningSlider(
            label = "Top P",
            description = "Limits the next token choice to a subset of tokens whose cumulative probability exceeds P.",
            value = config.topP.toFloat(),
            range = 0f..1f,
            onValueChange = { onConfigChange(config.copy(topP = it.toDouble())) },
            enabled = !config.topPDisabled && parameterSupport.supportsTopP,
        )
    }

    if (parameterSupport.supportsFrequencyPenalty) {
        TuningSlider(
            label = "Frequency Penalty",
            description = "Penalizes tokens based on their frequency in the generated text so far.",
            value = config.frequencyPenalty.toFloat(),
            range = -2f..2f,
            onValueChange = { onConfigChange(config.copy(frequencyPenalty = it.toDouble())) },
            enabled = !config.frequencyPenaltyDisabled && parameterSupport.supportsFrequencyPenalty,
        )
    }

    if (parameterSupport.supportsPresencePenalty) {
        TuningSlider(
            label = "Presence Penalty",
            description = "Penalizes tokens based on whether they have appeared at least once in the generated text.",
            value = config.presencePenalty.toFloat(),
            range = -2f..2f,
            onValueChange = { onConfigChange(config.copy(presencePenalty = it.toDouble())) },
            enabled = !config.presencePenaltyDisabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun PresetConfigurationForm(
    provider: ApiProvider,
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
    availableModels: List<DiscoveredApiModelUi>,
    filteredModels: List<DiscoveredApiModelUi>,
    isFetchingModels: Boolean,
    canFetchModels: Boolean,
    searchQuery: String,
    providerFilters: Set<String>,
    sortOption: ModelSortOption,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    onFetchModels: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleProviderFilter: (String) -> Unit,
    onClearProviderFilters: () -> Unit,
    onUpdateSortOption: (ModelSortOption) -> Unit,
) {
    val hasReadonlyContextWindow = selectedModelMetadata?.contextWindowTokens != null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NeoOutlinedTextField(
            value = config.displayName,
            onValueChange = { onConfigChange(config.copy(displayName = it)) },
            label = { Text("Preset Name (e.g. Creative)") },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        PresetModelAndVisionSection(
            config = config,
            selectedModelMetadata = selectedModelMetadata,
            availableModels = availableModels,
            filteredModels = filteredModels,
            isFetchingModels = isFetchingModels,
            canFetchModels = canFetchModels,
            searchQuery = searchQuery,
            providerFilters = providerFilters,
            sortOption = sortOption,
            onConfigChange = onConfigChange,
            onFetchModels = onFetchModels,
            onUpdateSearchQuery = onUpdateSearchQuery,
            onToggleProviderFilter = onToggleProviderFilter,
            onClearProviderFilters = onClearProviderFilters,
            onUpdateSortOption = onUpdateSortOption,
        )

        SystemPromptConfiguration(
            config = config,
            onConfigChange = onConfigChange,
        )

        if (provider == ApiProvider.OPENROUTER) {
            OpenRouterRoutingCard(
                routing = config.openRouterRouting,
                onRoutingChange = { onConfigChange(config.copy(openRouterRouting = it)) },
            )
        }

        ConfigurationHeader("Telemetry pricing")

        ApiModelPricingConfiguration(
            provider = provider,
            config = config,
            selectedModelMetadata = selectedModelMetadata,
            onConfigChange = onConfigChange,
        )

        ConfigurationHeader("Context")

        ContextConfiguration(
            config = config,
            hasReadonlyContextWindow = hasReadonlyContextWindow,
            supportsMaxTokens = parameterSupport.supportsMaxTokens,
            onConfigChange = onConfigChange,
        )

        ConfigurationHeader("Tuning")

        TuningConfiguration(
            parameterSupport = parameterSupport,
            config = config,
            onConfigChange = onConfigChange,
        )

        ParameterExclusionSection(
            parameterSupport = parameterSupport,
            config = config,
            onConfigChange = onConfigChange,
        )

        ConfigurationHeader("Headers")

        CustomHeadersList(
            customHeaders = config.customHeaders,
            onNavigateToCustomHeaders = onNavigateToCustomHeaders,
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Credentials")
@Composable
fun PreviewByokConfigureCredentials() {
    EnclaveTheme {
        ByokConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    apiProviderEditor =
                        ApiProviderEditorUiState(
                            assetDraft = MockSettingsData.apiModels[0],
                            presetDraft = null,
                        ),
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
            onTestApiProviderConnection = {},
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
    EnclaveTheme {
        ByokConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    apiProviderEditor =
                        ApiProviderEditorUiState(
                            assetDraft = MockSettingsData.apiModels[0],
                            presetDraft = MockSettingsData.apiModels[0].configurations[0],
                        ),
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
            onTestApiProviderConnection = {},
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
    EnclaveTheme {
        val headers =
            listOf(
                CustomHeaderUi("X-Custom-Header-1", "Value 1"),
                CustomHeaderUi("X-Custom-Header-2", "Value 2"),
                CustomHeaderUi("X-Custom-Header-3", "Value 3"),
                CustomHeaderUi("X-Custom-Header-4", "Value 4"),
            )
        val mockConfig =
            MockSettingsData.apiModels[0].configurations[0].copy(
                customHeaders = headers,
            )

        ByokConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    apiProviderEditor =
                        ApiProviderEditorUiState(
                            assetDraft = MockSettingsData.apiModels[0],
                            presetDraft = mockConfig,
                        ),
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
            onTestApiProviderConnection = {},
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
    EnclaveTheme {
        ByokConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    apiProviderEditor =
                        ApiProviderEditorUiState(
                            assetDraft = MockSettingsData.apiModels[2],
                            presetDraft = MockSettingsData.apiModels[2].configurations[0],
                        ),
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
            onTestApiProviderConnection = {},
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
