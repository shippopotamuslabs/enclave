package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.launch

private sealed interface LocalModelsSheetView {
    data object AssetList : LocalModelsSheetView
    data class ConfigList(val asset: LocalModelAssetUi) : LocalModelsSheetView
    data class Reassignment(
        val modelTypes: List<ModelType>, 
        val options: List<ReassignmentOptionUi>
    ) : LocalModelsSheetView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    onSelectLocalModelAsset: (LocalModelAssetUi?) -> Unit,
    onSelectLocalModelConfig: (LocalModelConfigUi?) -> Unit,
    onDeleteLocalModelAsset: (Long) -> Unit,
    onDeleteLocalModelConfig: (Long) -> Unit,
    onConfirmDeletionWithReassignment: (Long?, Long?) -> Unit,
    onDismissDeletionSafety: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<LocalDeleteTarget?>(null) }
    val scope = rememberCoroutineScope()

    val hideAndNavigate: (() -> Unit) -> Unit = { onNavigate ->
        scope.launch {
            sheetState.hide()
            onNavigate()
            onDismiss()
        }
    }

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            val viewState = remember(uiState) {
                when {
                    uiState.modelTypesNeedingReassignment.isNotEmpty() -> 
                        LocalModelsSheetView.Reassignment(
                            modelTypes = uiState.modelTypesNeedingReassignment,
                            options = uiState.reassignmentOptions
                        )
                    uiState.selectedLocalModelAsset != null -> 
                        LocalModelsSheetView.ConfigList(uiState.selectedLocalModelAsset)
                    else -> 
                        LocalModelsSheetView.AssetList
                }
            }

            AnimatedContent(targetState = viewState, label = "LocalModelsSheetTransition") { state ->
                when (state) {
                    is LocalModelsSheetView.ConfigList -> {
                        LocalModelConfigListView(
                            asset = state.asset,
                            onBack = { onSelectLocalModelAsset(null) },
                            onEditConfig = { config ->
                                onSelectLocalModelConfig(config)
                                hideAndNavigate(onNavigateToLocalModelConfigure)
                            },
                            onRequestDeleteConfig = { config ->
                                pendingDeleteTarget = LocalDeleteTarget.Config(config.id, config.displayName)
                            },
                            onAddConfig = {
                                onSelectLocalModelConfig(LocalModelConfigUi(localModelId = state.asset.metadataId))
                                hideAndNavigate(onNavigateToLocalModelConfigure)
                            }
                        )
                    }
                    is LocalModelsSheetView.AssetList -> {
                        LocalModelAssetListView(
                            localModels = uiState.localModels,
                            availableToDownloadModels = uiState.availableToDownloadModels,
                            onSelectAsset = { asset -> onSelectLocalModelAsset(asset) },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = LocalDeleteTarget.Asset(asset.metadataId, asset.huggingFaceModelName)
                            },
                            onDownloadNewModel = {
                                // TODO: Download new model
                            }
                        )
                    }
                    is LocalModelsSheetView.Reassignment -> {
                        ReassignmentView(
                            modelTypes = state.modelTypes,
                            reassignmentOptions = state.options,
                            onConfirm = onConfirmDeletionWithReassignment,
                            onDismiss = onDismissDeletionSafety
                        )
                    }
                }
            }
        }

        if (uiState.showCannotDeleteLastModelAlert) {
            AlertDialog(
                onDismissRequest = onDismissDeletionSafety,
                title = { Text("Cannot Delete Model") },
                text = { Text("At least one local or API model must remain. You cannot delete the last available model.") },
                confirmButton = {
                    TextButton(onClick = onDismissDeletionSafety) {
                        Text("OK")
                    }
                }
            )
        }

        pendingDeleteTarget?.let { deleteTarget ->
            DeleteConfirmationDialog(
                title = if (deleteTarget is LocalDeleteTarget.Asset) {
                    "Delete Model?"
                } else {
                    "Delete Preset?"
                },
                message = if (deleteTarget is LocalDeleteTarget.Asset) {
                    "Delete ${deleteTarget.displayName}? This removes the model and all of its presets."
                } else {
                    "Delete ${deleteTarget.displayName}? This preset will be removed."
                },
                confirmLabel = "Delete",
                onConfirm = {
                    when (deleteTarget) {
                        is LocalDeleteTarget.Asset -> onDeleteLocalModelAsset(deleteTarget.id)
                        is LocalDeleteTarget.Config -> onDeleteLocalModelConfig(deleteTarget.id)
                    }
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null }
            )
        }
    }
}

