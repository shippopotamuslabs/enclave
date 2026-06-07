package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.PersistentTooltip
import com.browntowndev.enclave.core.ui.component.neo.NeoBackScreenHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoToggleRow
import com.browntowndev.enclave.core.ui.component.neo.neoOutlinedTextFieldColors
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

/** Model format string constant for GGUF models handled by llama.cpp. */
internal const val MODEL_FORMAT_GGUF = "GGUF"

/** Model format string constant for LiteRT models handled by the LiteRT engine. */
internal const val MODEL_FORMAT_LITERT = "LiteRT"

@Composable
internal fun LocalModelConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleBack: () -> Unit = {
        viewModel.onClearSelectedLocalModel()
        onNavigateBack()
    }

    LocalModelConfigureScreen(
        uiState = uiState,
        modelFormat = uiState.localModelEditor.selectedAsset?.format ?: MODEL_FORMAT_GGUF,
        onNavigateBack = handleBack,
        onConfigChange = viewModel::onLocalModelConfigFieldChange,
        onSave = {
            viewModel.onSaveLocalModelConfig(onSuccess = {
                viewModel.onClearSelectedLocalModel()
                onNavigateBack()
            })
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelConfigureScreen(
    uiState: SettingsUiState,
    modelFormat: String,
    onNavigateBack: () -> Unit,
    onConfigChange: (LocalModelConfigUi) -> Unit,
    onSave: () -> Unit,
) {
    val config = uiState.localModelEditor.configDraft ?: LocalModelConfigUi()
    // LiteRT SamplerConfig only supports temperature, topP, topK — not maxTokens, minP, or repetitionPenalty.
    val isGgufFormat = modelFormat == MODEL_FORMAT_GGUF
    var showSystemPromptSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSaveEnabled =
        !config.isSystemPreset &&
            config.displayName.isNotBlank() &&
            config.contextWindow.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoBackScreenHeader(
                title = "Configure Local Preset",
                onBackClick = onNavigateBack,
            )
        },
        bottomBar = {
            if (!config.isSystemPreset) {
                NeoPrimaryButton(
                    text = "Save Preset",
                    onClick = onSave,
                    enabled = isSaveEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                            .padding(bottom = 20.dp)
                            .testTag("SaveButton"),
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            NeoOutlinedTextField(
                value = config.displayName,
                onValueChange = { onConfigChange(config.copy(displayName = it)) },
                label = { Text("Preset Name (e.g. Creative)") },
                modifier = Modifier.fillMaxWidth().testTag("DisplayNameField"),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                readOnly = config.isSystemPreset,
                enabled = !config.isSystemPreset,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (!config.isSystemPreset) {
                                Modifier.clickable { showSystemPromptSheet = true }
                            } else {
                                Modifier
                            },
                        ),
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

            ConfigurationHeader("Context")

            // Max Tokens is only supported by llama.cpp (GGUF). LiteRT's SamplerConfig
            // has no output token limit — the engine generates until natural stop.
            if (isGgufFormat) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeoOutlinedTextField(
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
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                            ),
                        readOnly = config.isSystemPreset,
                        enabled = !config.isSystemPreset,
                    )

                    NeoOutlinedTextField(
                        value = config.contextWindow,
                        onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Context Window")
                                Spacer(modifier = Modifier.width(4.dp))
                                PersistentTooltip(description = "Total tokens (input + output) the model can process at once.")
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("ContextWindowField"),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                            ),
                        readOnly = config.isSystemPreset,
                        enabled = !config.isSystemPreset,
                    )
                }
            } else {
                NeoOutlinedTextField(
                    value = config.contextWindow,
                    onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Context Window")
                            Spacer(modifier = Modifier.width(4.dp))
                            PersistentTooltip(description = "Total tokens (input + output) the model can process at once.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                    readOnly = config.isSystemPreset,
                    enabled = !config.isSystemPreset,
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
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                readOnly = config.isSystemPreset,
                enabled = !config.isSystemPreset,
            )

            TuningSlider(
                label = "Temperature",
                description = "Controls randomness. Lower values are more deterministic; higher values are more creative.",
                value = config.temperature.toFloat(),
                range = 0f..2f,
                onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) },
                enabled = !config.isSystemPreset,
            )

            TuningSlider(
                label = "Top P",
                description = "Limits the next token choice to a subset of tokens whose cumulative probability exceeds P.",
                value = config.topP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(topP = it.toDouble())) },
                enabled = !config.isSystemPreset,
            )

            // Min P and Repetition Penalty are only supported by llama.cpp (GGUF).
            // LiteRT's SamplerConfig does not expose these sampling parameters.
            if (isGgufFormat) {
                TuningSlider(
                    label = "Min P",
                    description =
                        "Limits the next token choice to tokens with probability " +
                            "at least P times the probability of the most likely token.",
                    value = config.minP.toFloat(),
                    range = 0f..1f,
                    onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) },
                    enabled = !config.isSystemPreset,
                )

                TuningSlider(
                    label = "Repetition Penalty",
                    description = "Penalizes tokens that have already appeared, discouraging the model from repeating itself.",
                    value = config.repetitionPenalty.toFloat(),
                    range = 0f..2f,
                    onValueChange = { onConfigChange(config.copy(repetitionPenalty = it.toDouble())) },
                    enabled = !config.isSystemPreset,
                )
            }

            NeoToggleRow(
                title = "Thinking",
                checked = config.thinkingEnabled,
                onCheckedChange = { onConfigChange(config.copy(thinkingEnabled = it)) },
                subtitle = "Enable extended reasoning.",
                enabled = !config.isSystemPreset,
                switchTestTag = "ThinkingSwitch",
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "Local Model Configure Screen - Editable")
@Composable
fun PreviewLocalModelConfigureScreenEditable() {
    EnclaveTheme {
        LocalModelConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    localModelEditor =
                        LocalModelEditorUiState(
                            configDraft = MockSettingsData.localModels[0].configurations[0].copy(isSystemPreset = false),
                        ),
                ),
            modelFormat = MODEL_FORMAT_GGUF,
            onNavigateBack = {},
            onConfigChange = {},
            onSave = {},
        )
    }
}

@Preview(showBackground = true, name = "Local Model Configure Screen - System Preset")
@Composable
fun PreviewLocalModelConfigureScreenSystem() {
    EnclaveTheme {
        LocalModelConfigureScreen(
            uiState =
                MockSettingsData.baseUiState.copy(
                    localModelEditor =
                        LocalModelEditorUiState(
                            configDraft = MockSettingsData.localModels[0].configurations[0].copy(isSystemPreset = true),
                        ),
                ),
            modelFormat = MODEL_FORMAT_GGUF,
            onNavigateBack = {},
            onConfigChange = {},
            onSave = {},
        )
    }
}

@Preview(showBackground = true, name = "Assignment Selection Bottom Sheet (Local Model Screen)")
@Composable
fun PreviewLocalModelAssignmentSelectionBottomSheet() {
    EnclaveTheme {
        AssignmentSelectionContent(
            modifier = Modifier.padding(bottom = 32.dp),
            slotLabel = "Main",
            localAssets = MockSettingsData.localModels,
            apiAssets = MockSettingsData.apiModels,
            ttsAssets = emptyList(),
            liveVoiceAssets = emptyList(),
            mediaAssets = emptyList(),
            onDismiss = {},
            onSelect = { _, _, _, _, _ -> },
        )
    }
}
