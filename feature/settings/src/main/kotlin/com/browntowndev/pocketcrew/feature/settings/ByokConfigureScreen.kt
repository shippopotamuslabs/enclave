package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

@Composable
fun ByokConfigureRoute(
    onNavigateBack: () -> Unit,
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
        onApiModelAssetFieldChange = viewModel::onApiModelAssetFieldChange,
        onApiModelConfigFieldChange = viewModel::onApiModelConfigFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSaveApiCredentials = {
            viewModel.onSaveApiCredentials(onSuccess = {
                viewModel.onBackToByokList()
                onNavigateBack()
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
            if (isPresetMode) {
                PresetConfigurationForm(
                    config = uiState.selectedApiModelConfig!!,
                    onConfigChange = onApiModelConfigFieldChange,
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
                selectedApiModelAsset = MockSettingsData.apiModels[0],
                selectedApiModelConfig = MockSettingsData.apiModels[0].configurations[0]
            ),
            apiKey = "",
            onNavigateBack = {},
            onApiModelAssetFieldChange = {},
            onApiModelConfigFieldChange = {},
            onApiKeyChange = {},
            onSaveApiCredentials = {},
            onSaveApiModelConfig = {}
        )
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

        Button(
            onClick = onSave,
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
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = config.displayName,
            onValueChange = { onConfigChange(config.copy(displayName = it)) },
            label = { Text("Preset Name (e.g. Creative)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        TuningSlider(
            label = "Temperature",
            value = config.temperature.toFloat(),
            range = 0f..2f,
            onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) }
        )

        OutlinedTextField(
            value = config.maxTokens,
            onValueChange = { onConfigChange(config.copy(maxTokens = it)) },
            label = { Text("Max Tokens") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = config.contextWindow,
            onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
            label = { Text("Context Window") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = onSave,
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
fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
