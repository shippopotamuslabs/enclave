package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningControlStyle
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

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
        onFetchApiModels = viewModel::onFetchApiModels,
        onSaveApiCredentials = {
            viewModel.onSaveApiCredentials(onSuccess = { assetUi, configUi ->
                viewModel.onSelectApiModelAsset(assetUi)
                if (configUi != null) {
                    viewModel.onSelectApiModelConfig(configUi)
                } else {
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
    onFetchApiModels: () -> Unit,
    onSaveApiCredentials: () -> Unit,
    onSaveApiModelConfig: () -> Unit
) {
    val isPresetMode = uiState.selectedApiModelConfig != null
    val title = if (isPresetMode) "Configure Preset" else "Provider Credentials"

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
                    provider = uiState.selectedApiModelAsset?.provider ?: ApiProvider.OPENAI,
                    parameterSupport = uiState.selectedApiModelParameterSupport,
                    config = selectedConfig,
                    onConfigChange = onApiModelConfigFieldChange,
                    onNavigateToCustomHeaders = onNavigateToCustomHeaders,
                    onSave = onSaveApiModelConfig
                )
            } else {
                CredentialsConfigurationForm(
                    asset = uiState.selectedApiModelAsset ?: ApiModelAssetUi(
                        credentialsId = 0, displayName = "", provider = ApiProvider.ANTHROPIC,
                        modelId = "", baseUrl = null, isVision = false, credentialAlias = "",
                        configurations = emptyList()
                    ),
                    apiKey = apiKey,
                    availableModels = uiState.discoveredApiModels,
                    isFetchingModels = uiState.isDiscoveringApiModels,
                    onAssetChange = onApiModelAssetFieldChange,
                    onApiKeyChange = onApiKeyChange,
                    onFetchModels = onFetchApiModels,
                    onSave = onSaveApiCredentials
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Credentials")
@Composable
fun PreviewByokConfigureCredentials() {
    PocketCrewTheme {
        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                selectedApiModelAsset = MockSettingsData.apiModels[0],
                selectedApiModelConfig = null
            ),
            apiKey = "sk-....",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onFetchApiModels = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onNavigateToCustomHeaders = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK Configure - Preset Tuning")
@Composable
fun PreviewByokConfigurePreset() {
    PocketCrewTheme {
        ByokConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                selectedApiModelAsset = MockSettingsData.apiModels[0],
                selectedApiModelConfig = MockSettingsData.apiModels[0].configurations[0]
            ),
            apiKey = "",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onFetchApiModels = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onNavigateToCustomHeaders = {}
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
                selectedApiModelAsset = MockSettingsData.apiModels[0],
                selectedApiModelConfig = mockConfig
            ),
            apiKey = "",
            onNavigateBack = {},
            onNavigateToCustomHeaders = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onFetchApiModels = {},
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
                selectedApiModelAsset = MockSettingsData.apiModels[2],
                selectedApiModelConfig = MockSettingsData.apiModels[2].configurations[0]
            ),
            apiKey = "sk-or-....",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onFetchApiModels = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {},
            onNavigateToCustomHeaders = {}
        )
    }
}

@Preview(showBackground = true, name = "BYOK - Custom Headers List Only")
@Composable
fun PreviewByokCustomHeadersList() {
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

        androidx.compose.material3.Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            CustomHeadersList(
                customHeaders = mockConfig.customHeaders,
                onNavigateToCustomHeaders = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsConfigurationForm(
    asset: ApiModelAssetUi,
    apiKey: String,
    availableModels: List<String>,
    isFetchingModels: Boolean,
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onFetchModels: () -> Unit,
    onSave: () -> Unit
) {
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val supportsModelDiscovery = ApiProviderModelPolicy.supportsModelDiscovery(asset.provider)

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

        if (supportsModelDiscovery) {
            Button(
                onClick = onFetchModels,
                enabled = apiKey.isNotBlank() || asset.credentialsId != 0L,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isFetchingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (availableModels.isEmpty()) "Load Models" else "Refresh Models")
            }

            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it && availableModels.isNotEmpty() },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = asset.modelId,
                    onValueChange = { onAssetChange(asset.copy(modelId = it)) },
                    readOnly = false,
                    enabled = true,
                    label = { Text("Model") },
                    placeholder = {
                        Text(
                            if (availableModels.isEmpty()) {
                                "Type a model ID or load models"
                            } else {
                                "Type a model ID or choose one below"
                            }
                        )
                    },
                    trailingIcon = {
                        if (availableModels.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                        }
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        if (availableModels.isEmpty()) {
                            Text("Model discovery is optional. You can always enter a model ID manually.")
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    availableModels.forEach { modelId ->
                        DropdownMenuItem(
                            text = { Text(modelId) },
                            onClick = {
                                onAssetChange(asset.copy(modelId = modelId))
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = asset.modelId,
                onValueChange = { onAssetChange(asset.copy(modelId = it)) },
                label = { Text("Model ID") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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

        /* Removed Vision Support toggle: Vision APIs are distinct from chat completion APIs.
           Persistence remains for potential future vision-specific API implementation.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Vision Support", fontWeight = FontWeight.Medium)
            Switch(
                checked = asset.isVision,
                onCheckedChange = { onAssetChange(asset.copy(isVision = it)) }
            )
        }
        */

        val isSaveEnabled = if (asset.credentialsId == 0L) {
            asset.displayName.isNotBlank() &&
            asset.modelId.isNotBlank() &&
            apiKey.isNotBlank()
        } else {
            asset.displayName.isNotBlank() &&
            asset.modelId.isNotBlank()
        }

        Button(
            onClick = onSave,
            enabled = isSaveEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save Credentials", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetConfigurationForm(
    provider: ApiProvider,
    parameterSupport: ApiModelParameterSupport,
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    onSave: () -> Unit
) {
    val reasoningPolicy = parameterSupport.reasoningPolicy
    var reasoningDropdownExpanded by remember(config.reasoningEffort, reasoningPolicy) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        OutlinedTextField(
            value = config.displayName,
            onValueChange = { onConfigChange(config.copy(displayName = it)) },
            label = { Text("Preset Name (e.g. Creative)") },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next)
        )

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("System Prompt")
                    Spacer(modifier = Modifier.width(4.dp))
                    PersistentTooltip(
                        description = "Certain pipeline slots (like Crew Mode or Vision) require specialized system prompts to function correctly. Use the Import button to load a template."
                    )
                }
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
            OutlinedTextField(
                value = config.systemPrompt,
                onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
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
                label = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context Window")
                        Spacer(modifier = Modifier.width(4.dp))
                        PersistentTooltip(description = "Total tokens (input + output) the model can process at once.")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
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

        val maxTokensValid = !parameterSupport.supportsMaxTokens || config.maxTokens.isNotBlank()
        val topKValid = !parameterSupport.supportsTopK || config.topK.isNotBlank()
        val isSaveEnabled = config.displayName.isNotBlank() &&
                maxTokensValid &&
                config.contextWindow.isNotBlank() &&
                topKValid

        Button(
            onClick = onSave,
            enabled = isSaveEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save Preset", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenRouterRoutingCard(
    routing: OpenRouterRoutingConfiguration,
    onRoutingChange: (OpenRouterRoutingConfiguration) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationHeader("OpenRouter Routing")
        OpenRouterRoutingSection(
            routing = routing,
            onRoutingChange = onRoutingChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenRouterRoutingSection(
    routing: OpenRouterRoutingConfiguration,
    onRoutingChange: (OpenRouterRoutingConfiguration) -> Unit
) {
    var sortExpanded by remember(routing.providerSort) { mutableStateOf(false) }
    var dataCollectionExpanded by remember(routing.dataCollectionPolicy) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = routing.providerSort.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider Sort") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                OpenRouterProviderSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.displayName) },
                        onClick = {
                            onRoutingChange(routing.copy(providerSort = sort))
                            sortExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = dataCollectionExpanded,
            onExpandedChange = { dataCollectionExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = routing.dataCollectionPolicy.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Data Collection Policy") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dataCollectionExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = dataCollectionExpanded,
                onDismissRequest = { dataCollectionExpanded = false }
            ) {
                OpenRouterDataCollectionPolicy.entries.forEach { policy ->
                    DropdownMenuItem(
                        text = { Text(policy.displayName) },
                        onClick = {
                            onRoutingChange(routing.copy(dataCollectionPolicy = policy))
                            dataCollectionExpanded = false
                        }
                    )
                }
            }
        }

        RoutingSwitchRow(
            label = "Allow Fallbacks",
            description = "OpenRouter may fall back to alternate providers when the primary one is unavailable.",
            checked = routing.allowFallbacks,
            onCheckedChange = { onRoutingChange(routing.copy(allowFallbacks = it)) }
        )

        RoutingSwitchRow(
            label = "Require Parameters",
            description = "Only route to providers that support every parameter in the request.",
            checked = routing.requireParameters,
            onCheckedChange = { onRoutingChange(routing.copy(requireParameters = it)) }
        )

        RoutingSwitchRow(
            label = "Zero Data Retention",
            description = "Prefer providers that support zero data retention for this request.",
            checked = routing.zeroDataRetention,
            onCheckedChange = { onRoutingChange(routing.copy(zeroDataRetention = it)) }
        )
    }
}

@Composable
private fun RoutingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            PersistentTooltip(description = description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class ReasoningOptionUi(
    val label: String,
    val effort: ApiReasoningEffort
)

private fun reasoningOptions(policy: com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningPolicy): List<ReasoningOptionUi> =
    policy.supportedEfforts.map { effort ->
        ReasoningOptionUi(
            label = if (policy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
                if (effort == ApiReasoningEffort.HIGH || effort == ApiReasoningEffort.XHIGH) {
                    "16 agents"
                } else {
                    "4 agents"
                }
            } else {
                effort.displayName
            },
            effort = effort
        )
    }

private fun reasoningSelectionLabel(
    effort: ApiReasoningEffort?,
    policy: com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningPolicy
): String {
    if (effort == null) {
        return ""
    }

    return if (policy.controlStyle == ApiReasoningControlStyle.XAI_MULTI_AGENT) {
        if (effort == ApiReasoningEffort.HIGH || effort == ApiReasoningEffort.XHIGH) {
            "16 agents"
        } else {
            "4 agents"
        }
    } else {
        effort.displayName
    }
}

@Composable
fun CustomHeadersList(
    customHeaders: List<CustomHeaderUi>,
    onNavigateToCustomHeaders: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (customHeaders.isNotEmpty()) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    customHeaders.take(3).forEach { header ->
                        Text(
                            text = "${header.key}: ${header.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customHeaders.size > 3) {
                        Text(
                            text = "... and ${customHeaders.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCustomHeaders)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigurationHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun TuningSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                PersistentTooltip(description = description)
            }
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}
