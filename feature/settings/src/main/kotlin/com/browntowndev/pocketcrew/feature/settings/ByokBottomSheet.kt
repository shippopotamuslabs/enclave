package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.launch

private sealed interface ByokSheetView {
    data object AssetList : ByokSheetView
    data class ConfigList(val asset: ApiModelAssetUi) : ByokSheetView
    data class Reassignment(
        val modelTypes: List<ModelType>,
        val options: List<ReassignmentOptionUi>
    ) : ByokSheetView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onStartCreateApiModelAsset: () -> Unit,
    onSelectApiModelAsset: (ApiModelAssetUi?) -> Unit,
    onSelectApiModelConfig: (ApiModelConfigUi?) -> Unit,
    onDeleteApiModelAsset: (Long) -> Unit,
    onDeleteApiModelConfig: (ApiModelConfigurationId) -> Unit,
    onConfirmDeletionWithReassignment: (LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onDismissDeletionSafety: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteTarget by remember { mutableStateOf<ByokDeleteTarget?>(null) }
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
                    uiState.deletion.modelTypesNeedingReassignment.isNotEmpty() ->
                        ByokSheetView.Reassignment(
                            modelTypes = uiState.deletion.modelTypesNeedingReassignment,
                            options = uiState.deletion.reassignmentOptions
                        )
                    uiState.apiProvidersSheet.selectedAsset != null ->
                        ByokSheetView.ConfigList(uiState.apiProvidersSheet.selectedAsset)
                    else ->
                        ByokSheetView.AssetList
                }
            }

            AnimatedContent(targetState = viewState, label = "ByokSheetTransition") { state ->
                when (state) {
                    is ByokSheetView.ConfigList -> {
                        ByokConfigListView(
                            asset = state.asset,
                            onBack = { onSelectApiModelAsset(null) },
                            onEditAsset = {
                                hideAndNavigate {
                                    onSelectApiModelConfig(null)
                                    onNavigateToByokConfigure()
                                }
                            },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = ByokDeleteTarget.Asset(asset.credentialsId, asset.displayName)
                            },
                            onEditConfig = { config ->
                                hideAndNavigate {
                                    onSelectApiModelConfig(config)
                                    onNavigateToByokConfigure()
                                }
                            },
                            onRequestDeleteConfig = { config ->
                                pendingDeleteTarget = ByokDeleteTarget.Config(config.id, config.displayName)
                            },
                            onAddConfig = {
                                hideAndNavigate {
                                    onSelectApiModelConfig(ApiModelConfigUi())
                                    onNavigateToByokConfigure()
                                }
                            }
                        )
                    }
                    is ByokSheetView.AssetList -> {
                        ByokAssetListView(
                            apiModels = uiState.apiProvidersSheet.assets,
                            onSelectAsset = { asset -> onSelectApiModelAsset(asset) },
                            onEditAsset = { asset ->
                                hideAndNavigate {
                                    onSelectApiModelAsset(asset)
                                    onSelectApiModelConfig(null)
                                    onNavigateToByokConfigure()
                                }
                            },
                            onRequestDeleteAsset = { asset ->
                                pendingDeleteTarget = ByokDeleteTarget.Asset(asset.credentialsId, asset.displayName)
                            },
                            onAddAsset = {
                                hideAndNavigate {
                                    onStartCreateApiModelAsset()
                                    onNavigateToByokConfigure()
                                }
                            }
                        )
                    }
                    is ByokSheetView.Reassignment -> {
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

        if (uiState.deletion.showLastModelAlert) {
            AlertDialog(
                onDismissRequest = onDismissDeletionSafety,
                title = { Text("Cannot Delete Provider") },
                text = { Text("At least one local or API model must remain. You cannot delete the last available provider.") },
                confirmButton = {
                    TextButton(onClick = onDismissDeletionSafety) {
                        Text("OK")
                    }
                }
            )
        }

        pendingDeleteTarget?.let { deleteTarget ->
            DeleteConfirmationDialog(
                title = if (deleteTarget is ByokDeleteTarget.Asset) {
                    "Delete Provider?"
                } else {
                    "Delete Preset?"
                },
                message = if (deleteTarget is ByokDeleteTarget.Asset) {
                    "Delete ${deleteTarget.displayName}? This removes the provider and all of its presets."
                } else {
                    "Delete ${deleteTarget.displayName}? This preset will be removed."
                },
                confirmLabel = "Delete",
                onConfirm = {
                    when (deleteTarget) {
                        is ByokDeleteTarget.Asset -> onDeleteApiModelAsset(deleteTarget.id)
                        is ByokDeleteTarget.Config -> onDeleteApiModelConfig(deleteTarget.id)
                    }
                    pendingDeleteTarget = null
                },
                onDismiss = { pendingDeleteTarget = null }
            )
        }
    }
}

