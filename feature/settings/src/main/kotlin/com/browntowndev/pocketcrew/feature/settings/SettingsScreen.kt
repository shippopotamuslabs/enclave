package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onHapticPressChange: (Boolean) -> Unit,
    onHapticResponseChange: (Boolean) -> Unit,
    onShowCustomizationSheet: (Boolean) -> Unit,
    onCustomizationEnabledChange: (Boolean) -> Unit,
    onPromptOptionChange: (SystemPromptOption) -> Unit,
    onCustomPromptTextChange: (String) -> Unit,
    onSaveCustomization: () -> Unit,
    onShowDataControlsSheet: (Boolean) -> Unit,
    onAllowMemoriesChange: (Boolean) -> Unit,
    onDeleteAllConversations: () -> Unit,
    onDeleteAllMemories: () -> Unit,
    onShowMemoriesSheet: (Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenToS: () -> Unit,
    onShowFeedbackSheet: (Boolean) -> Unit,
    onFeedbackTextChange: (String) -> Unit,
    onSubmitFeedback: () -> Unit,
    onNavigateToModelDownload: () -> Unit,
    // Model Configuration
    onShowModelConfigSheet: (Boolean) -> Unit,
    onSelectModelType: (com.browntowndev.pocketcrew.domain.model.inference.ModelType) -> Unit,
    onBackToModelList: () -> Unit,
    onHuggingFaceModelNameChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Double) -> Unit,
    onSaveModelConfig: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onCloseClick) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader("Theme") }
            item {
                ThemeSelectionRow(
                    selectedTheme = uiState.theme,
                    onThemeChange = onThemeChange
                )
            }

            item { SectionHeader("Haptic Feedback") }
            item {
                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "When pressing buttons",
                    checked = uiState.hapticPress,
                    onCheckedChange = onHapticPressChange
                )
            }
            item {
                SettingsToggleRow(
                    icon = Icons.Default.Vibration,
                    title = "When Pocket Crew is responding",
                    checked = uiState.hapticResponse,
                    onCheckedChange = onHapticResponseChange
                )
            }

            item { SectionHeader("Customization") }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Tune,
                    title = "Customize Pocket Crew",
                    onClick = { onShowCustomizationSheet(true) }
                )
            }

            item { SectionHeader("Data") }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Storage,
                    title = "Data Controls",
                    onClick = { onShowDataControlsSheet(true) }
                )
            }

            item { SectionHeader("Memories") }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Memory,
                    title = "Memories",
                    onClick = { onShowMemoriesSheet(true) }
                )
            }

            item { SectionHeader("Configuration") }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Settings,
                    title = "Model Configuration",
                    onClick = { onShowModelConfigSheet(true) }
                )
            }

            item { SectionHeader("Information") }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Memory,
                    title = "AI Models Status",
                    onClick = onNavigateToModelDownload
                )
            }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Description,
                    title = "Terms of Service",
                    onClick = onOpenToS
                )
            }
            item {
                SettingsClickableRow(
                    icon = Icons.Default.Feedback,
                    title = "Give Feedback",
                    onClick = { onShowFeedbackSheet(true) }
                )
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        // Bottom Sheets
        if (uiState.showCustomizationSheet) {
            CustomizationBottomSheet(
                uiState = uiState,
                onDismiss = { onShowCustomizationSheet(false) },
                onEnabledChange = onCustomizationEnabledChange,
                onOptionChange = onPromptOptionChange,
                onPromptChange = onCustomPromptTextChange,
                onSave = onSaveCustomization
            )
        }

        if (uiState.showDataControlsSheet) {
            DataControlsBottomSheet(
                uiState = uiState,
                onDismiss = { onShowDataControlsSheet(false) },
                onAllowMemoriesChange = onAllowMemoriesChange,
                onDeleteConversations = onDeleteAllConversations,
                onDeleteMemories = onDeleteAllMemories
            )
        }

        if (uiState.showMemoriesSheet) {
            MemoriesBottomSheet(
                memories = uiState.memories,
                onDismiss = { onShowMemoriesSheet(false) },
                onDeleteMemory = onDeleteMemory
            )
        }

        if (uiState.showFeedbackSheet) {
            FeedbackBottomSheet(
                feedbackText = uiState.feedbackText,
                onDismiss = { onShowFeedbackSheet(false) },
                onFeedbackChange = onFeedbackTextChange,
                onSubmit = onSubmitFeedback
            )
        }

        if (uiState.showModelConfigSheet) {
            ModelConfigurationBottomSheet(
                uiState = uiState,
                onDismiss = { onShowModelConfigSheet(false) },
                onSelectModelType = onSelectModelType,
                onBackToModelList = onBackToModelList,
                onHuggingFaceModelNameChange = onHuggingFaceModelNameChange,
                onTemperatureChange = onTemperatureChange,
                onTopKChange = onTopKChange,
                onTopPChange = onTopPChange,
                onSave = onSaveModelConfig
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionRow(
    selectedTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val options = AppTheme.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, theme ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onThemeChange(theme) },
                selected = selectedTheme == theme,
                label = { 
                    Text(
                        theme.name.lowercase().replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                        }
                    ) 
                }
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: Any, // Can be ImageVector or Painter
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickableRow(
    icon: Any, // Can be ImageVector or Painter
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsIcon(icon: Any) {
    when (icon) {
        is ImageVector -> Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is androidx.compose.ui.graphics.painter.Painter -> Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOptionChange: (SystemPromptOption) -> Unit,
    onPromptChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customize Pocket Crew",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onSave) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Customization",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.customizationEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "System Prompt Options",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Grid of options
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptOptionCard(
                        option = SystemPromptOption.CUSTOM,
                        selected = uiState.selectedPromptOption == SystemPromptOption.CUSTOM,
                        onClick = { onOptionChange(SystemPromptOption.CUSTOM) },
                        modifier = Modifier.weight(1f)
                    )
                    PromptOptionCard(
                        option = SystemPromptOption.CONCISE,
                        selected = uiState.selectedPromptOption == SystemPromptOption.CONCISE,
                        onClick = { onOptionChange(SystemPromptOption.CONCISE) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptOptionCard(
                        option = SystemPromptOption.FORMAL,
                        selected = uiState.selectedPromptOption == SystemPromptOption.FORMAL,
                        onClick = { onOptionChange(SystemPromptOption.FORMAL) },
                        modifier = Modifier.weight(1f)
                    )
                    PromptOptionCard(
                        option = SystemPromptOption.RIGOROUS,
                        selected = uiState.selectedPromptOption == SystemPromptOption.RIGOROUS,
                        onClick = { onOptionChange(SystemPromptOption.RIGOROUS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isCustom = uiState.selectedPromptOption == SystemPromptOption.CUSTOM
            OutlinedTextField(
                value = if (isCustom) uiState.customPromptText else uiState.selectedPromptOption.stubPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = uiState.customizationEnabled && isCustom,
                readOnly = !isCustom,
                label = { Text("System Prompt") },
                placeholder = { if (isCustom) Text("Enter your custom system prompt...") }
            )
        }
    }
}

@Composable
fun PromptOptionCard(
    option: SystemPromptOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataControlsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onAllowMemoriesChange: (Boolean) -> Unit,
    onDeleteConversations: () -> Unit,
    onDeleteMemories: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Data Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Allow Pocket Crew to remember details about your conversations to personalize them.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = uiState.allowMemories, onCheckedChange = onAllowMemoriesChange)
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onDeleteConversations,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete All Conversations",
                    color = MaterialTheme.colorScheme.error
                )
            }

            TextButton(
                onClick = onDeleteMemories,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete All Memories",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesBottomSheet(
    memories: List<StoredMemory>,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Memories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (memories.isEmpty()) {
                Text(
                    text = "No memories stored yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memories) { memory ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = memory.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteMemory(memory.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete memory",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    feedbackText: String,
    onDismiss: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "How Can I Improve Pocket Crew?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = feedbackText,
                onValueChange = onFeedbackChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Describe your feedback") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier.align(Alignment.End),
                enabled = feedbackText.isNotBlank()
            ) {
                Text("Submit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onSelectModelType: (com.browntowndev.pocketcrew.domain.model.inference.ModelType) -> Unit,
    onBackToModelList: () -> Unit,
    onHuggingFaceModelNameChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Double) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSelectingModel = uiState.selectedModelType == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            if (isSelectingModel) {
                // Model list view
                Text(
                    text = "Model Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.modelConfigurations.isEmpty()) {
                    Text(
                        text = "No models configured. Download a model first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.modelConfigurations) { config ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onSelectModelType(config.modelType) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = config.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Model detail view
                val config = uiState.selectedModelConfig
                var huggingFaceDropdownExpanded by remember { mutableStateOf(false) }

                // Header with back button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBackToModelList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back to model list"
                        )
                    }
                    Text(
                        text = config?.displayName ?: "Model Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (config != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // HuggingFace Model Name Dropdown
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
                                shape = RoundedCornerShape(12.dp)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tunings Section
                        Text(
                            text = "Tunings",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Temperature
                        Text(
                            text = "Temperature: ${String.format("%.2f", config.temperature)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = config.temperature.toFloat(),
                            onValueChange = { onTemperatureChange(it.toDouble()) },
                            valueRange = 0f..2f,
                            steps = 19
                        )

                        // Top K
                        Text(
                            text = "Top K: ${config.topK}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = config.topK.toFloat(),
                            onValueChange = { onTopKChange(it.toInt()) },
                            valueRange = 1f..100f,
                            steps = 98
                        )

                        // Top P
                        Text(
                            text = "Top P: ${String.format("%.2f", config.topP)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = config.topP.toFloat(),
                            onValueChange = { onTopPChange(it.toDouble()) },
                            valueRange = 0f..1f,
                            steps = 19
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
fun PreviewSettingsScreenLight() {
    PocketCrewTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                memories = listOf(
                    StoredMemory("1", "Memory 1"),
                    StoredMemory("2", "Memory 2")
                )
            ),
            onCloseClick = {},
            onThemeChange = {},
            onHapticPressChange = {},
            onHapticResponseChange = {},
            onShowCustomizationSheet = {},
            onCustomizationEnabledChange = {},
            onPromptOptionChange = {},
            onCustomPromptTextChange = {},
            onSaveCustomization = {},
            onShowDataControlsSheet = {},
            onAllowMemoriesChange = {},
            onDeleteAllConversations = {},
            onDeleteAllMemories = {},
            onShowMemoriesSheet = {},
            onDeleteMemory = {},
            onOpenToS = {},
            onShowFeedbackSheet = {},
            onFeedbackTextChange = {},
            onSubmitFeedback = {},
            onNavigateToModelDownload = {},
            onShowModelConfigSheet = {},
            onSelectModelType = {},
            onBackToModelList = {},
            onHuggingFaceModelNameChange = {},
            onTemperatureChange = {},
            onTopKChange = {},
            onTopPChange = {},
            onSaveModelConfig = {}
        )
    }
}
