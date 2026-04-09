package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.browntowndev.pocketcrew.domain.model.inference.SystemPromptTemplates
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@Composable
fun LocalModelConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleBack: () -> Unit = {
        viewModel.onClearSelectedLocalModel()
        onNavigateBack()
    }

    LocalModelConfigureScreen(
        uiState = uiState,
        onNavigateBack = handleBack,
        onConfigChange = viewModel::onLocalModelConfigFieldChange,
        onSave = {
            viewModel.onSaveLocalModelConfig(onSuccess = {
                viewModel.onClearSelectedLocalModel()
                onNavigateBack()
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelConfigureScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onConfigChange: (LocalModelConfigUi) -> Unit,
    onSave: () -> Unit
) {
    val config = uiState.localModelEditor.configDraft ?: LocalModelConfigUi()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Local Preset", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = config.displayName,
                onValueChange = { onConfigChange(config.copy(displayName = it)) },
                label = { Text("Preset Name (e.g. Creative)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                readOnly = config.isSystemPreset,
                enabled = !config.isSystemPreset
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Row {
                        TextButton(
                            onClick = { expanded = true },
                            enabled = !config.isSystemPreset
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
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("System Prompt")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(
                                description = "Certain pipeline slots (like Crew Mode or Vision) require specialized system prompts to function correctly. Use the Import button to load a template."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4,
                    maxLines = 4,
                    readOnly = config.isSystemPreset,
                    enabled = !config.isSystemPreset
                )
            }

            ConfigurationHeader("Context")

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = config.maxTokens,
                    onValueChange = { onConfigChange(config.copy(maxTokens = it)) },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Max Tokens")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Maximum number of tokens the model can generate in a single response.")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    readOnly = config.isSystemPreset,
                    enabled = !config.isSystemPreset
                )

                OutlinedTextField(
                    value = config.contextWindow,
                    onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Context Window")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Total tokens (input + output) the model can process at once.")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    readOnly = config.isSystemPreset,
                    enabled = !config.isSystemPreset
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                readOnly = config.isSystemPreset,
                enabled = !config.isSystemPreset
            )

            TuningSlider(
                label = "Temperature",
                description = "Controls randomness. Lower values are more deterministic; higher values are more creative.",
                value = config.temperature.toFloat(),
                range = 0f..2f,
                onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) },
                enabled = !config.isSystemPreset
            )

            TuningSlider(
                label = "Top P",
                description = "Limits the next token choice to a subset of tokens whose cumulative probability exceeds P.",
                value = config.topP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(topP = it.toDouble())) },
                enabled = !config.isSystemPreset
            )

            TuningSlider(
                label = "Min P",
                description = "Limits the next token choice to tokens with probability at least P times the probability of the most likely token.",
                value = config.minP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) },
                enabled = !config.isSystemPreset
            )

            TuningSlider(
                label = "Repetition Penalty",
                description = "Penalizes tokens that have already appeared, discouraging the model from repeating itself.",
                value = config.repetitionPenalty.toFloat(),
                range = 0f..2f,
                onValueChange = { onConfigChange(config.copy(repetitionPenalty = it.toDouble())) },
                enabled = !config.isSystemPreset
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Enable extended reasoning with higher token budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.thinkingEnabled,
                    onCheckedChange = { onConfigChange(config.copy(thinkingEnabled = it)) },
                    enabled = !config.isSystemPreset
                )
            }

            if (!config.isSystemPreset) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Preset", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "Local Model Configure Screen - Editable")
@Composable
fun PreviewLocalModelConfigureScreenEditable() {
    PocketCrewTheme {
        LocalModelConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                localModelEditor = LocalModelEditorUiState(
                    configDraft = MockSettingsData.localModels[0].configurations[0].copy(isSystemPreset = false)
                )
            ),
            onNavigateBack = {},
            onConfigChange = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true, name = "Local Model Configure Screen - System Preset")
@Composable
fun PreviewLocalModelConfigureScreenSystem() {
    PocketCrewTheme {
        LocalModelConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                localModelEditor = LocalModelEditorUiState(
                    configDraft = MockSettingsData.localModels[0].configurations[0].copy(isSystemPreset = true)
                )
            ),
            onNavigateBack = {},
            onConfigChange = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true, name = "Assignment Selection Bottom Sheet (Local Model Screen)")
@Composable
fun PreviewLocalModelAssignmentSelectionBottomSheet() {
    PocketCrewTheme {
        AssignmentSelectionContent(
            modifier = Modifier.padding(bottom = 32.dp),
            slotLabel = "Main",
            localAssets = MockSettingsData.localModels,
            apiAssets = MockSettingsData.apiModels,
            onDismiss = {},
            onSelect = { _, _ -> }
        )
    }
}
