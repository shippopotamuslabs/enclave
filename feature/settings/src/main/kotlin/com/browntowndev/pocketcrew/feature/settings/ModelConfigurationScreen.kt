package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Composable
fun ModelConfigurationRoute(
    modelType: ModelType,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelConfigurationScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSetDefaultModel = viewModel::onSetDefaultModel,
        onShowAssignmentDialog = viewModel::onShowAssignmentDialog
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onSetDefaultModel: (ModelType, Long?, Long?) -> Unit,
    onShowAssignmentDialog: (Boolean, ModelType?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pipeline Assignments", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(text = "Default Pipeline Slots")
                Spacer(modifier = Modifier.height(8.dp))
                DefaultAssignmentsCard(
                    assignments = uiState.defaultAssignments,
                    onEditAssignment = { onShowAssignmentDialog(true, it) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (uiState.showAssignmentDialog && uiState.editingAssignmentSlot != null) {
            AssignmentSelectionDialog(
                slot = uiState.editingAssignmentSlot,
                localAssets = uiState.localModels,
                apiAssets = uiState.apiModels,
                onDismiss = { onShowAssignmentDialog(false, null) },
                onSelect = { localId, apiId -> 
                    onSetDefaultModel(uiState.editingAssignmentSlot, localId, apiId)
                }
            )
        }
    }
}

@Composable
fun DefaultAssignmentsCard(
    assignments: List<DefaultModelAssignmentUi>,
    onEditAssignment: (ModelType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            assignments.forEach { assignment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = assignment.modelType.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = assignment.currentModelName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (assignment.providerName != null) {
                            Text(
                                text = "Provider: ${assignment.providerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onEditAssignment(assignment.modelType) }) {
                        Icon(
                            imageVector = Icons.Default.Edit, 
                            contentDescription = "Change default model for ${assignment.modelType.name}", 
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (assignment != assignments.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun AssignmentSelectionDialog(
    slot: ModelType,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (localId: Long?, apiId: Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign model to $slot") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (localAssets.isNotEmpty()) {
                    Text("Local Models", style = MaterialTheme.typography.titleSmall)
                    localAssets.forEach { asset ->
                        asset.configurations.forEach { config ->
                            SelectionRow(
                                title = "${asset.displayName} - ${config.displayName}",
                                onClick = { onSelect(config.id, null) }
                            )
                        }
                    }
                }

                if (apiAssets.isNotEmpty()) {
                    Text("API Models", style = MaterialTheme.typography.titleSmall)
                    apiAssets.forEach { asset ->
                        asset.configurations.forEach { config ->
                            SelectionRow(
                                title = "${asset.displayName} - ${config.displayName}",
                                onClick = { onSelect(null, config.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SelectionRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
    )
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Pipeline Assignments Screen")
@Composable
fun PreviewModelConfigurationScreen() {
    PocketCrewTheme {
        ModelConfigurationScreen(
            uiState = MockSettingsData.baseUiState,
            onNavigateBack = {},
            onSetDefaultModel = { _, _, _ -> },
            onShowAssignmentDialog = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Assignment Selection Dialog")
@Composable
fun PreviewAssignmentSelectionDialog() {
    PocketCrewTheme {
        AssignmentSelectionDialog(
            slot = ModelType.MAIN,
            localAssets = MockSettingsData.localModels,
            apiAssets = MockSettingsData.apiModels,
            onDismiss = {},
            onSelect = { _, _ -> }
        )
    }
}