@Composable
private fun ReassignmentView(
    modelTypes: List<ModelType>,
    reassignmentOptions: List<ReassignmentOptionUi>,
    onConfirm: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<ReassignmentOptionUi?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Reassignment Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val typesList = modelTypes.joinToString { it.displayLabel }
        Text(
            text = if (reassignmentOptions.isEmpty()) {
                "The following slot(s) are using this model as default: $typesList. " +
                    "No compatible models exist."
            } else {
                "The following slot(s) are using this model as default: $typesList. " +
                    "Please select a replacement config first."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (reassignmentOptions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(reassignmentOptions) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = option }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    selectedOption?.let { opt ->
                        onConfirm(
                            if (opt.localModelId != null) opt.configId else null,
                            if (opt.apiCredentialsId != null) opt.configId else null
                        )
                    }
                },
                enabled = selectedOption != null
            ) {
                Text("Delete & Reassign")
            }
        }
    }
}

@Composable
private fun LocalModelAssetListView(
    localModels: List<LocalModelAssetUi>,
    availableToDownloadModels: List<LocalModelAssetUi>,
    onSelectAsset: (LocalModelAssetUi) -> Unit,
    onRequestDeleteAsset: (LocalModelAssetUi) -> Unit,
    onDownloadNewModel: () -> Unit
) {
    Column {
        Text(
            text = "Local AI Models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Manage your downloaded models and their tuning presets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (localModels.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(localModels, key = { it.metadataId }) { asset ->
                    LocalModelAssetCard(
                        asset = asset,
                        onSelectAsset = onSelectAsset,
                        onRequestDeleteAsset = onRequestDeleteAsset
                    )
                }
            }

            if (availableToDownloadModels.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AVAILABLE FOR DOWNLOAD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(availableToDownloadModels, key = { "dl_${it.metadataId}" }) { asset ->
                    LocalModelAssetCard(
                        asset = asset,
                        onSelectAsset = { /* Re-download logic */ },
                        onRequestDeleteAsset = null
                    )
                }
            }

            if (localModels.isEmpty() && availableToDownloadModels.isEmpty()) {
                item {
                    Text(
                        text = "No models downloaded yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalModelAssetCard(
    asset: LocalModelAssetUi,
    onSelectAsset: (LocalModelAssetUi) -> Unit,
    onRequestDeleteAsset: ((LocalModelAssetUi) -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onSelectAsset(asset) },
                    onLongClick = {
                        if (onRequestDeleteAsset != null) {
                            menuExpanded = true
                        }
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.huggingFaceModelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(asset.sizeInBytes / (1024 * 1024 * 1024.0)).format(1)} GB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (onRequestDeleteAsset != null) {
                        Text(
                            text = "${asset.configurations.size} Presets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (onRequestDeleteAsset == null) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Model ${asset.huggingFaceModelName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (onRequestDeleteAsset != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuExpanded = false
                        onRequestDeleteAsset(asset)
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalModelConfigListView(
    asset: LocalModelAssetUi,
    onBack: () -> Unit,
    onEditConfig: (LocalModelConfigUi) -> Unit,
    onRequestDeleteConfig: (LocalModelConfigUi) -> Unit,
    onAddConfig: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to local models")
            }
            Column {
                Text(
                    text = asset.huggingFaceModelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(asset.configurations, key = { it.id }) { config ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditConfig(config) },
                                onLongClick = { menuExpanded = true }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Temp: ${config.temperature} | Max: ${config.maxTokens}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onEditConfig(config) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Preset ${config.displayName}",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteConfig(config)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onAddConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Preset",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private sealed interface LocalDeleteTarget {
    val id: Long
    val displayName: String

    data class Asset(
        override val id: Long,
        override val displayName: String
    ) : LocalDeleteTarget

    data class Config(
        override val id: Long,
        override val displayName: String
    ) : LocalDeleteTarget
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Local Models Sheet - Assets List")
@Composable
fun PreviewLocalModelsBottomSheetAssets() {
    PocketCrewTheme {
        LocalModelsBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToLocalModelConfigure = {},
            onSelectLocalModelAsset = {},
            onSelectLocalModelConfig = {},
            onDeleteLocalModelAsset = {},
            onDeleteLocalModelConfig = {},
            onConfirmDeletionWithReassignment = { _, _ -> },
            onDismissDeletionSafety = {}
        )
    }
}

@Preview(showBackground = true, name = "Local Models Sheet - Config List")
@Composable
fun PreviewLocalModelsBottomSheetContext() {
    PocketCrewTheme {
        LocalModelsBottomSheet(
            uiState = MockSettingsData.baseUiState.copy(
                selectedLocalModelAsset = MockSettingsData.localModels[0]
            ),
            onDismiss = {},
            onNavigateToLocalModelConfigure = {},
            onSelectLocalModelAsset = {},
            onSelectLocalModelConfig = {},
            onDeleteLocalModelAsset = {},
            onDeleteLocalModelConfig = {},
            onConfirmDeletionWithReassignment = { _, _ -> },
            onDismissDeletionSafety = {}
        )
    }
}
