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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Composable
fun CompactionConfigureRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    CompactionConfigureScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onProviderTypeChange = viewModel::onCompactionProviderTypeChange,
        onApiModelIdChange = viewModel::onCompactionApiModelIdChange,
        onOpenModelSelection = { viewModel.onShowAssignmentDialog(true, ModelType.FAST) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactionConfigureScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onProviderTypeChange: (CompactionProviderType) -> Unit,
    onApiModelIdChange: (String?) -> Unit,
    onOpenModelSelection: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Compaction Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                Text(
                    text = "Context compaction helps keep your conversations within the model's memory limits by compressing older parts of the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SectionHeader(text = "Compaction Provider")
                Column {
                    CompactionProviderOption(
                        title = "Disabled",
                        subtitle = "Uses FIFO pruning when the context window is tight.",
                        selected = uiState.compaction.providerType == CompactionProviderType.DISABLED,
                        onClick = { onProviderTypeChange(CompactionProviderType.DISABLED) }
                    )
                    CompactionProviderOption(
                        title = "Configured API Model",
                        subtitle = "Uses a standard API model for compaction.",
                        selected = uiState.compaction.providerType == CompactionProviderType.API_MODEL,
                        onClick = { onProviderTypeChange(CompactionProviderType.API_MODEL) }
                    )
                    CompactionProviderOption(
                        title = "Tiny ONNX (Future)",
                        subtitle = "Planned on-device universal compaction.",
                        selected = uiState.compaction.providerType == CompactionProviderType.TINY_ONNX,
                        enabled = false,
                        onClick = { onProviderTypeChange(CompactionProviderType.TINY_ONNX) }
                    )
                }
            }

            if (uiState.compaction.providerType == CompactionProviderType.API_MODEL) {
                item {
                    SectionHeader(text = "Compaction Model")
                    SettingsNavigationItem(
                        title = "Selected Model",
                        subtitle = uiState.compaction.apiModelDisplayName ?: "Not Configured",
                        icon = Icons.Default.Info,
                        onClick = onOpenModelSelection
                    )
                }
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Note: Compaction providers may incur API costs depending on your selection.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (uiState.assignments.isDialogOpen) {
        AssignmentSelectionBottomSheet(
            slotLabel = "Compaction Model",
            localAssets = emptyList(),
            apiAssets = uiState.apiProvidersSheet.assets,
            onDismiss = { onApiModelIdChange(null) },
            onSelect = { _: LocalModelConfigurationId?, apiConfigId: ApiModelConfigurationId? ->
                onApiModelIdChange(apiConfigId?.value)
            }
        )
    }
}

@Composable
fun CompactionProviderOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

// Components are already available via SettingsScreen.kt in the same package (if imported correctly). 
// Since they are defined in SettingsScreen.kt, they are visible. I will remove the duplicate definitions here.
