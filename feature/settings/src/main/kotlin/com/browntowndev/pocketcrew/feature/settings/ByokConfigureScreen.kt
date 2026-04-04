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
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
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
        onSaveApiCredentials = {
            viewModel.onSaveApiCredentials(onSuccess = { assetUi, configUi ->
                viewModel.onSelectApiModelAsset(assetUi)
                if (configUi != null) {
                    viewModel.onSelectApiModelConfig(configUi)
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
                    onAssetChange = onApiModelAssetFieldChange,
                    onApiKeyChange = onApiKeyChange,
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
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
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
    onAssetChange: (ApiModelAssetUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

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
                            onAssetChange(asset.copy(provider = provider))
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

        OutlinedTextField(
            value = asset.modelId,
            onValueChange = { onAssetChange(asset.copy(modelId = it)) },
            label = { Text("Model ID (e.g. gpt-4o)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

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

        if (asset.provider == ApiProvider.SELF_HOSTED) {
            OutlinedTextField(
                value = asset.baseUrl ?: "",
                onValueChange = { onAssetChange(asset.copy(baseUrl = it)) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

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

        val isSaveEnabled = asset.displayName.isNotBlank() &&
                asset.modelId.isNotBlank() &&
                apiKey.isNotBlank() &&
                (asset.provider != ApiProvider.SELF_HOSTED || !asset.baseUrl.isNullOrBlank())

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

@Composable
fun PresetConfigurationForm(
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
    onNavigateToCustomHeaders: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = config.displayName,
            onValueChange = { onConfigChange(config.copy(displayName = it)) },
            label = { Text("Preset Name (e.g. Creative)") },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next)
        )

        OutlinedTextField(
            value = config.systemPrompt,
            onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 5
        )

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

        ConfigurationHeader("Tuning")

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

        TuningSlider(
            label = "Min P",
            description = "Limits the next token choice to tokens with probability at least P times the probability of the most likely token.",
            value = config.minP.toFloat(),
            range = 0f..1f,
            onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) }
        )

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

        TuningSlider(
            label = "Frequency Penalty",
            description = "Penalizes tokens based on their frequency in the generated text so far.",
            value = config.frequencyPenalty.toFloat(),
            range = -2f..2f,
            onValueChange = { onConfigChange(config.copy(frequencyPenalty = it.toDouble())) }
        )

        TuningSlider(
            label = "Presence Penalty",
            description = "Penalizes tokens based on whether they have appeared at least once in the generated text.",
            value = config.presencePenalty.toFloat(),
            range = -2f..2f,
            onValueChange = { onConfigChange(config.copy(presencePenalty = it.toDouble())) }
        )

        ConfigurationHeader("Headers")

        CustomHeadersList(
            customHeaders = config.customHeaders,
            onNavigateToCustomHeaders = onNavigateToCustomHeaders
        )

        val isSaveEnabled = config.displayName.isNotBlank() &&
                config.maxTokens.isNotBlank() &&
                config.contextWindow.isNotBlank() &&
                config.topK.isNotBlank()

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
