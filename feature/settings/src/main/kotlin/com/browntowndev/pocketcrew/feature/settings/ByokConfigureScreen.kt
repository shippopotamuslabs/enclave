package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.coroutines.launch
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
        onApiModelFieldChange = viewModel::onApiModelFieldChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSaveApiModel = {
            viewModel.onSaveApiModel(onSuccess = {
                viewModel.onBackToByokList()
                onNavigateBack()
            })
        },
        onDeleteApiModel = {
            val id = uiState.selectedApiModel?.id
            if (id != null && id != 0L) {
                viewModel.onDeleteApiModel(id, onSuccess = {
                    viewModel.onBackToByokList()
                    onNavigateBack()
                })
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokConfigureScreen(
    uiState: SettingsUiState,
    apiKey: String,
    onNavigateBack: () -> Unit,
    onApiModelFieldChange: (ApiModelConfigUi) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveApiModel: () -> Unit,
    onDeleteApiModel: () -> Unit,
) {
    val model = uiState.selectedApiModel ?: ApiModelConfigUi()
    val scrollState = rememberScrollState()
    var passwordVisible by remember { mutableStateOf(false) }
    var expandedProviderMenu by remember { mutableStateOf(false) }
    val isNew = model.id == 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isNew) "Add Custom Model" else "Configure API Model",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = onDeleteApiModel) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete model", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedProviderMenu,
                    onExpandedChange = { expandedProviderMenu = it }
                ) {
                    OutlinedTextField(
                        value = model.provider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProviderMenu) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProviderMenu,
                        onDismissRequest = { expandedProviderMenu = false }
                    ) {
                        ApiProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    onApiModelFieldChange(model.copy(provider = provider))
                                    expandedProviderMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = model.displayName,
                    onValueChange = { onApiModelFieldChange(model.copy(displayName = it)) },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Claude 3.5 Sonnet") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = model.modelId,
                    onValueChange = { onApiModelFieldChange(model.copy(modelId = it)) },
                    label = { Text("Model ID") },
                    placeholder = { Text("e.g. claude-3-5-sonnet-20241022") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = model.baseUrl,
                    onValueChange = { onApiModelFieldChange(model.copy(baseUrl = it)) },
                    label = { Text("API Base URL (Optional)") },
                    placeholder = { Text("Leave blank for default") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { onApiKeyChange(it) },
                    label = { Text("API Key" + if (!isNew) " (Leave blank to keep existing)" else "") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                        val desc = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = desc)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Advanced Section
                var advancedExpanded by remember { mutableStateOf(false) }
                val rotation by animateFloatAsState(if (advancedExpanded) 180f else 0f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Advanced Configurations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (advancedExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Max Tokens
                            OutlinedTextField(
                                value = model.maxTokens.toString(),
                                onValueChange = { newValue ->
                                    newValue.toIntOrNull()?.let { onApiModelFieldChange(model.copy(maxTokens = it)) }
                                },
                                label = { 
                                    LabelWithInfo(
                                        label = "Max Tokens",
                                        infoText = "The maximum number of tokens the model can generate in a single response.",
                                        showValue = false
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                            )

                            // Context Window
                            OutlinedTextField(
                                value = model.contextWindow.toString(),
                                onValueChange = { newValue ->
                                    newValue.toIntOrNull()?.let { onApiModelFieldChange(model.copy(contextWindow = it)) }
                                },
                                label = { 
                                    LabelWithInfo(
                                        label = "Context Window",
                                        infoText = "The total number of tokens (input + output) the model can process at once. Larger windows allow for longer conversations.",
                                        showValue = false
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                            )

                            // Temperature
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabelWithInfo(
                                    label = "Temperature: ${String.format(Locale.ROOT, "%.2f", model.temperature)}",
                                    infoText = "Controls randomness: Higher values (e.g., 1.0) make output more creative, lower values (e.g., 0.2) make it more focused and deterministic."
                                )
                                Slider(
                                    value = model.temperature.toFloat(),
                                    onValueChange = { onApiModelFieldChange(model.copy(temperature = it.toDouble())) },
                                    valueRange = 0f..2f
                                )
                            }

                            // Top P
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabelWithInfo(
                                    label = "Top P: ${String.format(Locale.ROOT, "%.2f", model.topP)}",
                                    infoText = "Nucleus sampling: Limits the model to a subset of tokens whose cumulative probability is P. Another way to control diversity."
                                )
                                Slider(
                                    value = model.topP.toFloat(),
                                    onValueChange = { onApiModelFieldChange(model.copy(topP = it.toDouble())) },
                                    valueRange = 0f..1f
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isReady = model.displayName.isNotBlank() && model.modelId.isNotBlank() && (apiKey.isNotBlank() || !isNew)
                Button(
                    onClick = onSaveApiModel,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = isReady
                ) {
                    Text(if (isNew) "Save Custom Model" else "Update Model", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelWithInfo(
    label: String, 
    infoText: String,
    showValue: Boolean = true
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (showValue) Modifier.widthIn(min = 100.dp) else Modifier
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above
            ),
            tooltip = {
                PlainTooltip {
                    Text(infoText)
                }
            },
            state = tooltipState
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        scope.launch { tooltipState.show() }
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
