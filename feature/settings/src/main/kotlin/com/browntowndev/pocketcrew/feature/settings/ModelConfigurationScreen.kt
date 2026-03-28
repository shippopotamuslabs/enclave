package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.launch

@Composable
fun ModelConfigurationRoute(
    modelType: ModelType,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleBack: () -> Unit = {
        viewModel.onClearSelectedModel()
        onNavigateBack()
    }

    ModelConfigurationScreen(
        modelType = modelType,
        uiState = uiState,
        onNavigateBack = handleBack,
        onHuggingFaceModelNameChange = viewModel::onHuggingFaceModelNameChange,
        onTemperatureChange = viewModel::onTemperatureChange,
        onTopKChange = viewModel::onTopKChange,
        onTopPChange = viewModel::onTopPChange,
        onMaxTokensChange = viewModel::onMaxTokensChange,
        onContextWindowChange = viewModel::onContextWindowChange,
        onSave = {
            viewModel.onSaveModelConfig(onSuccess = {
                viewModel.onClearSelectedModel()
                onNavigateBack()
            })
        },
        onSetDefaultModel = viewModel::onSetDefaultModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationScreen(
    modelType: ModelType,
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onHuggingFaceModelNameChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Double) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onContextWindowChange: (Int) -> Unit,
    onSave: () -> Unit,
    onSetDefaultModel: (ModelType, ModelSource, Long?) -> Unit,
) {
    val config = uiState.selectedModelConfig
    val assignment = uiState.defaultAssignments.find { it.modelType == modelType }
    var huggingFaceDropdownExpanded by remember { mutableStateOf(false) }
    var apiDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = modelType.displayName(),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            if (assignment != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Processing Engine",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = assignment.source == ModelSource.ON_DEVICE,
                            onClick = { onSetDefaultModel(modelType, ModelSource.ON_DEVICE, null) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("On-Device")
                        }
                        SegmentedButton(
                            selected = assignment.source == ModelSource.API,
                            onClick = {
                                val apiId = uiState.apiModels.firstOrNull()?.id
                                onSetDefaultModel(modelType, ModelSource.API, apiId)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("API (BYOK)")
                        }
                    }

                    if (assignment.source == ModelSource.ON_DEVICE && config != null) {
                        Text(
                            text = "HuggingFace Model",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        ExposedDropdownMenuBox(
                            expanded = huggingFaceDropdownExpanded,
                            onExpandedChange = { huggingFaceDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = config.huggingFaceModelName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = huggingFaceDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = huggingFaceDropdownExpanded,
                                onDismissRequest = { huggingFaceDropdownExpanded = false }
                            ) {
                                uiState.availableHuggingFaceModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName) },
                                        onClick = {
                                            onHuggingFaceModelNameChange(modelName)
                                            huggingFaceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

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
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelWithInfo(
                                            label = "Temperature: ${String.format(Locale.ROOT, "%.2f", config.temperature)}",
                                            infoText = "Controls randomness: Higher values (e.g., 1.0) make output more creative, lower values (e.g., 0.2) make it more focused and deterministic."
                                        )
                                        Slider(
                                            value = config.temperature.toFloat(),
                                            onValueChange = { onTemperatureChange(it.toDouble()) },
                                            valueRange = 0f..2f
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelWithInfo(
                                            label = "Top K: ${config.topK}",
                                            infoText = "Limits the model to the top K most likely next tokens. Reduces the chance of low-probability 'garbage' tokens."
                                        )
                                        Slider(
                                            value = config.topK.toFloat(),
                                            onValueChange = { onTopKChange(it.toInt()) },
                                            valueRange = 1f..100f
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelWithInfo(
                                            label = "Top P: ${String.format(Locale.ROOT, "%.2f", config.topP)}",
                                            infoText = "Nucleus sampling: Limits the model to a subset of tokens whose cumulative probability is P. Another way to control diversity."
                                        )
                                        Slider(
                                            value = config.topP.toFloat(),
                                            onValueChange = { onTopPChange(it.toDouble()) },
                                            valueRange = 0f..1f
                                        )
                                    }

                                    OutlinedTextField(
                                        value = config.maxTokens.toString(),
                                        onValueChange = { newValue ->
                                            newValue.toIntOrNull()?.let { onMaxTokensChange(it) }
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

                                    OutlinedTextField(
                                        value = config.contextWindow.toString(),
                                        onValueChange = { newValue ->
                                            newValue.toIntOrNull()?.let { onContextWindowChange(it) }
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
                                }
                            }
                        }
                    } else if (assignment.source == ModelSource.API) {
                        Text(
                            text = "Custom API Model",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (uiState.apiModels.isEmpty()) {
                            Text(
                                text = "No custom models configured. Please add one in 'API & Models'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = apiDropdownExpanded,
                                onExpandedChange = { apiDropdownExpanded = it }
                            ) {
                                val currentApiModelName =
                                    assignment.currentModelName.takeIf { it.isNotBlank() }
                                        ?: "Select Model"
                                OutlinedTextField(
                                    value = currentApiModelName,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = apiDropdownExpanded
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = apiDropdownExpanded,
                                    onDismissRequest = { apiDropdownExpanded = false }
                                ) {
                                    uiState.apiModels.forEach { apiModel ->
                                        DropdownMenuItem(
                                            text = { Text(apiModel.displayName) },
                                            onClick = {
                                                onSetDefaultModel(
                                                    modelType,
                                                    ModelSource.API,
                                                    apiModel.id
                                                )
                                                apiDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No configuration found for ${modelType.displayName()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
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
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
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