@Composable
private fun ByokAssetListView(
    apiModels: List<ApiModelAssetUi>,
    onSelectAsset: (ApiModelAssetUi) -> Unit,
    onEditAsset: (ApiModelAssetUi) -> Unit,
    onRequestDeleteAsset: (ApiModelAssetUi) -> Unit,
    onAddAsset: () -> Unit
) {
    Column {
        Text(
            text = "External AI Providers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Manage your own API keys and model configurations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Text(
            text = "Hint: long press to edit/delete the provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apiModels, key = { it.credentialsId }) { asset ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectAsset(asset) },
                                onLongClick = { menuExpanded = true }
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
                                    text = asset.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${asset.provider.displayName} • ${asset.modelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "${asset.configurations.size} Preset(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "View configurations",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEditAsset(asset)
                            }
                        )
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddAsset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add new API Provider")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add API Provider", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ByokConfigListView(
    asset: ApiModelAssetUi,
    onBack: () -> Unit,
    onEditAsset: () -> Unit,
    onRequestDeleteAsset: (ApiModelAssetUi) -> Unit,
    onEditConfig: (ApiModelConfigUi) -> Unit,
    onRequestDeleteConfig: (ApiModelConfigUi) -> Unit,
    onAddConfig: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to providers")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                var menuExpanded by remember { mutableStateOf(false) }

                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEditAsset()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRequestDeleteAsset(asset)
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(asset.configurations, key = { it.id.value }) { config ->
                var menuExpanded by remember { mutableStateOf(false) }
                val isDeleteEnabled = asset.configurations.size > 1

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditConfig(config) },
                                onLongClick = { menuExpanded = true }
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
                                    text = config.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Temp: ${config.temperature.format(2)} | Max: ${config.maxTokens}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options for ${config.displayName}",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEditConfig(config)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = isDeleteEnabled,
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

private sealed interface ByokDeleteTarget {
    val displayName: String

    data class Asset(
        val id: Long,
        override val displayName: String
    ) : ByokDeleteTarget

    data class Config(
        val id: ApiModelConfigurationId,
        override val displayName: String
    ) : ByokDeleteTarget
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Assets List")
@Composable
fun PreviewByokBottomSheetAssets() {
    PocketCrewTheme {
        ByokBottomSheet(
            uiState = MockSettingsData.baseUiState,
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {},
            onDismissDeletionSafety = {},
            onConfirmDeletionWithReassignment = {_, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "BYOK Bottom Sheet - Config List")
@Composable
fun PreviewByokBottomSheetContext() {
    PocketCrewTheme {
        ByokBottomSheet(
            uiState = MockSettingsData.baseUiState.copy(
                apiProvidersSheet = MockSettingsData.baseUiState.apiProvidersSheet.copy(
                    selectedAsset = MockSettingsData.apiModels[0]
                )
            ),
            onDismiss = {},
            onNavigateToByokConfigure = {},
            onStartCreateApiModelAsset = {},
            onSelectApiModelAsset = {},
            onSelectApiModelConfig = {},
            onDeleteApiModelAsset = {},
            onDeleteApiModelConfig = {},
            onDismissDeletionSafety = {},
            onConfirmDeletionWithReassignment = {_, _ -> },
        )
    }
}
