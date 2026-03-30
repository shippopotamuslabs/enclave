package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val config = uiState.selectedLocalModelConfig ?: LocalModelConfigUi()

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
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = config.systemPrompt,
                onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            TuningSlider(
                label = "Temperature",
                value = config.temperature.toFloat(),
                range = 0f..2f,
                onValueChange = { onConfigChange(config.copy(temperature = it.toDouble())) }
            )

            TuningSlider(
                label = "Top P",
                value = config.topP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(topP = it.toDouble())) }
            )

            OutlinedTextField(
                value = config.topK,
                onValueChange = { onConfigChange(config.copy(topK = it)) },
                label = { Text("Top K") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            TuningSlider(
                label = "Min P",
                value = config.minP.toFloat(),
                range = 0f..1f,
                onValueChange = { onConfigChange(config.copy(minP = it.toDouble())) }
            )

            TuningSlider(
                label = "Repetition Penalty",
                value = config.repetitionPenalty.toFloat(),
                range = 0f..2f,
                onValueChange = { onConfigChange(config.copy(repetitionPenalty = it.toDouble())) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = config.maxTokens,
                    onValueChange = { onConfigChange(config.copy(maxTokens = it)) },
                    label = { Text("Max Tokens") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = config.contextWindow,
                    onValueChange = { onConfigChange(config.copy(contextWindow = it)) },
                    label = { Text("Context Window") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Preset", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, name = "Local Model Configure Screen")
@Composable
fun PreviewLocalModelConfigureScreen() {
    PocketCrewTheme {
        LocalModelConfigureScreen(
            uiState = MockSettingsData.baseUiState.copy(
                selectedLocalModelConfig = MockSettingsData.localModels[0].configurations[0]
            ),
            onNavigateBack = {},
            onConfigChange = {},
            onSave = {}
        )
    }
}
